-- 1. 로컬 인증 테이블
CREATE TABLE local_credential (
                                  id BIGSERIAL PRIMARY KEY,
                                  member_id BIGINT NOT NULL UNIQUE,
                                  email VARCHAR(100) NOT NULL UNIQUE,
                                  password_hash VARCHAR(255) NOT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT fk_local_credential_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- 2. 소셜 인증 테이블
CREATE TABLE oauth_account (
                               id BIGSERIAL PRIMARY KEY,
                               member_id BIGINT NOT NULL UNIQUE,
                               provider VARCHAR(20) NOT NULL,
                               provider_subject VARCHAR(100) NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject),
                               CONSTRAINT fk_oauth_account_member FOREIGN KEY (member_id) REFERENCES member (id)
);

-- 3. 리프레시 토큰 테이블
CREATE TABLE refresh_token (
                               id BIGSERIAL PRIMARY KEY,
                               member_id BIGINT NOT NULL,
                               token_hash VARCHAR(255) NOT NULL UNIQUE,
                               expiry_date TIMESTAMP NOT NULL,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id)
);

--리프레시 토큰 테이블의 member_id는 다중 기기 로그인 위해 UNIQUE 제약 조건을 걸지 않아서 따로 인덱스를 추가함)
CREATE INDEX idx_refresh_token_member_id ON refresh_token (member_id);
