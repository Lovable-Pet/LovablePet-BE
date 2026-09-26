package com.lovablepet.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // @Scheduled 작업 활성화 (예: 만료 리프레시 토큰 정리)
}
