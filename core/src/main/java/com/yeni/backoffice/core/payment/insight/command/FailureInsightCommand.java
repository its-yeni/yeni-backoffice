package com.yeni.backoffice.core.payment.insight.command;

import java.time.LocalDateTime;
import java.util.List;

public record FailureInsightCommand(
        LocalDateTime windowFrom,
        LocalDateTime windowTo,
        int totalCandidateCount,
        List<FailureLogEntry> entries
) {

    public boolean isEmpty() {
        return entries == null || entries.isEmpty();
    }

    public boolean isTruncated() {
        return entries != null && totalCandidateCount > entries.size();
    }
}
