package com.app.tably.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableAsync: 알림 발송(FR-20)을 요청 트랜잭션 밖에서 비동기로 처리하기 위함
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
