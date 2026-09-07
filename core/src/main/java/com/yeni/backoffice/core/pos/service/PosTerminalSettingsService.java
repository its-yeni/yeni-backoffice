package com.yeni.backoffice.core.pos.service;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class PosTerminalSettingsService {
    public TerminalSettings settings(PosTerminal terminal){return new TerminalSettings(terminal.getStoreId(),terminal.getId(),terminal.getTerminalCode(),terminal.getTerminalName(),terminal.isActive(),terminal.getAppVersion(),terminal.effectiveSyncIntervalSeconds(),terminal.effectiveRequestTimeoutSeconds(),terminal.effectiveMaxRetryCount(),terminal.effectiveLogRetentionDays(),terminal.getLastConnectedAt());}
    public ConnectionDiagnostic diagnostic(PosTerminal terminal){return new ConnectionDiagnostic("CONNECTED","CONNECTED",LocalDateTime.now(),terminal.getLastConnectedAt());}
    public record TerminalSettings(Long storeId,Long terminalId,String terminalCode,String terminalName,boolean active,String appVersion,int syncIntervalSeconds,int requestTimeoutSeconds,int maxRetryCount,int logRetentionDays,LocalDateTime lastConnectedAt){}
    public record ConnectionDiagnostic(String serverStatus,String databaseStatus,LocalDateTime serverTime,LocalDateTime authenticatedAt){}
}
