package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.pos.entity.*;
import com.yeni.backoffice.core.pos.enums.*;
import com.yeni.backoffice.core.pos.repository.PosSyncExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PosSyncManagementService {
    private final PosSyncExecutionRepository executions;

    public PosSyncManagementService(PosSyncExecutionRepository executions){this.executions=executions;}

    @Transactional
    public SyncExecution report(PosTerminal terminal,SyncReport command){
        validate(command);
        PosSyncExecution existing=executions.findByTerminalIdAndClientRunId(terminal.getId(),command.clientRunId().trim()).orElse(null);
        if(existing!=null){
            if(existing.getResourceType()!=command.resourceType())throw new ConflictException(ErrorCode.CONFLICT,"같은 동기화 실행 ID가 다른 리소스에 사용되었습니다.");
            return SyncExecution.from(existing);
        }
        boolean succeeded=command.status()==PosSyncStatus.SUCCEEDED;
        PosSyncExecution saved=executions.save(PosSyncExecution.builder().terminalId(terminal.getId()).storeId(terminal.getStoreId())
                .clientRunId(command.clientRunId().trim()).resourceType(command.resourceType()).status(command.status())
                .cursorValue(trim(command.cursorValue(),200)).successCount(Math.max(0,command.successCount()))
                .failureCount(Math.max(0,command.failureCount())).retryCount(0).errorCode(trim(command.errorCode(),60))
                .errorMessage(trim(command.errorMessage(),500)).reportedAt(LocalDateTime.now())
                .completedAt(succeeded?LocalDateTime.now():null).build());
        return SyncExecution.from(saved);
    }

    @Transactional(readOnly=true)
    public SyncOverview overview(PosTerminal terminal){
        List<ResourceCheckpoint> checkpoints=Arrays.stream(PosSyncResource.values()).map(resource->executions
                .findTopByTerminalIdAndResourceTypeAndStatusOrderByCompletedAtDesc(terminal.getId(),resource,PosSyncStatus.SUCCEEDED)
                .map(value->new ResourceCheckpoint(resource,value.getCursorValue(),value.getSuccessCount(),value.getCompletedAt()))
                .orElse(new ResourceCheckpoint(resource,null,0,null))).toList();
        List<SyncExecution> retryQueue=executions.findByTerminalIdAndStatusInOrderByReportedAtDesc(terminal.getId(),
                List.of(PosSyncStatus.FAILED,PosSyncStatus.RETRY_REQUESTED)).stream().map(SyncExecution::from).toList();
        return new SyncOverview(checkpoints,retryQueue);
    }

    @Transactional
    public SyncExecution requestRetry(PosTerminal terminal,Long executionId){
        PosSyncExecution execution=requireOwned(terminal,executionId); execution.requestRetry(); return SyncExecution.from(execution);
    }

    @Transactional
    public SyncExecution resolve(PosTerminal terminal,Long executionId){
        PosSyncExecution execution=requireOwned(terminal,executionId); execution.resolve(); return SyncExecution.from(execution);
    }

    private PosSyncExecution requireOwned(PosTerminal terminal,Long id){
        PosSyncExecution execution=executions.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"동기화 실행 이력을 찾을 수 없습니다."));
        if(!execution.getTerminalId().equals(terminal.getId()))throw new BusinessException(ErrorCode.FORBIDDEN,"다른 단말의 동기화 이력에는 접근할 수 없습니다.");
        return execution;
    }
    private void validate(SyncReport command){
        if(command==null||!StringUtils.hasText(command.clientRunId())||command.clientRunId().trim().length()>80||command.resourceType()==null
                ||(command.status()!=PosSyncStatus.SUCCEEDED&&command.status()!=PosSyncStatus.FAILED))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"동기화 결과 보고 값이 올바르지 않습니다.");
    }
    private String trim(String value,int max){if(!StringUtils.hasText(value))return null;String v=value.trim();return v.length()<=max?v:v.substring(0,max);}

    public record SyncReport(String clientRunId,PosSyncResource resourceType,PosSyncStatus status,String cursorValue,
            int successCount,int failureCount,String errorCode,String errorMessage){}
    public record ResourceCheckpoint(PosSyncResource resourceType,String cursorValue,int successCount,LocalDateTime lastSucceededAt){}
    public record SyncOverview(List<ResourceCheckpoint> checkpoints,List<SyncExecution> retryQueue){}
    public record SyncExecution(Long id,String clientRunId,PosSyncResource resourceType,PosSyncStatus status,String cursorValue,
            int successCount,int failureCount,int retryCount,String errorCode,String errorMessage,LocalDateTime reportedAt,LocalDateTime completedAt){
        static SyncExecution from(PosSyncExecution value){return new SyncExecution(value.getId(),value.getClientRunId(),value.getResourceType(),value.getStatus(),value.getCursorValue(),value.getSuccessCount(),value.getFailureCount(),value.getRetryCount(),value.getErrorCode(),value.getErrorMessage(),value.getReportedAt(),value.getCompletedAt());}
    }
}
