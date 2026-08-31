package com.yeni.backoffice.api.admin.audit.dto;

import java.time.LocalDateTime;

public record AdminAuditLogResponse(
        String source,
        Long sourceId,
        String domainType,
        String actionType,
        String referenceType,
        String referenceKey,
        String actor,
        String resultStatus,
        String description,
        String beforeValue,
        String afterValue,
        String requestId,
        LocalDateTime loggedAt
) {
}
