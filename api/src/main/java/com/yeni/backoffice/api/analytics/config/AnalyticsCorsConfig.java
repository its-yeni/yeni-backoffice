package com.yeni.backoffice.api.analytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 분석 API 만 외부(Power BI Web / 브라우저 기반 BI 도구)에서 GET 으로 읽을 수 있도록 CORS 를 연다.
 * 관리자 업무 API(/admin/api/**)는 대상에서 제외한다. (Power BI Desktop 자체는 CORS 미적용이지만
 * Power BI Service / 웹 커넥터 시나리오를 위해 명시적으로 허용한다.)
 */
@Configuration
public class AnalyticsCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/analytics/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
