package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryAddressRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.MockScenarioOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderItemCreateRequest;
import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.ProductOptionGroup;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionGroupRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;

import java.util.List;
import com.yeni.backoffice.core.commerce.service.CommerceMockScenarioService;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentCancelRequest;
import com.yeni.backoffice.core.payment.service.PaymentCancelService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 데모/포트폴리오용 시드 데이터.
 * <p>
 * fly 프로필은 인메모리 H2를 쓰기 때문에 배포·재시작마다 데이터가 전부 사라진다. 그 상태로
 * 방문자가 들어오면 주문·PG거래·매출원장·후속처리 화면이 전부 비어 있어 무엇을 보여주는
 * 화면인지 알기 어렵다. 그래서 주문이 하나도 없을 때(=기동 직후) "Mock 주문 생성" 화면과
 * 동일한 {@link CommerceMockScenarioService}를 재사용해 승인/실패/결과불명/망취소/취소
 * 주문을 미리 만들어 둔다.
 */
@Component
@Profile("fly | demo")
@Order(150)
public class DemoOrderSeedInitializer implements CommandLineRunner {

    private final CommerceOrderRepository orders;
    private final CommerceStoreRepository stores;
    private final ProductRepository products;
    private final ProductOptionGroupRepository optionGroups;
    private final CommerceMockScenarioService mockScenarioService;
    private final PaymentCancelService paymentCancelService;

    public DemoOrderSeedInitializer(CommerceOrderRepository orders,
                                     CommerceStoreRepository stores,
                                     ProductRepository products,
                                     ProductOptionGroupRepository optionGroups,
                                     CommerceMockScenarioService mockScenarioService,
                                     PaymentCancelService paymentCancelService) {
        this.orders = orders;
        this.stores = stores;
        this.products = products;
        this.optionGroups = optionGroups;
        this.mockScenarioService = mockScenarioService;
        this.paymentCancelService = paymentCancelService;
    }

    @Override
    public void run(String... args) {
        if (orders.count() > 0) {
            return;
        }
        // DemoSettlementSeedInitializer가 YENI-SHOP-01 매장 기준으로 정산을 돌리므로,
        // 예외 시나리오와 앞부분 주문은 이 매장에 귀속시켜 매출 원장·정산이 실제로 연결되게 한다.
        Long shopStoreId = stores.findByStoreCode("YENI-SHOP-01").map(store -> store.getId()).orElse(null);
        // 운영 대시보드 "매장별 운영 집계"·채널 분석(자사몰/매장)이 실제로 여러 줄 나오도록,
        // 정상 주문 일부를 나머지 매장에 라운드로빈으로 분산한다. 주문 품목은 각 매장이 실제로
        // 취급하는 상품에서 고른다.
        List<CommerceStore> spreadStores = stores.findAllByOrderByIdAsc().stream()
                .filter(CommerceStore::isActive)
                .filter(store -> !store.getId().equals(shopStoreId))
                .toList();

        // 목록·통계 화면이 비어 보이지 않도록 넉넉히 채운다. 대부분(약 4/5)은 정상 승인,
        // 나머지는 결과 불명·망취소·결제수단 오류·한도 초과를 섞어 예외 처리 화면도 함께 시연되게 한다.
        String[] buyers = {
                "김민준", "이서연", "박도윤", "최지우", "정하윤", "한소율", "오예준", "윤서아", "임지호", "송하린",
                "조현우", "배수빈", "강민서", "문태윤", "서유진", "백승호", "김예린", "홍지안", "이도현", "신유나",
                "장서준", "노아린", "유현석", "권수아", "안지훈", "남궁민", "황보름", "선우진", "제갈현", "독고윤",
                "김하늘", "이준영", "박서준", "최유나", "정민재", "한지아", "오세훈", "윤하람", "임채원", "송지후",
                "조은서", "배준혁", "강도현", "문가온", "서지완", "백지호", "김도연", "홍세라"
        };
        String[] scenarioCycle = {
                "NORMAL", "NORMAL", "NORMAL", "NORMAL", "RESULT_UNKNOWN",
                "NORMAL", "NORMAL", "NORMAL", "INTERNAL_FAIL", "NORMAL",
                "NORMAL", "NORMAL", "PAYMENT_METHOD_ERROR", "NORMAL", "RESULT_UNKNOWN",
                "NORMAL", "NORMAL", "NORMAL", "CARD_LIMIT_EXCEEDED", "NORMAL"
        };

        CommerceOrderResponse cancelTarget = null;
        for (int i = 0; i < buyers.length; i++) {
            String scenario = scenarioCycle[i % scenarioCycle.length];
            // 예외 시나리오·앞 10건·취소 대상은 정산 연결을 위해 YENI-SHOP-01 고정,
            // 그 밖의 정상 주문은 나머지 매장에 라운드로빈으로 분산한다.
            Long storeId = shopStoreId;
            List<CommerceOrderItemCreateRequest> items = null;
            if ("NORMAL".equals(scenario) && i >= 10 && i != 2 && !spreadStores.isEmpty()) {
                CommerceStore target = spreadStores.get(i % spreadStores.size());
                List<CommerceOrderItemCreateRequest> storeItems = itemsForStore(target.getStoreCode());
                if (storeItems != null) {
                    storeId = target.getId();
                    items = storeItems;
                }
            }
            CommerceOrderResponse order = seed(scenario, buyers[i], storeId, items);
            if (cancelTarget == null && i == 2 && order != null && order.paymentId() != null) {
                cancelTarget = order;
            }
        }

        if (cancelTarget != null && cancelTarget.paymentId() != null) {
            try {
                paymentCancelService.cancelPayment(cancelTarget.paymentId(), new PaymentCancelRequest(
                        cancelTarget.payableAmount(), "데모 시연용 취소", "SEED-CANCEL-" + cancelTarget.id()));
            } catch (Exception ignore) {
                // 시드 데이터 생성 실패가 앱 기동을 막으면 안 된다.
            }
        }
    }

    // 배송지가 없으면 배송 레코드가 생성되지 않아 배송·반품 화면이 비어 보인다.
    // 정상 주문에는 실제 주소를 넣어 후속 배송/반품 흐름이 이어지도록 한다.
    private static final String[][] ADDRESSES = {
            {"04524", "서울 중구 세종대로 110", "8층"},
            {"06236", "서울 강남구 테헤란로 231", "4층"},
            {"13529", "경기 성남시 분당구 판교역로 235", "H스퀘어 N동"},
            {"48058", "부산 해운대구 센텀중앙로 79", "1203호"},
            {"21999", "인천 연수구 컨벤시아대로 165", "3층"},
            {"34126", "대전 유성구 대학로 291", "301호"},
            {"41068", "대구 동구 첨단로 39", "2층"},
            {"63309", "제주 제주시 첨단로 242", "A동 501호"},
    };
    private int addressCursor = 0;

    private CommerceOrderResponse seed(String scenario, String buyerName, Long storeId,
            List<CommerceOrderItemCreateRequest> items) {
        try {
            DeliveryAddressRequest delivery = null;
            if ("NORMAL".equals(scenario)) {
                String[] a = ADDRESSES[addressCursor++ % ADDRESSES.length];
                delivery = new DeliveryAddressRequest(buyerName, "010-0000-0000", a[0], a[1], a[2], null);
            }
            MockScenarioOrderResponse response = mockScenarioService.run(scenario, buyerName, "010-0000-0000", items, storeId, delivery);
            return response.order();
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 해당 매장이 실제 취급하는 상품 중, 옵션 선택 없이 바로 주문 가능한 것 1~2개를 고른다. */
    private List<CommerceOrderItemCreateRequest> itemsForStore(String storeCode) {
        List<CommerceOrderItemCreateRequest> picked = products.findByStoreCode(storeCode).stream()
                .filter(p -> !p.isInventoryManaged() || p.getStockQuantity() > 0)
                .filter(p -> optionGroups.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                        .noneMatch(ProductOptionGroup::isRequiredOption))
                .limit(2)
                .map(p -> new CommerceOrderItemCreateRequest(p.getId(), 1))
                .toList();
        return picked.isEmpty() ? null : picked;
    }
}
