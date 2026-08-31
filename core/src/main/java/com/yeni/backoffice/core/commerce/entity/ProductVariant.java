package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * 옵션값 조합별 SKU. 현재재고(stockQuantity)와 예약재고(reservedQuantity)를 분리해서 관리한다.
 * 가용재고 = 현재재고 - 예약재고. 주문 생성 시점에는 reserve()로 예약재고만 늘리고(현재재고는 그대로),
 * 실제로 물건이 나가는 출고 완료 시점에 ship()으로 현재재고와 예약재고를 함께 줄인다.
 */
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="product_variant",uniqueConstraints={@UniqueConstraint(name="uk_product_variant_sku",columnNames="sku"),@UniqueConstraint(name="uk_product_variant_combination",columnNames={"productId","combinationKey"}),@UniqueConstraint(name="uk_product_variant_barcode",columnNames="barcode")})
public class ProductVariant extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long productId;
 @Column(nullable=false,length=100) private String sku;
 @Column(length=64) private String barcode;
 @Column(nullable=false,length=500) private String combinationKey;
 @Column(nullable=false,length=500) private String optionSummary;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal additionalPrice;
 @Column(nullable=false) private int stockQuantity;
 @Column(nullable=false) private int reservedQuantity;
 /** 재고 부족 여부를 판단하는 기준 수량. 가용재고가 이 값 이하로 떨어지면 "부족"으로 표시한다. */
 @Column(nullable=false) private int safetyStock;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ProductSaleStatus saleStatus;
 @Column(nullable=false) private int sortOrder;

 public static final int DEFAULT_SAFETY_STOCK = 5;

 public int getAvailableQuantity(){return Math.max(0,stockQuantity-reservedQuantity);}

 /**
  * 재고 원장(InventoryLedgerService)이 매장별 재고(StoreVariantInventory) 합계를 이 SKU의 전역 프로젝션에
  * 주입한다. 매장 재고 행이 하나라도 있으면 현재재고·예약재고는 이 메서드로만 바뀌고,
  * 아래 receiveStock/reserve/ship/adjust 같은 직접 증감 메서드는 매장 재고가 없는 폴백 경로에서만 쓴다.
  */
 public void applyProjection(int stock,int reserved){this.stockQuantity=Math.max(0,stock);this.reservedQuantity=Math.max(0,reserved);syncSaleStatus();}

 public void update(String sku,String barcode,BigDecimal price,int stock,ProductSaleStatus status){this.sku=sku.trim();this.barcode=barcode==null||barcode.isBlank()?null:barcode.trim();additionalPrice=price;stockQuantity=stock;saleStatus=stock==0&&status==ProductSaleStatus.ON_SALE?ProductSaleStatus.SOLD_OUT:status;}

 /** 입고: 현재재고만 늘린다. */
 public void receiveStock(int quantity){if(quantity<=0)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"입고 수량은 1개 이상이어야 합니다.");stockQuantity+=quantity;syncSaleStatus();}

 /** 주문 생성: 가용재고(현재재고-예약재고) 범위 안에서만 예약재고를 늘린다. 현재재고는 바뀌지 않는다. */
 public void reserve(int quantity){
  if(saleStatus!=ProductSaleStatus.ON_SALE)throw new ValidationBusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
  if(getAvailableQuantity()<quantity)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,"선택한 옵션 조합의 가용 재고가 부족합니다.");
  reservedQuantity+=quantity;
  syncSaleStatus();
 }

 /** 결제 실패/취소: 예약재고만 되돌린다. 현재재고는 애초에 줄인 적이 없어 그대로 둔다. */
 public void releaseReservation(int quantity){reservedQuantity=Math.max(0,reservedQuantity-quantity);syncSaleStatus();}

 /** 출고 완료: 실제로 물건이 나가는 시점이므로 현재재고와 예약재고를 함께 줄인다. */
 public void ship(int quantity){
  if(reservedQuantity<quantity)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"예약된 수량보다 많이 출고할 수 없습니다.");
  if(stockQuantity<quantity)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,"실물 재고보다 많이 출고할 수 없습니다.");
  stockQuantity-=quantity;reservedQuantity-=quantity;
  syncSaleStatus();
 }

 /** 재고 조정(실사 등으로 인한 수기 증감). */
 public void adjust(int delta){int next=stockQuantity+delta;if(next<0)throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"조정 후 재고는 0 이상이어야 합니다.");stockQuantity=next;syncSaleStatus();}

 private void syncSaleStatus(){
  if(getAvailableQuantity()==0&&saleStatus==ProductSaleStatus.ON_SALE)saleStatus=ProductSaleStatus.SOLD_OUT;
  else if(getAvailableQuantity()>0&&saleStatus==ProductSaleStatus.SOLD_OUT)saleStatus=ProductSaleStatus.ON_SALE;
 }
}
