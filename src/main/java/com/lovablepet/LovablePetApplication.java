package com.lovablepet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 인증은 JWT 필터(SecurityConfig)로만 한다. UserDetailsServiceAutoConfiguration 은 쓰지 않는 인메모리 사용자를 만들고
// 그 비밀번호를 기동할 때마다 로그에 남기므로 제외한다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class LovablePetApplication {

    public static void main(String[] args) {
        SpringApplication.run(LovablePetApplication.class, args);
    }
}
