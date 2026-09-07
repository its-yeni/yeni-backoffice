package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.pos.entity.PosInboundRequest;
import com.yeni.backoffice.core.pos.enums.PosRequestStatus;
import com.yeni.backoffice.core.pos.enums.PosRequestType;
import com.yeni.backoffice.core.pos.repository.PosInboundRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PosRequestRegistrationService {
    private final PosInboundRequestRepository requests;

    public PosRequestRegistrationService(PosInboundRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PosInboundRequest register(
            Long terminalId,
            Long storeId,
            String clientRequestId,
            PosRequestType requestType,
            String requestHash) {
        return requests.saveAndFlush(PosInboundRequest.builder()
                .terminalId(terminalId)
                .storeId(storeId)
                .clientRequestId(clientRequestId)
                .requestType(requestType)
                .requestHash(requestHash)
                .status(PosRequestStatus.RECEIVED)
                .retryCount(0)
                .build());
    }
}
