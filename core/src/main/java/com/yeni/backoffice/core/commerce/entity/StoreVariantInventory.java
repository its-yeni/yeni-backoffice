package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import com.yeni.backoffice.core.common.exception.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="store_variant_inventory",uniqueConstraints=@UniqueConstraint(name="uk_store_variant_inventory",columnNames={"storeId","variantId"}))
public class StoreVariantInventory extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false) private Long variantId;
    @Column(nullable=false) private int stockQuantity;
    @Column(nullable=false) private int reservedQuantity;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal averageUnitCost;
    @Column(nullable=false) private boolean saleEnabled;
    /** 매장별 안전재고. 가용재고가 이 값 이하로 떨어지면 "부족"으로 표시하고 발주 제안 대상이 된다. */
    @Column(nullable=false) @org.hibernate.annotations.ColumnDefault("0") private int safetyStock;
    public void update(int stock,boolean enabled){stockQuantity=Math.max(0,stock);saleEnabled=enabled;}
    public void updateSafetyStock(int value){safetyStock=Math.max(0,value);}
    /** 재고 원장이 매장 행 합계를 ProductVariant 프로젝션에 주입할 때 쓰는 조정. LOT/원가와 무관한 순수 수량 조정. */
    public void adjustStock(int delta){int next=stockQuantity+delta;if(next<0)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"조정 후 매장 재고는 0 이상이어야 합니다.");stockQuantity=next;}
    public int getAvailableQuantity(){return Math.max(0,stockQuantity-reservedQuantity);}
    public void receive(int quantity,BigDecimal unitCost){
        if(quantity<=0||unitCost==null||unitCost.signum()<0)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"입고 수량과 단가를 확인해 주세요.");
        BigDecimal oldValue=cost().multiply(BigDecimal.valueOf(stockQuantity));
        stockQuantity+=quantity;
        averageUnitCost=oldValue.add(unitCost.multiply(BigDecimal.valueOf(quantity)))
                .divide(BigDecimal.valueOf(stockQuantity),2,java.math.RoundingMode.HALF_UP);
    }
    public void reserve(int quantity){if(quantity<=0||getAvailableQuantity()<quantity)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);reservedQuantity+=quantity;}
    public void release(int quantity){reservedQuantity=Math.max(0,reservedQuantity-quantity);}
    public BigDecimal ship(int quantity){if(quantity<=0||reservedQuantity<quantity||stockQuantity<quantity)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);BigDecimal cogs=cost().multiply(BigDecimal.valueOf(quantity));stockQuantity-=quantity;reservedQuantity-=quantity;return cogs;}
    public void transferOut(int quantity){if(quantity<=0||getAvailableQuantity()<quantity)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);stockQuantity-=quantity;}
    public void transferIn(int quantity,BigDecimal unitCost){receive(quantity,unitCost);}
    private BigDecimal cost(){return averageUnitCost==null?BigDecimal.ZERO:averageUnitCost;}
}
