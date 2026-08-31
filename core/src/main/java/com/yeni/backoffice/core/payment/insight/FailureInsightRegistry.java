package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.enums.InsightProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class FailureInsightRegistry {

    private final Map<InsightProvider, FailureInsightProvider> providers = new EnumMap<>(InsightProvider.class);

    public FailureInsightRegistry(List<FailureInsightProvider> failureInsightProviders) {
        for (FailureInsightProvider provider : failureInsightProviders) {
            providers.put(provider.provider(), provider);
        }
    }

    public FailureInsightProvider get(InsightProvider provider) {
        FailureInsightProvider found = providers.get(provider);
        if (found == null) {
            throw new IllegalArgumentException("Failure insight provider is not registered: " + provider);
        }
        return found;
    }
}
