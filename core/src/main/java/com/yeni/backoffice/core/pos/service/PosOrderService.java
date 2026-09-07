package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.service.CommerceOrderQueryService;
import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.*;
import com.yeni.backoffice.core.payment.service.PaymentCancelService;
import com.yeni.backoffice.core.pos.entity.*;
import com.yeni.backoffice.core.pos.enums.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PosOrderService {
    private final CommerceOrderQueryService orders;
    private final PaymentCancelService cancellations;
    private final PosIdempotencyService idempotency;

    public PosOrderService(CommerceOrderQueryService orders,PaymentCancelService cancellations,PosIdempotencyService idempotency){
        this.orders=orders;this.cancellations=cancellations;this.idempotency=idempotency;
    }

    public CommerceOrderQueryService.OrderPage list(PosTerminal terminal,Integer page,Integer size){
        return orders.getOrdersInStore(terminal.getStoreId(),page,size);
    }
    public CommerceOrderResponse detail(PosTerminal terminal,Long orderId){return orders.getOrderInStore(orderId,terminal.getStoreId());}

    public CancelResult cancel(PosTerminal terminal,Long orderId,String clientRequestId,String requestHash,
            BigDecimal amount,String reason){
        CommerceOrderResponse order=orders.getOrderInStore(orderId,terminal.getStoreId());
        if(order.paymentId()==null)throw new ConflictException(ErrorCode.ORDER_PAYMENT_NOT_ALLOWED,"결제 승인 전 주문은 결제 취소할 수 없습니다.");
        PosIdempotencyService.BeginResult begin=idempotency.begin(terminal.getId(),terminal.getStoreId(),clientRequestId,
                PosRequestType.CANCEL,requestHash);
        PosInboundRequest inbound=begin.request();
        if(begin.replay()&&inbound.getStatus()==PosRequestStatus.SUCCEEDED){
            return new CancelResult(orderId,inbound.getServerReference(),true,inbound.getStatus().name(),null);
        }
        if(begin.replay()&&(inbound.getStatus()==PosRequestStatus.PROCESSING||inbound.getStatus()==PosRequestStatus.MANUAL_REVIEW))
            throw new ConflictException(ErrorCode.CONFLICT,"이미 처리 중이거나 수동 확인이 필요한 POS 취소 요청입니다.");
        idempotency.markProcessing(inbound.getId());
        try{
            PaymentBridgeCancelResponse response=cancellations.cancelPaymentBridge(order.paymentId(),
                    new PaymentBridgeCancelRequest(null,amount,reason,"POS-CANCEL-"+inbound.getId()));
            String reference=response.cancelId()==null?"UNKNOWN":response.cancelId().toString();
            idempotency.markSucceeded(inbound.getId(),reference);
            return new CancelResult(orderId,reference,false,PosRequestStatus.SUCCEEDED.name(),response);
        }catch(RuntimeException failure){
            idempotency.markFailed(inbound.getId(),failure instanceof BusinessException business?business.getErrorCode().name():ErrorCode.INTERNAL_SERVER_ERROR.name(),safeMessage(failure));
            throw failure;
        }
    }

    private String safeMessage(RuntimeException failure){String value=failure.getMessage();if(value==null||value.isBlank())return "POS 취소 처리에 실패했습니다.";return value.length()<=500?value:value.substring(0,500);}
    public record CancelResult(Long orderId,String cancelReference,boolean replay,String requestStatus,PaymentBridgeCancelResponse paymentCancel){}
}
