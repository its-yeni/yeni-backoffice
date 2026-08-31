package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;import com.yeni.backoffice.core.common.entity.BaseTimeEntity;import com.yeni.backoffice.core.common.exception.*;import jakarta.persistence.*;import lombok.*;import java.math.BigDecimal;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="product_option_value")
public class ProductOptionValue extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long optionGroupId;@Column(nullable=false,length=100)private String valueName;@Column(nullable=false,precision=19,scale=2)private BigDecimal additionalPrice;@Column(nullable=false)private boolean inventoryManaged;@Column(nullable=false)private int stockQuantity;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)private ProductSaleStatus saleStatus;@Column(nullable=false)private int sortOrder;
 public void update(String n,BigDecimal p,boolean managed,int stock,ProductSaleStatus status){valueName=n;additionalPrice=p;inventoryManaged=managed;stockQuantity=stock;saleStatus=managed&&stock==0&&status==ProductSaleStatus.ON_SALE?ProductSaleStatus.SOLD_OUT:status;}
 public void decreaseStock(int q){if(!inventoryManaged)return;if(stockQuantity<q)throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH,"옵션 재고가 부족합니다.");stockQuantity-=q;if(stockQuantity==0)saleStatus=ProductSaleStatus.SOLD_OUT;}
 public void increaseStock(int q){if(!inventoryManaged)return;stockQuantity+=q;if(saleStatus==ProductSaleStatus.SOLD_OUT)saleStatus=ProductSaleStatus.ON_SALE;}
 public void changeSortOrder(int n){sortOrder=n;}
}
