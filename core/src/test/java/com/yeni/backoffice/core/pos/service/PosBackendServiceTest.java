package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.service.CommerceOrderQueryService;
import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.payment.service.PaymentApproveService;
import com.yeni.backoffice.core.payment.service.PaymentCancelService;
import com.yeni.backoffice.core.pos.entity.*;
import com.yeni.backoffice.core.pos.enums.*;
import com.yeni.backoffice.core.pos.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosBackendServiceTest {
    @Test void terminalAuthenticationAcceptsMatchingCredentialAndRejectsInvalidCredential(){
        PosTerminalRepository repository=mock(PosTerminalRepository.class);String raw="portfolio-secret";
        PosTerminal terminal=PosTerminal.builder().id(7L).storeId(3L).terminalCode("POS-01").terminalName("카운터 1").active(true).credentialHash(PosTerminalAuthenticationService.hashCredential(raw)).build();
        when(repository.findByStoreIdAndTerminalCode(3L,"POS-01")).thenReturn(Optional.of(terminal));
        PosTerminalAuthenticationService service=new PosTerminalAuthenticationService(repository);
        assertThat(service.authenticate(3L,"POS-01",raw,"1.0.0")).isSameAs(terminal);
        assertThatThrownBy(()->service.authenticate(3L,"POS-01","wrong","1.0.0")).isInstanceOf(BusinessException.class);
    }

    @Test void cashPaymentRejectsReceivedAmountBelowOrderAmount(){
        CommerceOrderQueryService orders=mock(CommerceOrderQueryService.class);
        when(orders.getOrderInStore(10L,3L)).thenReturn(order(10L,3L,null));
        PosPaymentService service=new PosPaymentService(orders,mock(PaymentApproveService.class),mock(PosIdempotencyService.class));
        PosPaymentService.PaymentCommand command=new PosPaymentService.PaymentCommand(PosPaymentService.Method.CASH,new BigDecimal("7900"),new BigDecimal("5000"),null,null);
        assertThatThrownBy(()->service.approve(terminal(),10L,"pay-1","hash",command)).isInstanceOf(ValidationBusinessException.class);
    }

    @Test void successfulPaymentReplayDoesNotApproveTwice(){
        CommerceOrderQueryService orders=mock(CommerceOrderQueryService.class);PosIdempotencyService idempotency=mock(PosIdempotencyService.class);PaymentApproveService approvals=mock(PaymentApproveService.class);
        when(orders.getOrderInStore(10L,3L)).thenReturn(order(10L,3L,55L));
        PosInboundRequest inbound=PosInboundRequest.builder().id(9L).terminalId(7L).storeId(3L).clientRequestId("pay-1").requestType(PosRequestType.PAYMENT).requestHash("hash").status(PosRequestStatus.SUCCEEDED).serverReference("55").retryCount(0).build();
        when(idempotency.begin(7L,3L,"pay-1",PosRequestType.PAYMENT,"hash")).thenReturn(new PosIdempotencyService.BeginResult(inbound,true));
        PosPaymentService.PaymentResult result=new PosPaymentService(orders,approvals,idempotency).approve(terminal(),10L,"pay-1","hash",new PosPaymentService.PaymentCommand(PosPaymentService.Method.CARD,new BigDecimal("7900"),null,null,0));
        assertThat(result.replay()).isTrue();verifyNoInteractions(approvals);
    }

    @Test void clientLogRedactsCredentialsAndCardNumbers(){
        PosClientLogRepository repository=mock(PosClientLogRepository.class);when(repository.save(any())).thenAnswer(invocation->invocation.getArgument(0));
        PosClientLogService service=new PosClientLogService(repository);
        service.receive(terminal(),List.of(new PosClientLogService.LogCommand(PosLogLevel.ERROR,PosLogCategory.PAYMENT,LocalDateTime.now(),"req-1","Authorization: secret 4111-1111-1111-1111",null)));
        ArgumentCaptor<PosClientLog> captor=ArgumentCaptor.forClass(PosClientLog.class);verify(repository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).contains("[REDACTED]").contains("[REDACTED_CARD]").doesNotContain("secret","4111");
    }

    @Test void posOrderDetailAlwaysUsesAuthenticatedStoreScope(){
        CommerceOrderQueryService orders=mock(CommerceOrderQueryService.class);
        when(orders.getOrderInStore(10L,3L)).thenReturn(order(10L,3L,null));
        PosOrderService service=new PosOrderService(orders,mock(PaymentCancelService.class),mock(PosIdempotencyService.class));
        assertThat(service.detail(terminal(),10L).storeId()).isEqualTo(3L);
        verify(orders).getOrderInStore(10L,3L);
    }

    private PosTerminal terminal(){return PosTerminal.builder().id(7L).storeId(3L).terminalCode("POS-01").terminalName("카운터 1").active(true).credentialHash("hash").build();}
    private CommerceOrderResponse order(Long id,Long storeId,Long paymentId){return new CommerceOrderResponse(id,"POS-01-1","POS 고객",null,"상품",1,new BigDecimal("7900"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("7900"),paymentId==null?BigDecimal.ZERO:new BigDecimal("7900"),BigDecimal.ZERO,paymentId==null?"PENDING_PAYMENT":"PAID",paymentId==null?"READY":"APPROVED",paymentId,paymentId==null?null:"TID",null,List.of(),null,storeId,LocalDateTime.now(),LocalDateTime.now());}
}
