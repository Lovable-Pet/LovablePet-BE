-- 로컬 로그인을 이메일 대신 아이디(username)로 하도록 변경한다.
-- 이메일은 연락·계정 찾기용으로 계속 필수(NOT NULL) + 중복 불가(UNIQUE)로 유지한다.
ALTER TABLE local_credential ADD COLUMN username VARCHAR(20);

-- 이미 가입된 로컬 계정(개발 중 테스트 데이터)에는 임시 아이디 user{id} 를 부여한다.
UPDATE local_credential SET username = 'user' || id WHERE username IS NULL;

ALTER TABLE local_credential ALTER COLUMN username SET NOT NULL;
ALTER TABLE local_credential ADD CONSTRAINT uk_local_credential_username UNIQUE (username);
