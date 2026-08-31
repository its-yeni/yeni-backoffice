package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.ShipmentDtos.ShipmentPendingResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.entity.InventoryLot;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.InventoryLotRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.commerce.entity.StoreVariantInventory;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 결제 완료(PAID) 주문 중 아직 출고되지 않은 SKU 주문 상품을 관리한다.
 * 재고 차감은 이미 주문 생성 시점에 "예약"으로 반영돼 있으므로(ProductVariant.reserve),
 * 여기서는 실제 물건이 나가는 시점에 ProductVariant.ship()으로 현재재고·예약재고를 함께 정리한다.
 */
@Service
public class ShipmentService {
    private final CommerceOrderRepository orderRepository;
    private final CommerceOrderItemRepository itemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryTransactionService inventoryTransactionService;
    private final StoreVariantInventoryRepository storeInventoryRepository;
    private InventoryLotRepository lotRepository;
    private InventoryLedgerService inventoryLedgerService;

    public ShipmentService(CommerceOrderRepository orderRepository, CommerceOrderItemRepository itemRepository,
            ProductVariantRepository variantRepository, InventoryTransactionService inventoryTransactionService,
            StoreVariantInventoryRepository storeInventoryRepository) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.variantRepository = variantRepository;
        this.inventoryTransactionService = inventoryTransactionService;
        this.storeInventoryRepository = storeInventoryRepository;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ShipmentService(CommerceOrderRepository orderRepository, CommerceOrderItemRepository itemRepository,
            ProductVariantRepository variantRepository, InventoryTransactionService inventoryTransactionService,
            StoreVariantInventoryRepository storeInventoryRepository, InventoryLotRepository lotRepository,
            InventoryLedgerService inventoryLedgerService) {
        this(orderRepository, itemRepository, variantRepository, inventoryTransactionService, storeInventoryRepository);
        this.lotRepository = lotRepository;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional(readOnly = true)
    public List<ShipmentPendingResponse> listPending() { return listPending(null); }

    @Transactional(readOnly = true)
    public List<ShipmentPendingResponse> listPending(Long storeId) {
        List<Long> paidOrderIds = orderRepository.findByOrderStatus(OrderStatus.PAID).stream()
                .filter(order -> storeId == null || storeId.equals(order.getStoreId()))
                .map(CommerceOrder::getId).toList();
        if (paidOrderIds.isEmpty()) return List.of();
        List<CommerceOrderItem> pending = itemRepository.findByOrderIdInAndShippedYnFalseAndProductVariantIdIsNotNullOrderByIdAsc(paidOrderIds);
        if (pending.isEmpty()) return List.of();
        Map<Long, CommerceOrder> orders = orderRepository.findAllById(pending.stream().map(CommerceOrderItem::getOrderId).distinct().toList())
                .stream().collect(Collectors.toMap(CommerceOrder::getId, Function.identity()));
        return pending.stream().map(item -> {
            CommerceOrder order = orders.get(item.getOrderId());
            return new ShipmentPendingResponse(item.getId(), item.getOrderId(), order == null ? "-" : order.getOrderNo(),
                    order == null ? "-" : order.getBuyerName(), item.getProductName(), item.getProductCode(),
                    item.getOptionSummary(), item.getQuantity(), item.getItemAmount(), order == null ? null : order.getUpdatedAt());
        }).toList();
    }

    /** 출고 완료 처리. 주문상품 행 자체를 잠가서 동일 건에 대한 동시/중복 완료 요청을 안전하게 막는다. */
    @Transactional
    public void completeShipment(Long orderItemId) {
        completeShipment(orderItemId, null);
    }

    @Transactional
    public void completeShipment(Long orderItemId, Long storeId) {
        CommerceOrderItem item = itemRepository.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "주문 상품을 찾을 수 없습니다."));
        if (item.isShippedYn()) throw new ConflictException(ErrorCode.CONFLICT, "이미 출고 완료된 주문 상품입니다.");
        if (item.getProductVariantId() == null)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "SKU가 연결되지 않은 주문 상품은 출고 처리할 수 없습니다.");
        CommerceOrder order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        if (storeId != null && !storeId.equals(order.getStoreId()))
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND);
        if (order.getOrderStatus() != OrderStatus.PAID)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "결제 완료된 주문만 출고 처리할 수 있습니다.");
        if (order.getStoreId() != null && inventoryLedgerService != null) {
            // 매장 재고 원장 경로: 매장 현재·예약재고 차감 + LOT FEFO 소진 + 전역 프로젝션 동기화 + 감사 로그를 한 번에.
            storeInventoryRepository.findByStoreIdAndVariantId(order.getStoreId(), item.getProductVariantId())
                    .ifPresent(inv -> item.recordCost(inv.getAverageUnitCost()));
            inventoryLedgerService.ship(order.getStoreId(), item.getProductVariantId(), item.getQuantity(),
                    "출고 완료", "ORDER", order.getId(), "SYSTEM");
        } else {
            // 폴백(매장 미지정): SKU 전역 재고만 차감.
            ProductVariant variant = variantRepository.findByIdForUpdate(item.getProductVariantId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
            variant.ship(item.getQuantity());
            inventoryTransactionService.record(variant, InventoryTransactionType.SHIPMENT, item.getQuantity(),
                    "출고 완료", "ORDER", order.getId(), "SYSTEM");
        }
        item.markShipped();
    }
}
