package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderSummaryResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.MockScenarioOrderRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.MockScenarioOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.SplitDeliveryRequest;
import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.commerce.service.CommerceMockScenarioService;
import com.yeni.backoffice.core.commerce.service.CommerceOrderService;
import com.yeni.backoffice.core.commerce.scope.OperationalScopeResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/admin/api/commerce/orders")
public class CommerceOrderRestController {

    private final CommerceOrderService orderService;
    private final CommerceMockScenarioService mockScenarioService;
    private final CommerceDeliveryService deliveryService;
    private final OperationalScopeResolver scopeResolver;

    public CommerceOrderRestController(CommerceOrderService orderService, CommerceMockScenarioService mockScenarioService, CommerceDeliveryService deliveryService, OperationalScopeResolver scopeResolver) {
        this.orderService = orderService;
        this.mockScenarioService = mockScenarioService;
        this.deliveryService = deliveryService;
        this.scopeResolver = scopeResolver;
    }

    @GetMapping
    public ResponseEntity<List<CommerceOrderResponse>> orders(@RequestParam(required = false) Long brandId,
                                                               @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(orderService.getOrdersInScope(scopeResolver.resolve(brandId, storeId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<CommerceOrderSummaryResponse> summary(@RequestParam(required = false) Long brandId,
                                                                 @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(orderService.getSummaryInScope(scopeResolver.resolve(brandId, storeId)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<CommerceOrderResponse> order(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }

    @PostMapping
    public ResponseEntity<CommerceOrderResponse> create(@Valid @RequestBody CommerceOrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @PostMapping("/mock")
    public ResponseEntity<CommerceOrderResponse> createMock() {
        return ResponseEntity.ok(orderService.createMockOrder());
    }

    @PostMapping("/mock-scenario")
    public ResponseEntity<MockScenarioOrderResponse> createMockScenario(@Valid @RequestBody MockScenarioOrderRequest request,@RequestParam(required=false) Long storeId) {
        return ResponseEntity.ok(mockScenarioService.run(request.scenario(), request.buyerName(), request.buyerPhone(), request.items(),storeId,request.delivery()));
    }

    @PostMapping("/{orderId}/pay")
    public ResponseEntity<CommerceOrderResponse> pay(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.approvePayment(orderId));
    }

    @GetMapping("/{orderId}/deliveries")
    public ResponseEntity<List<DeliveryResponse>> deliveries(@PathVariable Long orderId) {
        return ResponseEntity.ok(deliveryService.listByOrder(orderId));
    }

    @PostMapping("/{orderId}/split-delivery")
    public ResponseEntity<DeliveryResponse> splitDelivery(@PathVariable Long orderId, @Valid @RequestBody SplitDeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.splitDelivery(orderId, request.orderItemIds()));
    }
}
