package com.yeni.backoffice.core.pos.repository;

import com.yeni.backoffice.core.pos.entity.PosSyncExecution;
import com.yeni.backoffice.core.pos.enums.PosSyncResource;
import com.yeni.backoffice.core.pos.enums.PosSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PosSyncExecutionRepository extends JpaRepository<PosSyncExecution,Long> {
    Optional<PosSyncExecution> findByTerminalIdAndClientRunId(Long terminalId,String clientRunId);
    Optional<PosSyncExecution> findTopByTerminalIdAndResourceTypeAndStatusOrderByCompletedAtDesc(
            Long terminalId, PosSyncResource resourceType, PosSyncStatus status);
    List<PosSyncExecution> findByTerminalIdAndStatusInOrderByReportedAtDesc(
            Long terminalId, Collection<PosSyncStatus> statuses);
}
