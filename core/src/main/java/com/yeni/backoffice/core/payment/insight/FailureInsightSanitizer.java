package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.command.FailureLogEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/** OpenAI 전송 전에 오류 메시지의 개인정보/인증정보를 제거하고 입력 크기를 제한한다. */
@Component
public class FailureInsightSanitizer {

    static final int MAX_MESSAGE_LENGTH = 500;

    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:01[016789][- ]?\\d{3,4}[- ]?\\d{4})(?!\\d)");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(authorization|api[-_ ]?key|access[-_ ]?token|secret|bearer)\\s*[:=]?\\s*[^\\s,;]+"
    );

    public FailureInsightCommand sanitize(FailureInsightCommand command) {
        List<FailureLogEntry> sanitized = command.entries().stream().map(this::sanitize).toList();
        return new FailureInsightCommand(command.windowFrom(), command.windowTo(), command.totalCandidateCount(), sanitized);
    }

    FailureLogEntry sanitize(FailureLogEntry entry) {
        return new FailureLogEntry(
                bounded(entry.source(), 40),
                bounded(entry.refKey(), 100),
                entry.occurredAt(),
                bounded(entry.eventType(), 60),
                bounded(entry.pgCompany(), 40),
                bounded(entry.resultCode(), 60),
                redact(entry.message()),
                entry.durationMs()
        );
    }

    String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = SECRET.matcher(value).replaceAll("$1=[SECRET]");
        redacted = EMAIL.matcher(redacted).replaceAll("[EMAIL]");
        redacted = PHONE.matcher(redacted).replaceAll("[PHONE]");
        redacted = CARD.matcher(redacted).replaceAll("[CARD]");
        return bounded(redacted, MAX_MESSAGE_LENGTH);
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }
}
