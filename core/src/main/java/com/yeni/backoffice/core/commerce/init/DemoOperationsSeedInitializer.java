package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnResponse;
import com.yeni.backoffice.core.commerce.dto.ShipmentDtos.ShipmentPendingResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.entity.StoreVariantInventory;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.enums.ReturnResponsibility;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.entity.InventoryLot;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.InventoryLotRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.commerce.service.CommerceReturnService;
import com.yeni.backoffice.core.commerce.service.InventoryTransactionService;
import com.yeni.backoffice.core.commerce.service.ShipmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배포/로컬 데모 환경에서 목록·통계 화면이 실제 운영 데이터처럼 보이도록 후속 흐름을 채운다.
 * <p>
 * {@link DemoOrderSeedInitializer}(Order 150)가 만든 주문 위에서
 * <ul>
 *   <li>주문·결제·매출을 지난 4주에 분산 배치(매출 추이/전일 대비 지표가 살아 있도록)</li>
 *   <li>배송 진행(송장 발급·배송완료·지연·반송)</li>
 *   <li>출고 처리 일부 완료</li>
 *   <li>반품 접수/검수/환불완료/환불실패/반려</li>
 *   <li>품절·안전재고 이하 SKU와 입출고 내역</li>
 * </ul>
 * 을 만든다. {@link DemoSettlementSeedInitializer}(Order 200)보다 먼저 실행해서, 분산 배치된
 * 매출을 기준으로 일자별 정산이 만들어지게 한다.
 * <p>
 * 각 단계는 {@link TransactionTemplate}로 개별 트랜잭션에서 실행한다 — 한 건이 실패해도
 * (rollback-only 오염 없이) 나머지 시드는 그대로 채워지고, 앱 기동도 막지 않는다.
 */
@Component
@Profile("fly | demo")
@Order(180)
public class DemoOperationsSeedInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoOperationsSeedInitializer.class);

    @PersistenceContext
    private EntityManager em;

    private final TransactionTemplate tx;
    private final CommerceOrderRepository orders;
    private final CommerceDeliveryRepository deliveries;
    private final CommerceDeliveryService deliveryService;
    private final CommerceReturnService returnService;
    private final ShipmentService shipmentService;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final CommerceStoreRepository stores;
    private final StoreVariantInventoryRepository storeInventories;
    private final InventoryLotRepository lots;
    private final InventoryTransactionService inventoryTx;
    private final com.yeni.backoffice.core.commerce.service.InventoryLedgerService ledger;

    public DemoOperationsSeedInitializer(PlatformTransactionManager txManager,
                                         CommerceOrderRepository orders,
                                         CommerceDeliveryRepository deliveries,
                                         CommerceDeliveryService deliveryService,
                                         CommerceReturnService returnService,
                                         ShipmentService shipmentService,
                                         ProductVariantRepository variants,
                                         ProductRepository products,
                                         CommerceStoreRepository stores,
                                         StoreVariantInventoryRepository storeInventories,
                                         InventoryLotRepository lots,
                                         InventoryTransactionService inventoryTx,
                                         com.yeni.backoffice.core.commerce.service.InventoryLedgerService ledger) {
        this.ledger = ledger;
        this.tx = new TransactionTemplate(txManager);
        this.orders = orders;
        this.deliveries = deliveries;
        this.deliveryService = deliveryService;
        this.returnService = returnService;
        this.shipmentService = shipmentService;
        this.variants = variants;
        this.products = products;
        this.stores = stores;
        this.storeInventories = storeInventories;
        this.lots = lots;
        this.inventoryTx = inventoryTx;
    }

    // "며칠 전" 분포 — 최근일수록 촘촘하고, 오늘/어제 건을 넉넉히 둬서 전일 대비 지표가 의미 있게 나온다.
    private static final int[] DAYS_AGO = {
            0, 0, 0, 0, 0, 0, 1, 1, 1, 1,
            2, 2, 3, 3, 4, 5, 6, 7, 8, 9,
            10, 11, 12, 13, 14, 16, 18, 20, 22, 24,
            25, 26, 27, 3, 1, 0, 6, 13, 2, 0,
            1, 5, 9, 15, 21, 4, 8, 0
    };
    // 실제 택배사와 무관한 가상 배송사명.
    private static final String[] CARRIERS = {"가온택배", "다온택배", "라온택배", "마루택배", "바로택배"};

    @Override
    public void run(String... args) {
        if (readOnly(() -> orders.count()) == 0L) {
            return;
        }
        boolean alreadyProgressed = readOnly(() -> deliveries.findAllByOrderByIdDesc().stream()
                .anyMatch(delivery -> delivery.getStatus() != DeliveryStatus.PREPARING));
        if (alreadyProgressed) {
            return;
        }

        seedLocationInventory();
        progressDeliveries();
        progressShipments();
        createReturns();
        adjustInventoryHealth();
        seedOpeningLots();
        resyncVariantProjections();
        spreadOverTime();
        log.info("데모 운영 데이터 시딩 완료");
    }

    /**
     * 위치(매장)별 SKU 재고를 채운다. 각 매장의 재고는 <b>그 매장의 상품 카탈로그(product.store_code 일치)</b>에
     * 속한 SKU만 채운다 — 재고 현황에 그 매장에서 팔지도 않는 상품이 뜨면 안 되기 때문.
     * 매장마다 재고 수량·안전재고를 다르게 줘서 위치별 재고가 복제본처럼 보이지 않게 한다.
     */
    private void seedLocationInventory() {
        List<Long> storeIds = readOnly(() -> stores.findAllByOrderByIdAsc().stream()
                .map(CommerceStore::getId)
                .toList());
        for (int idx = 0; idx < storeIds.size(); idx++) {
            Long storeId = storeIds.get(idx);
            int storeSafety = idx == 0 ? 12 : 6 + (idx % 3);
            // 매장별 재고 배수: 첫 매장(대표몰)은 넉넉, 아울렛류(뒤쪽)는 얕게. 카탈로그가 복제본이라 수량이라도 다르게.
            double stockFactor = idx == 0 ? 1.15 : Math.max(0.35, 1.0 - idx * 0.09);
            step(() -> {
                CommerceStore store = stores.findById(storeId).orElse(null);
                if (store == null) return;
                java.util.Set<Long> storeProductIds = products.findByStoreCode(store.getStoreCode()).stream()
                        .map(Product::getId).collect(java.util.stream.Collectors.toSet());
                if (storeProductIds.isEmpty()) return;
                for (ProductVariant variant : variants.findAllByOrderByProductIdAscSortOrderAscIdAsc()) {
                    if (!storeProductIds.contains(variant.getProductId())) continue;
                    if (storeInventories.findByStoreIdAndVariantId(storeId, variant.getId()).isPresent()) {
                        continue;
                    }
                    Product product = products.findById(variant.getProductId()).orElse(null);
                    BigDecimal salePrice = product == null || product.getSalePrice() == null
                            ? BigDecimal.valueOf(10000) : product.getSalePrice();
                    BigDecimal unitCost = salePrice.multiply(BigDecimal.valueOf(0.42))
                            .setScale(0, java.math.RoundingMode.HALF_UP);
                    int base = Math.max(variant.getStockQuantity(), variant.getReservedQuantity());
                    int scaled = (int) Math.round(base * stockFactor) + (int) (variant.getId() % 5);
                    storeInventories.save(StoreVariantInventory.builder()
                            .storeId(storeId)
                            .variantId(variant.getId())
                            .stockQuantity(Math.max(scaled, variant.getReservedQuantity()))
                            .reservedQuantity(variant.getReservedQuantity())
                            .averageUnitCost(unitCost)
                            .safetyStock(storeSafety)
                            .saleEnabled(true)
                            .build());
                }
            });
        }
    }

    /** 배송 준비 건을 송장발급 → 일부 배송완료 → 일부 지연(72h 초과) → 1건 반송 으로 진행. */
    private void progressDeliveries() {
        List<Long[]> targets = readOnly(() -> deliveries.findByStatusOrderByIdDesc(DeliveryStatus.PREPARING).stream()
                .map(d -> new Long[]{d.getId(), d.getOrderId()})
                .toList());
        int index = 0;
        for (Long[] target : targets) {
            Long deliveryId = target[0];
            OrderStatus status = readOnly(() -> orders.findById(target[1]).map(CommerceOrder::getOrderStatus).orElse(null));
            if (status != OrderStatus.PAID) {
                continue;
            }
            int mod = index % 10;
            String carrier = CARRIERS[index % CARRIERS.length];
            String tracking = "%s%09d".formatted(carrier.substring(0, 1), 100000000L + (long) index * 137);
            int step = index;
            step(() -> {
                if (mod == 0) {
                    return; // 배송 준비 그대로 (출고 대기로 노출)
                }
                deliveryService.dispatch(deliveryId, carrier, tracking);
                if (mod <= 5) {
                    deliveryService.complete(deliveryId);
                } else if (mod == 9) {
                    deliveryService.returnDelivery(deliveryId, "고객 단순 변심 반송");
                }
            });
            index++;
        }

        // 배송 중인데 72시간 넘게 멈춰 있는 "지연" 건 — shipped_at 을 4~6일 전으로 당긴다.
        List<Long> inTransit = readOnly(() -> deliveries.findByStatusOrderByIdDesc(DeliveryStatus.IN_TRANSIT).stream()
                .map(CommerceDelivery::getId).limit(3).toList());
        for (int i = 0; i < inTransit.size(); i++) {
            Long id = inTransit.get(i);
            LocalDateTime staleShippedAt = LocalDateTime.now().minusDays(4L + i).minusHours(6);
            step(() -> em.createNativeQuery("UPDATE commerce_delivery SET shipped_at = :ts, updated_at = :ts WHERE id = :id")
                    .setParameter("ts", staleShippedAt).setParameter("id", id).executeUpdate());
        }
    }

    /** 출고 대기 목록의 앞쪽 절반을 출고 완료 처리해서 "오늘 출고 처리" 진행률이 0이 아니게 한다. */
    private void progressShipments() {
        List<Long> pending = readOnly(() -> shipmentService.listPending().stream()
                .map(ShipmentPendingResponse::orderItemId)
                .toList());
        int target = pending.size() / 2;
        for (int i = 0; i < target; i++) {
            Long orderItemId = pending.get(i);
            step(() -> shipmentService.completeShipment(orderItemId));
        }
    }

    /** 결제 완료 주문에 반품을 다양한 상태로 만든다: 접수 대기 / 검수 중 / 환불 완료 / 환불 실패 / 반려. */
    private void createReturns() {
        List<Long> paidOrderIds = readOnly(() -> orders.findByOrderStatus(OrderStatus.PAID).stream()
                .map(CommerceOrder::getId)
                .sorted((a, b) -> Long.compare(b, a))
                .toList());
        if (paidOrderIds.size() < 8) {
            return;
        }
        String[] reasons = {
                "상품 불량 (봉제 마감 하자)", "단순 변심", "사이즈 미스", "색상이 사진과 다름",
                "배송 중 파손", "구성품 누락", "오배송"
        };

        request(paidOrderIds.get(0), reasons[0], ReturnResponsibility.SELLER_FAULT);      // 접수 대기
        request(paidOrderIds.get(1), reasons[1], ReturnResponsibility.CUSTOMER_FAULT);    // 접수 대기

        inspect(request(paidOrderIds.get(2), reasons[2], ReturnResponsibility.CUSTOMER_FAULT));  // 검수 중
        inspect(request(paidOrderIds.get(3), reasons[3], ReturnResponsibility.SELLER_FAULT));    // 검수 중

        refund(request(paidOrderIds.get(4), reasons[4], ReturnResponsibility.SELLER_FAULT), true);   // 환불 완료
        refund(request(paidOrderIds.get(5), reasons[5], ReturnResponsibility.SELLER_FAULT), true);   // 환불 완료
        refund(request(paidOrderIds.get(6), reasons[6], ReturnResponsibility.SELLER_FAULT), false);  // 환불 실패

        Long rejectId = request(paidOrderIds.get(7), reasons[1], ReturnResponsibility.CUSTOMER_FAULT);
        if (rejectId != null) {
            step(() -> {
                returnService.startInspection(rejectId);
                returnService.reject(rejectId, "제품 하자가 확인되지 않아 반려 처리");
            });
        }
    }

    private Long request(Long orderId, String reason, ReturnResponsibility responsibility) {
        try {
            return tx.execute(status -> {
                ReturnResponse response = returnService.requestReturn(new ReturnCreateRequest(
                        orderId, null, reason, responsibility,
                        responsibility == ReturnResponsibility.CUSTOMER_FAULT ? new BigDecimal("3000") : BigDecimal.ZERO));
                return response.id();
            });
        } catch (Exception e) {
            log.debug("반품 접수 스킵 orderId={}", orderId, e);
            return null;
        }
    }

    private void inspect(Long returnId) {
        if (returnId != null) {
            step(() -> returnService.startInspection(returnId));
        }
    }

    private void refund(Long returnId, boolean succeeds) {
        if (returnId == null) {
            return;
        }
        step(() -> {
            returnService.startInspection(returnId);
            returnService.completeInspection(returnId, null);
        });
        step(() -> {
            if (succeeds) {
                returnService.markRefundSucceeded(returnId);
            } else {
                returnService.markRefundFailed(returnId, "PG 취소 API 타임아웃 - 재시도 필요");
            }
        });
    }

    /** 재고 상태가 다양하게 보이도록: 안전재고 지정, 품절 임박(안전재고 이하) 3건, 품절 2건, 정상 입고 내역 3건. */
    private void adjustInventoryHealth() {
        List<Long> onSaleIds = readOnly(() -> variants.findAllByOrderByProductIdAscSortOrderAscIdAsc().stream()
                .filter(v -> v.getSaleStatus() == ProductSaleStatus.ON_SALE)
                .map(ProductVariant::getId)
                .toList());
        if (onSaleIds.size() < 8) {
            return;
        }

        // 판매 중 SKU 전반에 안전재고 기준선을 준다(로컬 카탈로그 시드는 safety_stock=0이라 "부족" 판정이 아예 안 된다).
        step(() -> em.createNativeQuery("UPDATE product_variant SET safety_stock = 8 WHERE safety_stock = 0 AND sale_status = 'ON_SALE'")
                .executeUpdate());

        // 품절 임박: 가용재고를 안전재고 아래(5개)로. sale_status 는 ON_SALE 유지 → "안전재고 이하 SKU"로 잡힌다.
        for (int i = 2; i < 5; i++) {
            Long id = onSaleIds.get(i);
            step(() -> em.createNativeQuery("UPDATE product_variant SET stock_quantity = reserved_quantity + 5 WHERE id = :id")
                    .setParameter("id", id).executeUpdate());
            step(() -> em.createNativeQuery("UPDATE store_variant_inventory SET stock_quantity = reserved_quantity + 5 WHERE variant_id = :id")
                    .setParameter("id", id).executeUpdate());
        }

        // 품절: 가용재고 0, sale_status 는 ON_SALE 유지 → "품절 SKU (판매중지·입고 필요)"로 잡힌다.
        for (int i = 0; i < 2; i++) {
            Long id = onSaleIds.get(i);
            step(() -> em.createNativeQuery("UPDATE product_variant SET stock_quantity = reserved_quantity WHERE id = :id")
                    .setParameter("id", id).executeUpdate());
            step(() -> em.createNativeQuery("UPDATE store_variant_inventory SET stock_quantity = reserved_quantity WHERE variant_id = :id")
                    .setParameter("id", id).executeUpdate());
        }

        // 입출고 내역 화면이 비지 않도록 정상 SKU 몇 건에 입고/조정 이력을 남긴다.
        String[][] moves = {
                {"RECEIPT", "30", "정기 입고 (발주 PO-2026-0142)", "PURCHASE_ORDER"},
                {"RECEIPT", "50", "정기 입고 (발주 PO-2026-0148)", "PURCHASE_ORDER"},
                {"ADJUST_IN", "4", "반품 재입고 (검수 양품)", "RETURN"},
                {"ADJUST_OUT", "2", "실사 차이 조정 (파손 폐기)", "STOCKTAKE"},
        };
        for (int i = 0; i < moves.length; i++) {
            Long id = onSaleIds.get(5 + i);
            String[] move = moves[i];
            step(() -> {
                ProductVariant v = variants.findById(id).orElseThrow();
                InventoryTransactionType type = InventoryTransactionType.valueOf(move[0]);
                int qty = Integer.parseInt(move[1]);
                switch (type) {
                    case RECEIPT, ADJUST_IN -> v.receiveStock(qty);
                    case ADJUST_OUT -> v.adjust(-Math.min(qty, v.getStockQuantity()));
                    default -> { /* no-op */ }
                }
                inventoryTx.record(v, type, qty, move[2], move[3], null, "system-demo");
            });
        }
    }

    /**
     * 위치별 SKU 재고에 "기초 재고 LOT"를 하나씩 발번한다 — LOT·유통기한 화면과 출고 FEFO 소진이
     * 실제로 동작하려면 재고를 담고 있는 LOT 행이 있어야 한다. 유통기한은 일부 SKU에만 부여해
     * 임박(CRITICAL)·주의(WARNING)·정상 상태가 골고루 보이게 한다.
     */
    private void seedOpeningLots() {
        if (readOnly(() -> lots.count()) > 0L) {
            return;
        }
        LocalDate today = LocalDate.now();
        String lotNo = "OPEN-" + today.format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        List<Long[]> targets = readOnly(() -> storeInventories.findAll().stream()
                .filter(inv -> inv.getStockQuantity() > 0)
                .map(inv -> new Long[]{inv.getStoreId(), inv.getVariantId(), (long) inv.getStockQuantity()})
                .toList());
        int[] expiryDays = {21, 75, 150};
        for (int i = 0; i < targets.size(); i++) {
            Long[] t = targets.get(i);
            int qty = t[2].intValue();
            LocalDate expiration = t[1] % 3 == 0 ? today.plusDays(expiryDays[i % expiryDays.length]) : null;
            step(() -> {
                if (lots.findByStoreIdAndVariantIdAndLotNo(t[0], t[1], lotNo).isPresent()) {
                    return;
                }
                lots.save(InventoryLot.builder()
                        .storeId(t[0]).variantId(t[1]).lotNo(lotNo)
                        .manufacturedDate(today.minusDays(90))
                        .expirationDate(expiration)
                        .receivedQuantity(qty).availableQuantity(qty)
                        .memo("기초 재고 (데모 시드)")
                        .build());
            });
        }
    }

    /**
     * 매장별 재고를 채운 뒤, 모든 SKU의 전역 수량(ProductVariant.stockQuantity)을 Σ 매장 재고로 다시 맞춘다.
     * adjustInventoryHealth()가 네이티브 SQL로 store_variant_inventory를 직접 바꾸므로 반드시 그 뒤에 실행한다.
     * 이렇게 해야 상품 목록의 재고와 재고 현황의 매장별 합계가 일치한다.
     */
    private void resyncVariantProjections() {
        List<Long> variantIds = readOnly(() -> variants.findAllByOrderByProductIdAscSortOrderAscIdAsc().stream()
                .map(ProductVariant::getId).toList());
        for (Long variantId : variantIds) {
            step(() -> ledger.resyncVariant(variantId));
        }
    }

    /** 주문/결제/매출/반품/배송을 지난 4주에 분산 배치한다. created_at 은 감사 대상이라 네이티브로만 바꿀 수 있다. */
    private void spreadOverTime() {
        List<String> orderNos = readOnly(() -> orders.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(CommerceOrder::getOrderNo)
                .toList());
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < orderNos.size(); i++) {
            int daysAgo = DAYS_AGO[i % DAYS_AGO.length];
            if (daysAgo == 0) {
                continue;
            }
            String orderNo = orderNos.get(i);
            LocalDateTime ts = now.minusDays(daysAgo)
                    .withHour(9 + (i % 11)).withMinute((i * 7) % 60).withSecond(0).withNano(0);
            LocalDate businessDate = ts.toLocalDate();
            step(() -> {
                em.createNativeQuery("UPDATE commerce_order SET created_at = :ts, updated_at = :ts WHERE order_no = :no")
                        .setParameter("ts", ts).setParameter("no", orderNo).executeUpdate();
                em.createNativeQuery("UPDATE payment_transaction SET created_at = :ts, updated_at = :ts, "
                                + "approved_at = CASE WHEN approved_at IS NULL THEN approved_at ELSE :ts END WHERE order_no = :no")
                        .setParameter("ts", ts).setParameter("no", orderNo).executeUpdate();
                em.createNativeQuery("UPDATE sales_transaction SET created_at = :ts, updated_at = :ts, occurred_at = :ts, business_date = :bd WHERE order_no = :no")
                        .setParameter("ts", ts).setParameter("bd", businessDate).setParameter("no", orderNo).executeUpdate();
                Long orderId = (Long) em.createNativeQuery("SELECT id FROM commerce_order WHERE order_no = :no")
                        .setParameter("no", orderNo).getResultList().stream().findFirst().map(x -> ((Number) x).longValue()).orElse(null);
                if (orderId != null) {
                    em.createNativeQuery("UPDATE commerce_return SET created_at = :ts, updated_at = :ts WHERE order_id = :id")
                            .setParameter("ts", ts).setParameter("id", orderId).executeUpdate();
                    em.createNativeQuery("UPDATE commerce_delivery SET created_at = :ts WHERE order_id = :id")
                            .setParameter("ts", ts).setParameter("id", orderId).executeUpdate();
                }
            });
        }
    }

    /** 개별 시드 스텝을 각자의 트랜잭션에서 실행하고, 실패하면 로그만 남기고 넘어간다. */
    private void step(Runnable action) {
        try {
            tx.executeWithoutResult(status -> action.run());
        } catch (Exception e) {
            log.debug("데모 시드 스텝 스킵", e);
        }
    }

    private <T> T readOnly(java.util.function.Supplier<T> query) {
        return tx.execute(status -> query.get());
    }
}
