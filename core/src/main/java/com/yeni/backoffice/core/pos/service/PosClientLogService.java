package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.pos.entity.*;
import com.yeni.backoffice.core.pos.enums.*;
import com.yeni.backoffice.core.pos.repository.PosClientLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.PageRequest;

@Service
public class PosClientLogService {
    private static final int MAX_BATCH=100;
    private final PosClientLogRepository logs;
    public PosClientLogService(PosClientLogRepository logs){this.logs=logs;}

    @Transactional
    public LogBatchResult receive(PosTerminal terminal,List<LogCommand> commands){
        if(commands==null||commands.isEmpty()||commands.size()>MAX_BATCH)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"POS 로그는 한 번에 1~100건만 전송할 수 있습니다.");
        List<PosClientLog> saved=new ArrayList<>();
        for(LogCommand command:commands){validate(command);saved.add(logs.save(PosClientLog.builder()
                .terminalId(terminal.getId()).storeId(terminal.getStoreId()).logLevel(command.logLevel())
                .category(command.category()).occurredAt(command.occurredAt()==null?LocalDateTime.now():command.occurredAt())
                .requestId(trim(command.requestId(),80)).errorMessage(trimRequired(redact(command.errorMessage()),500))
                .stackTrace(trim(redact(command.stackTrace()),4000)).retried(false).build()));}
        return new LogBatchResult(saved.size(),saved.stream().map(PosClientLog::getId).toList());
    }

    @Transactional(readOnly=true)
    public List<LogView> list(PosTerminal terminal,Integer requestedLimit){int limit=Math.min(Math.max(requestedLimit==null?100:requestedLimit,1),500);return logs.findByTerminalIdOrderByOccurredAtDesc(terminal.getId(),PageRequest.of(0,limit)).stream().map(LogView::from).toList();}

    @Transactional
    public LogView markRetried(PosTerminal terminal,Long logId){PosClientLog log=logs.findById(logId)
            .orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"POS 오류 로그를 찾을 수 없습니다."));
        if(!log.getTerminalId().equals(terminal.getId()))throw new BusinessException(ErrorCode.FORBIDDEN,"다른 단말의 로그에는 접근할 수 없습니다.");
        log.markRetried();return LogView.from(log);}

    private void validate(LogCommand command){if(command==null||command.logLevel()==null||command.category()==null||!StringUtils.hasText(command.errorMessage()))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"POS 로그 필수값이 누락되었습니다.");}
    private String trimRequired(String value,int max){String result=trim(value,max);if(result==null)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"오류 메시지는 필수입니다.");return result;}
    private String trim(String value,int max){if(!StringUtils.hasText(value))return null;String v=value.trim();return v.length()<=max?v:v.substring(0,max);}
    private String redact(String value){
        if(value==null)return null;
        String redacted=value.replaceAll("(?i)(authorization|x-pos-credential|api[-_ ]?key)(\\s*[:=]\\s*)[^\\s,;]+","$1$2[REDACTED]");
        return redacted.replaceAll("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)","[REDACTED_CARD]");
    }

    public record LogCommand(PosLogLevel logLevel,PosLogCategory category,LocalDateTime occurredAt,String requestId,String errorMessage,String stackTrace){}
    public record LogBatchResult(int acceptedCount,List<Long> logIds){}
    public record LogView(Long id,PosLogLevel logLevel,PosLogCategory category,LocalDateTime occurredAt,String requestId,String errorMessage,String stackTrace,boolean retried,LocalDateTime retriedAt){
        static LogView from(PosClientLog log){return new LogView(log.getId(),log.getLogLevel(),log.getCategory(),log.getOccurredAt(),log.getRequestId(),log.getErrorMessage(),log.getStackTrace(),log.isRetried(),log.getRetriedAt());}
    }
}
