package com.lovablepet.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** 현재 시각. 테스트에서 고정 시계로 바꿔 끼울 수 있도록 빈으로 주입한다. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
