package com.yeni.backoffice.core.payment.insight.command;

import java.time.LocalDateTime;

/**
 * LLM에 전달되는 실패 로그 1건의 메타데이터.
 * requestBody/responseBody 원문은 절대 포함하지 않는다 (민감정보 노출 방지).
 */
public record FailureLogEntry(
        String source,
        String refKey,
        LocalDateTime occurredAt,
        String eventType,
        String pgCompany,
        String resultCode,
        String message,
        Long durationMs
) {
}
