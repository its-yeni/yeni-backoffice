package com.yeni.backoffice.core.pos.repository;

import com.yeni.backoffice.core.pos.entity.PosInboundRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PosInboundRequestRepository extends JpaRepository<PosInboundRequest, Long> {
    Optional<PosInboundRequest> findByTerminalIdAndClientRequestId(Long terminalId, String clientRequestId);
}
