package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.enums.InsightCategory;
import com.yeni.backoffice.core.payment.enums.InsightProvider;
import com.yeni.backoffice.core.payment.enums.InsightSeverity;
import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.command.FailureLogEntry;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightItem;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInsightResultValidatorTest {

    private final FailureInsightResultValidator validator = new FailureInsightResultValidator();

    @Test
    void removesUnknownAndDuplicateReferencesAndRecalculatesCount() {
        LocalDateTime now = LocalDateTime.now();
        FailureInsightCommand command = new FailureInsightCommand(now.minusHours(1), now, 2, List.of(
                entry("REQ-1", now),
                entry("REQ-2", now.minusMinutes(1))
        ));
        FailureInsightResult modelResult = new FailureInsightResult(
                "요약", List.of(
                item(99, List.of("REQ-1", "REQ-1", "INVENTED")),
                item(3, List.of("REQ-1", "REQ-2"))
        ), InsightProvider.OPENAI, "test-model", Instant.now(), command.windowFrom(), command.windowTo(), 2, 2);

        FailureInsightResult validated = validator.validate(modelResult, command);

        assertThat(validated.items()).hasSize(2);
        assertThat(validated.items().get(0).relatedRefKeys()).containsExactly("REQ-1");
        assertThat(validated.items().get(0).affectedCount()).isEqualTo(1);
        assertThat(validated.items().get(1).relatedRefKeys()).containsExactly("REQ-2");
        assertThat(validated.items().get(1).affectedCount()).isEqualTo(1);
    }

    @Test
    void dropsGroupsThatReferenceNoInputEntries() {
        LocalDateTime now = LocalDateTime.now();
        FailureInsightCommand command = new FailureInsightCommand(now.minusHours(1), now, 1, List.of(entry("REQ-1", now)));
        FailureInsightResult modelResult = new FailureInsightResult(
                "요약", List.of(item(1, List.of("INVENTED"))), InsightProvider.OPENAI, "test-model",
                Instant.now(), command.windowFrom(), command.windowTo(), 1, 1);

        assertThat(validator.validate(modelResult, command).items()).isEmpty();
    }

    private FailureLogEntry entry(String refKey, LocalDateTime occurredAt) {
        return new FailureLogEntry("PG_API_LOG", refKey, occurredAt, "APPROVE", "INICIS", "500", "failed", 100L);
    }

    private FailureInsightItem item(int count, List<String> refKeys) {
        return new FailureInsightItem(
                InsightCategory.EXTERNAL_SYSTEM_ERROR, "외부 시스템 오류", count,
                InsightSeverity.HIGH, "로그를 확인하세요.", refKeys);
    }
}
