package com.yeni.backoffice.core.payment.dto;

import com.yeni.backoffice.core.payment.insight.result.FailureInsightItem;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;
import com.yeni.backoffice.core.payment.enums.InsightProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class InsightDtos {

    private InsightDtos() {
    }

    @Schema(description = "실패 로그 AI 원인 요약 (운영자 1차 트리아지 보조용, 최종 판단은 운영자가 합니다)")
    public record FailureInsightResponse(
            String summaryHeadline,
            List<FailureInsightItemResponse> items,
            String provider,
            String configuredProvider,
            String connectionStatus,
            boolean apiKeyConfigured,
            boolean fallbackUsed,
            String modelUsed,
            Instant generatedAt,
            LocalDateTime windowFrom,
            LocalDateTime windowTo,
            int sampleSize,
            int totalCandidateCount,
            boolean truncated
    ) {
        public static FailureInsightResponse from(
                FailureInsightResult result,
                InsightProvider configuredProvider,
                boolean apiKeyConfigured) {
            boolean fallbackUsed = configuredProvider == InsightProvider.OPENAI
                    && result.provider() != InsightProvider.OPENAI;
            String connectionStatus = configuredProvider == InsightProvider.MOCK
                    ? "MOCK"
                    : fallbackUsed ? "FALLBACK"
                    : result.sampleSize() == 0 ? "CONFIGURED" : "CONNECTED";
            return new FailureInsightResponse(
                    result.summaryHeadline(),
                    result.items().stream().map(FailureInsightItemResponse::from).toList(),
                    result.provider().name(),
                    configuredProvider.name(),
                    connectionStatus,
                    apiKeyConfigured,
                    fallbackUsed,
                    result.modelUsed(),
                    result.generatedAt(),
                    result.windowFrom(),
                    result.windowTo(),
                    result.sampleSize(),
                    result.totalCandidateCount(),
                    result.totalCandidateCount() > result.sampleSize()
            );
        }
    }

    public record FailureInsightItemResponse(
            String category,
            String cause,
            int affectedCount,
            String severity,
            String suggestedAction,
            List<String> relatedRefKeys
    ) {
        public static FailureInsightItemResponse from(FailureInsightItem item) {
            return new FailureInsightItemResponse(
                    item.category().name(),
                    item.cause(),
                    item.affectedCount(),
                    item.severity().name(),
                    item.suggestedAction(),
                    item.relatedRefKeys()
            );
        }
    }
}
