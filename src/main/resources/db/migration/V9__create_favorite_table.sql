-- 회원이 찜한 동물. 한 회원이 같은 동물을 두 번 찜할 수 없다.
-- pet 행은 동기화 때 지우지 않으므로(목록에서 내리기만 함) 입양된 동물의 찜도 남는다.
CREATE TABLE favorite (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member (id),
    pet_id     BIGINT      NOT NULL REFERENCES pet (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_favorite_member_pet UNIQUE (member_id, pet_id)
);

-- 내 찜 목록 (최근에 찜한 순)
CREATE INDEX idx_favorite_member_created_at ON favorite (member_id, created_at DESC);
