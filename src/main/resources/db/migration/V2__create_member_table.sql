CREATE TABLE member (
                        id BIGSERIAL PRIMARY KEY,
                        nickname VARCHAR(50) NOT NULL,
                        status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'DELETED')),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
