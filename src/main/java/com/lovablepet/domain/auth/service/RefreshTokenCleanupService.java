package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료된 리프레시 토큰 정리.
 * 로그아웃하지 않고 떠난 기기의 토큰은 재발급 요청이 오지 않아 계속 쌓이므로 주기적으로 삭제한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    // 기본: 매일 04:00 (app.auth.refresh-token-cleanup-cron 으로 변경 가능)
    @Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void deleteExpiredTokens() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("만료된 리프레시 토큰 {}건 삭제", deleted);
        }
    }
}
