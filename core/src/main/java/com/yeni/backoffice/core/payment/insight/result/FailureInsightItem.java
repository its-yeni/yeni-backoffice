package com.yeni.backoffice.core.payment.insight.result;

import com.yeni.backoffice.core.payment.enums.InsightCategory;
import com.yeni.backoffice.core.payment.enums.InsightSeverity;

import java.util.List;

public record FailureInsightItem(
        InsightCategory category,
        String cause,
        int affectedCount,
        InsightSeverity severity,
        String suggestedAction,
        List<String> relatedRefKeys
) {
}
