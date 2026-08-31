package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.config.PortfolioInsightProperties;
import com.yeni.backoffice.core.payment.entity.PaymentRecoveryTask;
import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.enums.InsightProvider;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.RecoveryStatus;
import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.command.FailureLogEntry;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;
import com.yeni.backoffice.core.payment.repository.PaymentRecoveryTaskRepository;
import com.yeni.backoffice.core.payment.repository.PgApiLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실패 로그/RecoveryTask를 모아 FailureInsightProvider에 넘기고, 결과를 카드용으로 반환한다.
 * 운영자의 최종 판단을 대신하지 않는 1차 트리아지 보조라는 스코프를 이 서비스 경계 안에서 지킨다:
 * provider 호출이 실패해도 화면이 죽지 않도록 항상 MOCK으로 폴백한다.
 */
@Service
public class FailureInsightService {

    private static final Logger log = LoggerFactory.getLogger(FailureInsightService.class);
    private static final List<RecoveryStatus> RECOVERY_TARGET_STATUSES = List.of(RecoveryStatus.READY, RecoveryStatus.FAILED);

    private final PgApiLogRepository pgApiLogRepository;
    private final PaymentRecoveryTaskRepository recoveryTaskRepository;
    private final PortfolioInsightProperties properties;
    private final FailureInsightRegistry registry;
    private final FailureInsightRouter router;
    private final FailureInsightSanitizer sanitizer;
    private final FailureInsightResultValidator resultValidator;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public FailureInsightService(
            PgApiLogRepository pgApiLogRepository,
            PaymentRecoveryTaskRepository recoveryTaskRepository,
            PortfolioInsightProperties properties,
            FailureInsightRegistry registry,
            FailureInsightRouter router,
            FailureInsightSanitizer sanitizer,
            FailureInsightResultValidator resultValidator) {
        this.pgApiLogRepository = pgApiLogRepository;
        this.recoveryTaskRepository = recoveryTaskRepository;
        this.properties = properties;
        this.registry = registry;
        this.router = router;
        this.sanitizer = sanitizer;
        this.resultValidator = resultValidator;
    }

    public FailureInsightResult getFailureInsight() {
        FailureInsightCommand command = collect();

        String cacheKey = buildCacheKey(command);
        evictExpiredCacheEntries();
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.result();
        }

        FailureInsightResult result = resolve(command);
        if (cache.size() >= 256) {
            cache.clear();
        }
        cache.put(cacheKey, new CacheEntry(result, Instant.now().plusSeconds(Math.max(1, properties.getCacheTtlSeconds()))));
        return result;
    }

    private FailureInsightResult resolve(FailureInsightCommand command) {
        InsightProvider routed = router.route();
        try {
            return resultValidator.validate(registry.get(routed).summarize(command), command);
        } catch (Exception e) {
            log.warn("Failure insight provider {} failed, falling back to MOCK.", routed, e);
            return resultValidator.validate(registry.get(InsightProvider.MOCK).summarize(command), command);
        }
    }

    private FailureInsightCommand collect() {
        LocalDateTime windowTo = LocalDateTime.now();
        LocalDateTime windowFrom = windowTo.minusHours(properties.getWindowHours());

        int maxEntries = Math.max(1, properties.getMaxEntries());
        PageRequest limit = PageRequest.of(0, maxEntries);
        long failedLogCount = pgApiLogRepository.countByResultStatusAndLoggedAtAfter(LogResultStatus.FAILED, windowFrom);
        long recoveryTaskCount = recoveryTaskRepository.countByStatusInAndCreatedAtAfter(RECOVERY_TARGET_STATUSES, windowFrom);
        List<PgApiLog> failedLogs = pgApiLogRepository
                .findByResultStatusAndLoggedAtAfterOrderByLoggedAtDesc(LogResultStatus.FAILED, windowFrom, limit);
        List<PaymentRecoveryTask> recoveryTasks = recoveryTaskRepository
                .findByStatusInAndCreatedAtAfterOrderByLastTriedAtDesc(RECOVERY_TARGET_STATUSES, windowFrom, limit);

        List<FailureLogEntry> entries = new ArrayList<>(failedLogs.size() + recoveryTasks.size());
        failedLogs.forEach(logEntry -> entries.add(toEntry(logEntry)));
        recoveryTasks.forEach(task -> entries.add(toEntry(task)));
        entries.sort(Comparator.comparing(FailureLogEntry::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int totalCandidateCount = Math.toIntExact(Math.min(Integer.MAX_VALUE, failedLogCount + recoveryTaskCount));
        List<FailureLogEntry> limited = entries.size() > maxEntries
                ? entries.subList(0, maxEntries)
                : entries;

        return sanitizer.sanitize(new FailureInsightCommand(windowFrom, windowTo, totalCandidateCount, List.copyOf(limited)));
    }

    private FailureLogEntry toEntry(PgApiLog logEntry) {
        return new FailureLogEntry(
                "PG_API_LOG",
                logEntry.getRequestId(),
                logEntry.getLoggedAt(),
                logEntry.getEventType() == null ? null : logEntry.getEventType().name(),
                logEntry.getPgCompany() == null ? null : logEntry.getPgCompany().name(),
                logEntry.getHttpStatus() == null ? null : String.valueOf(logEntry.getHttpStatus()),
                firstNonBlank(logEntry.getErrorMessage(), logEntry.getResultMessage()),
                logEntry.getDurationMs()
        );
    }

    private FailureLogEntry toEntry(PaymentRecoveryTask task) {
        LocalDateTime occurredAt = task.getLastTriedAt() != null ? task.getLastTriedAt() : task.getCreatedAt();
        return new FailureLogEntry(
                "RECOVERY_TASK",
                task.getTaskKey(),
                occurredAt,
                task.getRecoveryType() == null ? null : task.getRecoveryType().name(),
                null,
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getLastErrorMessage(),
                null
        );
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private String buildCacheKey(FailureInsightCommand command) {
        int contentHash = command.entries().hashCode();
        return router.route() + ":" + command.totalCandidateCount() + ":" + contentHash;
    }

    private void evictExpiredCacheEntries() {
        cache.entrySet().removeIf(entry -> !entry.getValue().isValid());
    }

    private record CacheEntry(FailureInsightResult result, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
