package com.lovablepet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로딩 + Flyway 마이그레이션 검증.
 * PostgreSQL(pgvector)이 떠 있어야 한다 — 로컬은 docker compose up -d, CI는 서비스 컨테이너 사용.
 */
@SpringBootTest
class LovablePetApplicationTests {

    @Test
    void contextLoads() {
    }
}
