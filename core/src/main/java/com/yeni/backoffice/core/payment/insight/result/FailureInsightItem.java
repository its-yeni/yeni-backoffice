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
        List<String> relatedRefKeys,
        /** 이 그룹에 포함된 refKey들의 출처 (PG_API_LOG / RECOVERY_TASK). 검증 단계에서 입력 표본과 대조해 채운다. */
        List<String> relatedSources
) {
}
