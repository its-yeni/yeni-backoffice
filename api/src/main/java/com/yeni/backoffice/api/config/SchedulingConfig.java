package com.yeni.backoffice.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 운영 자동화 스케줄러 활성화. 실제 작업은 {@link PortfolioOperationsScheduler} 참고. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
