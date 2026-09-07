package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import com.yeni.backoffice.core.pos.repository.PosTerminalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.security.SecureRandom;
import java.util.*;

@Service
public class PosTerminalAdminService {
    private final PosTerminalRepository terminals;private final CommerceStoreRepository stores;private final SecureRandom random=new SecureRandom();
    public PosTerminalAdminService(PosTerminalRepository terminals,CommerceStoreRepository stores){this.terminals=terminals;this.stores=stores;}
    @Transactional(readOnly=true) public List<TerminalView> list(Long storeId){return terminals.findAll().stream().filter(t->storeId==null||storeId.equals(t.getStoreId())).map(TerminalView::from).toList();}
    @Transactional(readOnly=true) public PosTerminal get(Long id){return require(id);}
    @Transactional public ProvisionedTerminal create(CreateTerminal command){validate(command.storeId(),command.terminalCode(),command.terminalName());stores.findById(command.storeId()).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장을 찾을 수 없습니다."));if(terminals.findByStoreIdAndTerminalCode(command.storeId(),command.terminalCode().trim()).isPresent())throw new ConflictException(ErrorCode.CONFLICT,"이미 등록된 POS 단말 코드입니다.");String credential=newCredential();PosTerminal terminal=PosTerminal.builder().storeId(command.storeId()).terminalCode(command.terminalCode().trim()).terminalName(command.terminalName().trim()).credentialHash(PosTerminalAuthenticationService.hashCredential(credential)).active(true).syncIntervalSeconds(value(command.syncIntervalSeconds(),300)).requestTimeoutSeconds(value(command.requestTimeoutSeconds(),30)).maxRetryCount(value(command.maxRetryCount(),5)).logRetentionDays(value(command.logRetentionDays(),30)).build();return new ProvisionedTerminal(TerminalView.from(terminals.save(terminal)),credential);}
    @Transactional public TerminalView update(Long id,UpdateTerminal command){PosTerminal terminal=require(id);if(command==null||!StringUtils.hasText(command.terminalName()))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"단말 이름은 필수입니다.");terminal.rename(command.terminalName().trim());terminal.changeActive(command.active());terminal.configure(value(command.syncIntervalSeconds(),terminal.effectiveSyncIntervalSeconds()),value(command.requestTimeoutSeconds(),terminal.effectiveRequestTimeoutSeconds()),value(command.maxRetryCount(),terminal.effectiveMaxRetryCount()),value(command.logRetentionDays(),terminal.effectiveLogRetentionDays()));return TerminalView.from(terminal);}
    @Transactional public ProvisionedTerminal rotateCredential(Long id){PosTerminal terminal=require(id);String credential=newCredential();terminal.rotateCredential(PosTerminalAuthenticationService.hashCredential(credential));return new ProvisionedTerminal(TerminalView.from(terminal),credential);}
    private PosTerminal require(Long id){return terminals.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"POS 단말을 찾을 수 없습니다."));}
    private void validate(Long storeId,String code,String name){if(storeId==null||!StringUtils.hasText(code)||code.trim().length()>40||!StringUtils.hasText(name)||name.trim().length()>100)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"매장, 단말 코드와 단말 이름을 확인해 주세요.");}
    private int value(Integer value,int fallback){return value==null||value<=0?fallback:value;}
    private String newCredential(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public record CreateTerminal(Long storeId,String terminalCode,String terminalName,Integer syncIntervalSeconds,Integer requestTimeoutSeconds,Integer maxRetryCount,Integer logRetentionDays){}
    public record UpdateTerminal(String terminalName,boolean active,Integer syncIntervalSeconds,Integer requestTimeoutSeconds,Integer maxRetryCount,Integer logRetentionDays){}
    public record ProvisionedTerminal(TerminalView terminal,String credential){ }
    public record TerminalView(Long id,Long storeId,String terminalCode,String terminalName,boolean active,String appVersion,int syncIntervalSeconds,int requestTimeoutSeconds,int maxRetryCount,int logRetentionDays,java.time.LocalDateTime lastConnectedAt){static TerminalView from(PosTerminal t){return new TerminalView(t.getId(),t.getStoreId(),t.getTerminalCode(),t.getTerminalName(),t.isActive(),t.getAppVersion(),t.effectiveSyncIntervalSeconds(),t.effectiveRequestTimeoutSeconds(),t.effectiveMaxRetryCount(),t.effectiveLogRetentionDays(),t.getLastConnectedAt());}}
}
