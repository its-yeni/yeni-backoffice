package com.yeni.backoffice.core.payment.insight.result;

import com.yeni.backoffice.core.payment.enums.InsightProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record FailureInsightResult(
        String summaryHeadline,
        List<FailureInsightItem> items,
        InsightProvider provider,
        String modelUsed,
        Instant generatedAt,
        LocalDateTime windowFrom,
        LocalDateTime windowTo,
        int sampleSize,
        int totalCandidateCount
) {

    public static FailureInsightResult empty(InsightProvider provider, LocalDateTime windowFrom, LocalDateTime windowTo) {
        return new FailureInsightResult(
                "최근 실패 이력이 없습니다.",
                List.of(),
                provider,
                provider.name().toLowerCase(),
                Instant.now(),
                windowFrom,
                windowTo,
                0,
                0
        );
    }
}
