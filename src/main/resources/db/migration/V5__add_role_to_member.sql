-- 회원 역할. 관리자(ADMIN)는 가입 API로 만들지 않고 운영 DB에서 직접 지정한다.
ALTER TABLE member
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'));
