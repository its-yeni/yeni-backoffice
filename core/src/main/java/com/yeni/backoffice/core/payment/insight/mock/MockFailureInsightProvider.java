package com.yeni.backoffice.core.payment.insight.mock;

import com.yeni.backoffice.core.payment.enums.InsightCategory;
import com.yeni.backoffice.core.payment.enums.InsightProvider;
import com.yeni.backoffice.core.payment.enums.InsightSeverity;
import com.yeni.backoffice.core.payment.insight.FailureInsightProvider;
import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.command.FailureLogEntry;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightItem;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 키워드 매칭만으로 원인을 분류하는 규칙 기반 구현체.
 * OpenAI 미설정 상태의 기본값이자, OpenAI 호출이 실패했을 때의 폴백으로도 재사용된다.
 */
@Component
public class MockFailureInsightProvider implements FailureInsightProvider {

    @Override
    public InsightProvider provider() {
        return InsightProvider.MOCK;
    }

    @Override
    public FailureInsightResult summarize(FailureInsightCommand command) {
        if (command.isEmpty()) {
            return FailureInsightResult.empty(provider(), command.windowFrom(), command.windowTo());
        }

        Map<InsightCategory, List<FailureLogEntry>> grouped = new LinkedHashMap<>();
        for (FailureLogEntry entry : command.entries()) {
            grouped.computeIfAbsent(classify(entry), key -> new ArrayList<>()).add(entry);
        }

        List<FailureInsightItem> items = new ArrayList<>();
        for (Map.Entry<InsightCategory, List<FailureLogEntry>> group : grouped.entrySet()) {
            items.add(toItem(group.getKey(), group.getValue()));
        }
        items.sort((a, b) -> b.affectedCount() - a.affectedCount());

        String headline = String.format(
                "최근 실패 %d건을 %d개 원인으로 분류했습니다%s. (규칙 기반 요약)",
                command.entries().size(),
                items.size(),
                command.isTruncated() ? String.format(Locale.KOREA, " (전체 %d건 중 최근 %d건 기준)", command.totalCandidateCount(), command.entries().size()) : ""
        );

        return new FailureInsightResult(
                headline,
                items,
                provider(),
                "rule-based",
                Instant.now(),
                command.windowFrom(),
                command.windowTo(),
                command.entries().size(),
                command.totalCandidateCount()
        );
    }

    private InsightCategory classify(FailureLogEntry entry) {
        String text = ((entry.resultCode() == null ? "" : entry.resultCode())
                + " " + (entry.message() == null ? "" : entry.message())
                + " " + (entry.eventType() == null ? "" : entry.eventType()))
                .toUpperCase(Locale.ROOT);

        if (entry.durationMs() != null && entry.durationMs() > 5000) {
            return InsightCategory.TIMEOUT;
        }
        if (containsAny(text, "TIMEOUT", "TIME_OUT", "TIMED OUT")) {
            return InsightCategory.TIMEOUT;
        }
        if (containsAny(text, "AMOUNT", "MISMATCH", "금액")) {
            return InsightCategory.AMOUNT_MISMATCH;
        }
        if (containsAny(text, "DUPLICATE", "IDEMPOTENC", "중복")) {
            return InsightCategory.DUPLICATE_REQUEST;
        }
        if (containsAny(text, "NETWORK", "CONNECT", "네트워크", "NET_CANCEL")) {
            return InsightCategory.NETWORK_ERROR;
        }
        if (containsAny(text, "EXTERNAL", "SAP", "PG", "ALIMTALK")) {
            return InsightCategory.EXTERNAL_SYSTEM_ERROR;
        }
        return InsightCategory.UNKNOWN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private FailureInsightItem toItem(InsightCategory category, List<FailureLogEntry> entries) {
        int count = entries.size();
        InsightSeverity severity = count >= 5 ? InsightSeverity.HIGH : count >= 2 ? InsightSeverity.MEDIUM : InsightSeverity.LOW;
        List<String> refKeys = entries.stream()
                .map(FailureLogEntry::refKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        List<String> sources = entries.stream()
                .map(FailureLogEntry::source)
                .filter(source -> source != null && !source.isBlank())
                .distinct()
                .toList();

        return new FailureInsightItem(
                category,
                describeCause(category, count),
                count,
                severity,
                suggestAction(category),
                refKeys,
                sources
        );
    }

    private String describeCause(InsightCategory category, int count) {
        return switch (category) {
            case TIMEOUT -> String.format("응답 지연/타임아웃으로 추정되는 실패 %d건", count);
            case AMOUNT_MISMATCH -> String.format("금액 불일치로 추정되는 실패 %d건", count);
            case DUPLICATE_REQUEST -> String.format("중복 요청으로 추정되는 실패 %d건", count);
            case NETWORK_ERROR -> String.format("네트워크/망취소 관련 실패 %d건", count);
            case EXTERNAL_SYSTEM_ERROR -> String.format("외부 시스템(PG/SAP/알림톡) 응답 실패 %d건", count);
            default -> String.format("원인이 명확하지 않은 실패 %d건", count);
        };
    }

    private String suggestAction(InsightCategory category) {
        return switch (category) {
            case TIMEOUT -> "재시도로 해결될 가능성이 높습니다. RecoveryTask 재시도를 우선 시도하세요.";
            case AMOUNT_MISMATCH -> "자동 재시도 대상이 아닙니다. 원거래 금액과 PG 응답 금액을 수동으로 대조하세요.";
            case DUPLICATE_REQUEST -> "idempotencyKey 기준으로 중복 요청 여부를 먼저 확인하세요.";
            case NETWORK_ERROR -> "PG 쪽 망취소 처리 여부를 확인한 뒤 재조회하세요.";
            case EXTERNAL_SYSTEM_ERROR -> "연동 대상 시스템의 상태를 확인하고 필요 시 재전송하세요.";
            default -> "요청/응답 로그를 직접 확인해 원인을 특정해야 합니다.";
        };
    }
}
