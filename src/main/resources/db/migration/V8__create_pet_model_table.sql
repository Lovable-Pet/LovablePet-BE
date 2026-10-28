-- 관리자가 요청한 AR(3D 모델) 생성 시도. 한 동물에 실패한 시도는 여러 개일 수 있고, 완성(DONE)은 하나뿐이다.
-- 공공데이터 동기화는 이 테이블을 건드리지 않는다 (pet 행은 지우지 않고 덮어쓰기만 하므로 pet_id 연결이 유지된다).
CREATE TABLE pet_model (
    id            BIGSERIAL PRIMARY KEY,
    pet_id        BIGINT        NOT NULL REFERENCES pet (id),
    job_id        VARCHAR(100)  UNIQUE,        -- 파이프라인 작업 ID. 작업을 등록하는 동안에는 비어 있다
    status        VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'DONE', 'PHOTO_REJECTED', 'UNSUITABLE', 'FAILED')),
    photo_url     TEXT          NOT NULL,      -- 관리자가 고른 사진
    weight_kg     NUMERIC(5, 2) NOT NULL,      -- 실제로 쓴 체중 (관리자가 넣었으면 그 값). 모델 크기(체고)를 정한다
    forced        BOOLEAN       NOT NULL,      -- 템플릿 부적합이어도 끝까지 만들기 (fit_stop 끔). 사진 거부는 못 넘는다
    requested_by  BIGINT        REFERENCES member (id),
    fit_level     VARCHAR(10),                 -- good | fair | poor
    fit_score     INTEGER,
    template      VARCHAR(30),                 -- jindo, jindo_puppy, maltese, toypoodle, chihuahua, retriever
    reasons       TEXT[]        NOT NULL,      -- 사진 거부·부적합 사유 (한국어)
    error         TEXT,
    glb_path      TEXT,                        -- model-dir 기준 상대 경로: {유기번호}/{job_id}.glb
    withers_m     NUMERIC(5, 3),               -- 체고(m). GLB 가 이미 실제 크기라 표시용
    tap_animation VARCHAR(30),                 -- 탭했을 때 재생할 애니메이션 (예: tail_wag)
    playback_rate NUMERIC(4, 2),
    cost_usd      NUMERIC(6, 3),
    version       BIGINT        NOT NULL,      -- 콜백과 진행 중 작업 확인이 동시에 와도 한 번만 반영
    created_at    TIMESTAMPTZ   NOT NULL,
    finished_at   TIMESTAMPTZ
);

-- 한 동물에 만드는 중 하나, 완성 하나까지 (버튼을 두 번 눌러도 두 개가 생기지 않는다)
CREATE UNIQUE INDEX uk_pet_model_pending ON pet_model (pet_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uk_pet_model_done ON pet_model (pet_id) WHERE status = 'DONE';
-- 진행 중 작업 확인, 하루 생성 횟수
CREATE INDEX idx_pet_model_status_created_at ON pet_model (status, created_at);
CREATE INDEX idx_pet_model_created_at ON pet_model (created_at);
