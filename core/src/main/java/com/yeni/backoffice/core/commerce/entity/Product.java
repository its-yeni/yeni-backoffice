package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uk_product_store_code", columnNames = {"storeCode","productCode"}))
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String storeCode;
    @Column private Long brandId;

    @Column(nullable = false, length = 80)
    private String productCode;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false)
    private boolean inventoryManaged;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductSaleStatus saleStatus;

    @Version
    private Long version;

    public void update(String productName, String category, String imageUrl, BigDecimal salePrice, int stockQuantity, boolean inventoryManaged, ProductSaleStatus saleStatus) {
        validate(productName, salePrice, stockQuantity, saleStatus);
        this.productName = productName;
        this.category = category;
        this.imageUrl = imageUrl;
        this.salePrice = salePrice;
        this.stockQuantity = stockQuantity;
        this.inventoryManaged = inventoryManaged;
        this.saleStatus = inventoryManaged && stockQuantity == 0 && ProductSaleStatus.ON_SALE.equals(saleStatus)
                ? ProductSaleStatus.SOLD_OUT : saleStatus;
    }

    public void changeSaleStatus(ProductSaleStatus saleStatus) {
        if (saleStatus == null) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "판매상태는 필수입니다.");
        }
        if (inventoryManaged && ProductSaleStatus.ON_SALE.equals(saleStatus) && stockQuantity == 0) {
            throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH, "재고가 없는 상품은 판매중으로 변경할 수 없습니다.");
        }
        this.saleStatus = saleStatus;
    }

    /** 옵션 SKU 재고 합계를 상품 목록/판매 가능 여부에 사용하는 대표 재고로 반영한다. */
    public void applyVariantStock(int stockQuantity) {
        this.stockQuantity = Math.max(0, stockQuantity);
        if (inventoryManaged && this.stockQuantity == 0 && ProductSaleStatus.ON_SALE.equals(saleStatus)) {
            this.saleStatus = ProductSaleStatus.SOLD_OUT;
        }
    }

    public void changeCategory(String category) {
        this.category = category;
    }

    public void assignStore(String storeCode){if(this.storeCode==null)this.storeCode=storeCode;}
    public void assignBrand(Long brandId){if(this.brandId==null)this.brandId=brandId;}

    public void decreaseStock(int quantity) {
        if (!ProductSaleStatus.ON_SALE.equals(saleStatus)) {
            throw new ValidationBusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        if (quantity <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "상품 수량은 1 이상이어야 합니다.");
        }
        if (!inventoryManaged) {
            return;
        }
        if (stockQuantity < quantity) {
            throw new ValidationBusinessException(ErrorCode.PRODUCT_STOCK_NOT_ENOUGH);
        }
        stockQuantity -= quantity;
        if (stockQuantity == 0) {
            saleStatus = ProductSaleStatus.SOLD_OUT;
        }
    }

    public void increaseStock(int quantity) {
        if (!inventoryManaged) {
            return;
        }
        if (quantity <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "복원 수량은 1 이상이어야 합니다.");
        }
        stockQuantity += quantity;
        if (ProductSaleStatus.SOLD_OUT.equals(saleStatus)) {
            saleStatus = ProductSaleStatus.ON_SALE;
        }
    }

    public static void validate(String productName, BigDecimal salePrice, int stockQuantity, ProductSaleStatus saleStatus) {
        if (productName == null || productName.isBlank()) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "상품명은 필수입니다.");
        }
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "판매가는 0보다 커야 합니다.");
        }
        if (stockQuantity < 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "재고는 0 이상이어야 합니다.");
        }
        if (saleStatus == null) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "판매상태는 필수입니다.");
        }
    }
}
