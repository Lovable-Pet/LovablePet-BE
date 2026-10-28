-- 공공데이터(농림축산검역본부 구조동물 조회 서비스)에서 매주 동기화하는 유기견.
-- 응답 원본은 raw 에 통째로 두고, 화면·검색에 쓰는 값만 컬럼으로 뽑는다.
-- 원본 값의 길이·형식이 보장되지 않아 문자열은 TEXT 로 둔다 (긴 값 하나 때문에 동기화가 멈추지 않도록).
CREATE TABLE pet (
    id                BIGSERIAL PRIMARY KEY,
    desertion_no      VARCHAR(30) NOT NULL UNIQUE, -- 유기번호. 동기화 기준 키이자 파이프라인 pet_id
    notice_no         TEXT,                        -- 공고번호 (예: 경기-화성-2026-01509)
    kind_name         TEXT,                        -- 품종 (예: 믹스견)
    color             TEXT,                        -- 털색 원문 (예: 갈색&흰색)
    age_text          TEXT,                        -- 나이 원문 (예: 2024(년생), 2026(60일미만)(년생))
    birth_year        INTEGER,
    weight_text       TEXT,                        -- 체중 원문 (예: 5.0(Kg))
    weight_kg         NUMERIC(5, 2),               -- 변환하지 못하면 NULL → AR 생성 불가
    sex               TEXT,                        -- M, F, Q(미상)
    neutered          TEXT,                        -- Y, N, U(미상)
    process_state     TEXT        NOT NULL,        -- 보호중, 종료(입양), 종료(반환) ... 공고중인 동물도 '보호중'으로 온다
    notice_start      DATE,
    notice_end        DATE,                        -- 이 날이 지나면 보호중(입양 가능), 그 전이면 공고중
    listed            BOOLEAN     NOT NULL,        -- 마지막 동기화 때 공공데이터의 공고중·보호중 목록에 있었는지
    org_name          TEXT,                        -- 관할 지자체 (예: 경기도 화성시)
    happen_place      TEXT,                        -- 발견 장소
    care_name         TEXT,                        -- 보호소
    care_tel          TEXT,
    care_addr         TEXT,
    care_reg_no       TEXT,
    special_mark      TEXT,                        -- 특징
    photo_urls        TEXT[]      NOT NULL,        -- https 로 바꾼 사진 주소 (popfile1~8, 중복 제거)
    raw               JSONB       NOT NULL,        -- 응답 원본. 보호소 담당자 이름이 들어 있어 API 로 그대로 내보내지 않는다
    source_updated_at TIMESTAMPTZ,                 -- 원본 수정 시각 (updTm 은 한국 시각으로 온다)
    synced_at         TIMESTAMPTZ NOT NULL,        -- 마지막으로 받은 시각
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 목록: 공공데이터 목록에 있고(listed) 공고 종료일이 지난(보호중) 동물
CREATE INDEX idx_pet_listed_notice_end ON pet (listed, notice_end);
