-- 성공한 공공데이터 동기화 기록. 서버가 꺼져 있어 주간 동기화를 놓쳤는지 확인하는 데 쓴다.
CREATE TABLE pet_sync_run (
    id          BIGSERIAL PRIMARY KEY,
    started_at  TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    fetched     INTEGER     NOT NULL,
    created     INTEGER     NOT NULL,
    updated     INTEGER     NOT NULL,
    unlisted    INTEGER     NOT NULL
);
