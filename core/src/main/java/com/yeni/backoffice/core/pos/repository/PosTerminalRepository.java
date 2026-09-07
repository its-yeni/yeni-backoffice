package com.yeni.backoffice.core.pos.repository;

import com.yeni.backoffice.core.pos.entity.PosTerminal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PosTerminalRepository extends JpaRepository<PosTerminal, Long> {
    Optional<PosTerminal> findByStoreIdAndTerminalCode(Long storeId, String terminalCode);
}
