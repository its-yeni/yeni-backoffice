package com.yeni.backoffice.core.pos.repository;
import com.yeni.backoffice.core.pos.entity.PosClientLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
public interface PosClientLogRepository extends JpaRepository<PosClientLog,Long>{
    List<PosClientLog> findByTerminalIdOrderByOccurredAtDesc(Long terminalId,Pageable pageable);
    long deleteByOccurredAtBefore(LocalDateTime threshold);
}
