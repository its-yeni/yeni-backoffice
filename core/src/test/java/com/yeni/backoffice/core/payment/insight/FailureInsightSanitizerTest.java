package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.command.FailureLogEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FailureInsightSanitizerTest {

    private final FailureInsightSanitizer sanitizer = new FailureInsightSanitizer();

    @Test
    void masksSensitiveValuesBeforeSendingLogsToProvider() {
        String message = "email=user@example.com phone=010-1234-5678 card=4111-1111-1111-1111 "
                + "Authorization: Bearer-secret-token";
        FailureLogEntry entry = new FailureLogEntry(
                "PG_API_LOG", "REQ-1", LocalDateTime.now(), "APPROVE", "INICIS", "500", message, 120L);

        FailureInsightCommand sanitized = sanitizer.sanitize(new FailureInsightCommand(
                LocalDateTime.now().minusHours(1), LocalDateTime.now(), 1, List.of(entry)));

        assertThat(sanitized.entries().get(0).message())
                .contains("[EMAIL]", "[PHONE]", "[CARD]", "[SECRET]")
                .doesNotContain("user@example.com", "010-1234-5678", "4111-1111-1111-1111", "Bearer-secret-token");
    }

    @Test
    void limitsMessageLength() {
        assertThat(sanitizer.redact("x".repeat(700)))
                .hasSize(FailureInsightSanitizer.MAX_MESSAGE_LENGTH + 1)
                .endsWith("…");
    }
}
