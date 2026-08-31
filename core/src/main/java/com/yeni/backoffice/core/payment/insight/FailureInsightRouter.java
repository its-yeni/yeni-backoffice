package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.config.PortfolioInsightProperties;
import com.yeni.backoffice.core.payment.enums.InsightProvider;
import org.springframework.stereotype.Component;

@Component
public class FailureInsightRouter {

    private final PortfolioInsightProperties properties;

    public FailureInsightRouter(PortfolioInsightProperties properties) {
        this.properties = properties;
    }

    public InsightProvider route() {
        try {
            return InsightProvider.valueOf(properties.getProvider().trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return InsightProvider.MOCK;
        }
    }
}
