package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "commerce_order_item")
public class CommerceOrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column
    private Long productId;

    @Column(nullable = false, length = 80)
    private String productCode;

    @Column(nullable = false, length = 200)
    private String productName;

    /** 주문 시점 상품 분류명 snapshot. 상품이 나중에 재분류돼도 이 주문의 표시·집계는 흔들리지 않는다. */
    @Column(length = 100)
    private String categoryName;

    @Column(length = 200)
    private String optionName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal itemAmount;

    @Column(length = 500)
    private String optionSummary;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal optionAdditionalAmount;

    @Column(length = 500)
    private String selectedOptionValueIds;

    @Column(nullable = false)
    private boolean addonItem;

    @Column
    private Long productVariantId;

    @Column(precision = 19, scale = 2)
    private BigDecimal unitCost;

    @Column(precision = 19, scale = 2)
    private BigDecimal costOfGoodsSold;

    @Column(nullable = false)
    private boolean shippedYn;

    @Column
    private java.time.LocalDateTime shippedAt;

    /** 이 주문 상품이 속한 배송(CommerceDelivery). 한 주문을 여러 박스로 나눠 보내는 부분배송을 지원하기 위해
     * 주문(orderId)이 아니라 주문 상품 단위로 배송을 연결한다 — null이면 아직 배송지가 없거나 배송 분리 전이다. */
    @Column
    private Long deliveryId;

    public void markShipped() {
        this.shippedYn = true;
        this.shippedAt = java.time.LocalDateTime.now();
    }

    public void recordCost(BigDecimal cost) {
        this.unitCost = cost == null ? BigDecimal.ZERO : cost;
        this.costOfGoodsSold = this.unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public void assignDelivery(Long deliveryId) {
        this.deliveryId = deliveryId;
    }

    public static CommerceOrderItem create(
            Long orderId,
            Long productId,
            String productCode,
            String productName,
            BigDecimal unitPrice,
            int quantity) {
        validate(unitPrice, quantity);

        CommerceOrderItem item = new CommerceOrderItem();
        item.orderId = orderId;
        item.productId = productId;
        item.productCode = productCode;
        item.productName = productName;
        item.optionName = null;
        item.unitPrice = unitPrice;
        item.quantity = quantity;
        item.itemAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        item.optionAdditionalAmount = BigDecimal.ZERO;
        item.addonItem = false;
        return item;
    }

    public static CommerceOrderItem createConfigured(Long orderId,Long productId,String code,String name,String categoryName,BigDecimal basePrice,
            BigDecimal optionAmount,int quantity,String optionSummary,String selectedIds,boolean addon,Long variantId){
        CommerceOrderItem item=create(orderId,productId,code,name,basePrice.add(optionAmount),quantity);
        item.categoryName=categoryName;
        item.optionAdditionalAmount=optionAmount;item.optionSummary=optionSummary;item.selectedOptionValueIds=selectedIds;item.addonItem=addon;item.productVariantId=variantId;return item;
    }

    private static void validate(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "상품 단가는 0보다 커야 합니다.");
        }
        if (quantity <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "상품 수량은 1 이상이어야 합니다.");
        }
    }
}
