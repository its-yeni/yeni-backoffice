package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.service.CommerceOrderQueryService;
import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.*;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.service.PaymentApproveService;
import com.yeni.backoffice.core.pos.entity.*;
import com.yeni.backoffice.core.pos.enums.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PosPaymentService {
    private final CommerceOrderQueryService orders;
    private final PaymentApproveService approvals;
    private final PosIdempotencyService idempotency;
    public PosPaymentService(CommerceOrderQueryService orders,PaymentApproveService approvals,PosIdempotencyService idempotency){this.orders=orders;this.approvals=approvals;this.idempotency=idempotency;}

    public PaymentResult approve(PosTerminal terminal,Long orderId,String clientRequestId,String requestHash,PaymentCommand command){
        CommerceOrderResponse order=orders.getOrderInStore(orderId,terminal.getStoreId());
        validate(order,command);
        PosIdempotencyService.BeginResult begin=idempotency.begin(terminal.getId(),terminal.getStoreId(),clientRequestId,PosRequestType.PAYMENT,requestHash);
        PosInboundRequest inbound=begin.request();
        if(begin.replay()&&inbound.getStatus()==PosRequestStatus.SUCCEEDED){CommerceOrderResponse refreshed=orders.getOrderInStore(orderId,terminal.getStoreId());return new PaymentResult(refreshed.paymentId(),refreshed.orderNo(),command.paymentMethod(),refreshed.paidAmount(),change(command),true,inbound.getStatus().name(),refreshed.tid());}
        if(begin.replay()&&(inbound.getStatus()==PosRequestStatus.PROCESSING||inbound.getStatus()==PosRequestStatus.MANUAL_REVIEW))throw new ConflictException(ErrorCode.CONFLICT,"이미 처리 중이거나 수동 확인이 필요한 POS 결제 요청입니다.");
        idempotency.markProcessing(inbound.getId());
        try{
            PaymentApproveResponse approved=approvals.approvePayment(new PaymentApproveRequest(PgProvider.MOCK,order.orderNo(),command.amount(),"KRW",order.buyerName(),order.productName(),"POS-PAY-"+inbound.getId(),"POS",terminal.getTerminalCode(),command.paymentMethod().name(),terminal.getStoreId()));
            idempotency.markSucceeded(inbound.getId(),approved.paymentId().toString());
            return new PaymentResult(approved.paymentId(),approved.orderNo(),command.paymentMethod(),approved.approvedAmount(),change(command),false,PosRequestStatus.SUCCEEDED.name(),approved.tid());
        }catch(RuntimeException failure){idempotency.markFailed(inbound.getId(),failure instanceof BusinessException b?b.getErrorCode().name():ErrorCode.INTERNAL_SERVER_ERROR.name(),safe(failure));throw failure;}
    }
    private void validate(CommerceOrderResponse order,PaymentCommand command){if(command==null||command.paymentMethod()==null||command.amount()==null||command.amount().signum()<=0)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"결제수단과 결제금액을 확인해 주세요.");if(command.amount().compareTo(order.payableAmount())!=0)throw new ValidationBusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);if(command.paymentMethod()==Method.CASH&&(command.cashReceivedAmount()==null||command.cashReceivedAmount().compareTo(command.amount())<0))throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"현금 수납액이 결제금액보다 작습니다.");}
    private BigDecimal change(PaymentCommand command){return command.paymentMethod()==Method.CASH?command.cashReceivedAmount().subtract(command.amount()):BigDecimal.ZERO;}
    private String safe(RuntimeException failure){String m=failure.getMessage();if(m==null||m.isBlank())return "POS 결제 처리에 실패했습니다.";return m.length()<=500?m:m.substring(0,500);}
    public enum Method { CARD,CASH }
    public record PaymentCommand(Method paymentMethod,BigDecimal amount,BigDecimal cashReceivedAmount,String approvalCode,Integer installmentMonths){}
    public record PaymentResult(Long paymentId,String orderNo,Method paymentMethod,BigDecimal approvedAmount,BigDecimal changeAmount,boolean replay,String requestStatus,String transactionId){}
}
