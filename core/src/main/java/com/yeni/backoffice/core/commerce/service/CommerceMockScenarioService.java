package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderItemCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.MockScenarioOrderResponse;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductOptionGroup;
import com.yeni.backoffice.core.commerce.enums.MockOrderScenario;
import com.yeni.backoffice.core.commerce.repository.ProductOptionGroupRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * "Mock 주문 생성" 화면의 시나리오 버튼을 실제 주문 생성 + 결제 승인 흐름으로 연결한다.
 * <p>
 * {@link CommerceOrderService}를 별도 빈으로 주입받아 호출하는 이유: createOrder()와
 * approvePayment()는 각각 독립된 트랜잭션으로 커밋되어야 한다(주문은 생성됐는데 결제만
 * 실패하는 시나리오를 보여줘야 하므로). 같은 클래스 안에서 this.createOrder(...)처럼
 * 자기 자신을 호출하면 스프링 프록시를 우회해 @Transactional이 무시되기 때문에,
 * 반드시 다른 빈을 통해 호출해야 각 메서드의 트랜잭션 경계가 실제로 지켜진다.
 */
@Service
public class CommerceMockScenarioService {

    private final CommerceOrderService commerceOrderService;
    private final ProductRepository productRepository;
    private final ProductOptionGroupRepository optionGroupRepository;

    public CommerceMockScenarioService(CommerceOrderService commerceOrderService, ProductRepository productRepository,
                                        ProductOptionGroupRepository optionGroupRepository) {
        this.commerceOrderService = commerceOrderService;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
    }

    public MockScenarioOrderResponse run(String scenarioName, String buyerName, String buyerPhone, List<CommerceOrderItemCreateRequest> items) {
        return run(scenarioName,buyerName,buyerPhone,items,null,null);
    }
    public MockScenarioOrderResponse run(String scenarioName, String buyerName, String buyerPhone, List<CommerceOrderItemCreateRequest> items,Long storeId) {
        return run(scenarioName,buyerName,buyerPhone,items,storeId,null);
    }
    public MockScenarioOrderResponse run(String scenarioName, String buyerName, String buyerPhone, List<CommerceOrderItemCreateRequest> items,Long storeId,
            com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryAddressRequest delivery) {
        MockOrderScenario scenario = parseScenario(scenarioName);
        List<CommerceOrderItemCreateRequest> resolvedItems = (items == null || items.isEmpty()) ? defaultItems() : items;

        String orderNo = generateOrderNo(scenario);
        CommerceOrderResponse created = commerceOrderService.createOrder(new CommerceOrderCreateRequest(
                orderNo,
                StringUtils.hasText(buyerName) ? buyerName.trim() : "포트폴리오 고객",
                StringUtils.hasText(buyerPhone) ? buyerPhone.trim() : "010-0000-0000",
                resolvedItems,
                delivery
        ), storeId);

        CommerceOrderResponse result = safeApprove(created.id());
        String message = describe(scenario, result);

        if (scenario == MockOrderScenario.DUPLICATE_REQUEST) {
            result = safeApprove(created.id());
            message = "동일 주문에 결제 승인을 2회 요청했습니다. 두 번째 요청은 idempotencyKey 기준으로 기존 결과를 그대로 반환합니다: "
                    + result.lastMessage();
        }

        return new MockScenarioOrderResponse(result, scenario.name(), label(scenario), message);
    }

    private CommerceOrderResponse safeApprove(Long orderId) {
        try {
            return commerceOrderService.approvePayment(orderId);
        } catch (BusinessException e) {
            // 실패 계열 시나리오는 approvePayment가 예외를 던진다. 주문 자체(및 실패 상태)는
            // 이미 별도 트랜잭션으로 커밋돼 있으므로, 최신 상태를 다시 조회해서 반환한다.
            return commerceOrderService.getOrder(orderId);
        }
    }

    private String generateOrderNo(MockOrderScenario scenario) {
        String base = "ORD-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String marker = switch (scenario) {
            case RESULT_UNKNOWN -> "UNKNOWN";
            case INTERNAL_FAIL -> "NETCANCEL";
            case PAYMENT_METHOD_ERROR -> "METHODERR";
            case CARD_LIMIT_EXCEEDED -> "CARDLIMIT";
            default -> null;
        };
        return marker == null ? base : base + "-" + marker;
    }

    private List<CommerceOrderItemCreateRequest> defaultItems() {
        // 필수 옵션(사이즈 등)이 걸린 상품은 옵션값 선택 없이는 주문을 만들 수 없으므로,
        // "아무 상품이나 2개" 기본값 후보에서 제외한다.
        List<Product> products = productRepository.findAll().stream()
                .filter(p -> !p.isInventoryManaged() || p.getStockQuantity() > 0)
                .filter(p -> optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                        .noneMatch(ProductOptionGroup::isRequiredOption))
                .limit(2)
                .toList();
        if (products.isEmpty()) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "옵션 선택 없이 주문 가능한 상품을 먼저 등록해 주세요.");
        }
        return products.stream().map(p -> new CommerceOrderItemCreateRequest(p.getId(), 1)).toList();
    }

    private MockOrderScenario parseScenario(String value) {
        try {
            return MockOrderScenario.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "알 수 없는 시나리오입니다: " + value);
        }
    }

    private String label(MockOrderScenario scenario) {
        return switch (scenario) {
            case NORMAL -> "정상 승인";
            case RESULT_UNKNOWN -> "결과 불명 (응답 딜레이)";
            case INTERNAL_FAIL -> "승인 후 내부 처리 실패 (망취소)";
            case PAYMENT_METHOD_ERROR -> "결제 수단 오류";
            case CARD_LIMIT_EXCEEDED -> "카드 한도 부족";
            case DUPLICATE_REQUEST -> "듀플리케이션 테스트 (중복 요청)";
        };
    }

    private String describe(MockOrderScenario scenario, CommerceOrderResponse result) {
        return switch (scenario) {
            case NORMAL -> "결제가 정상 승인됐습니다: " + result.lastMessage();
            case RESULT_UNKNOWN -> "PG 응답이 지연되어 결과 불명 상태가 됐습니다. RecoveryTask로 재조회가 필요합니다: " + result.lastMessage();
            case INTERNAL_FAIL -> "PG 승인은 성공했지만 이후 내부 처리 중 실패해 망취소/복구 대상으로 등록됐습니다: " + result.lastMessage();
            case PAYMENT_METHOD_ERROR -> "지원하지 않는 결제수단으로 승인에 실패했습니다: " + result.lastMessage();
            case CARD_LIMIT_EXCEEDED -> "카드 한도 초과로 승인에 실패했습니다: " + result.lastMessage();
            case DUPLICATE_REQUEST -> result.lastMessage();
        };
    }
}
