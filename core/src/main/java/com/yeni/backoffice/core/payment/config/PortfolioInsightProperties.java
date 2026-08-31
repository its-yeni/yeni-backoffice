package com.yeni.backoffice.core.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portfolio.insight")
public class PortfolioInsightProperties {

    /**
     * 활성 프로바이더. "mock" | "openai". 대소문자 무관.
     */
    private String provider = "mock";

    private int windowHours = 24;

    private int maxEntries = 50;

    private int cacheTtlSeconds = 300;

    private Openai openai = new Openai();

    @Getter
    @Setter
    public static class Openai {
        private String apiKey = "";
        private String model = "gpt-5-mini";
        private String baseUrl = "https://api.openai.com/v1";
        private int timeoutMs = 8000;
    }
}
