package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="store_product",uniqueConstraints=@UniqueConstraint(name="uk_store_product",columnNames={"storeId","productId"}))
public class StoreProduct extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false) private Long productId;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal salePrice;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ProductSaleStatus saleStatus;
    @Column(nullable=false) private boolean exposed;
    @Column(nullable=false) private boolean inventoryManaged;
    public void update(BigDecimal price,ProductSaleStatus status,boolean exposed,boolean inventoryManaged){salePrice=price;saleStatus=status;this.exposed=exposed;this.inventoryManaged=inventoryManaged;}
}
