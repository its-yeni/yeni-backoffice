package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="commerce_store", uniqueConstraints=@UniqueConstraint(name="uk_commerce_store_code",columnNames="storeCode"))
public class CommerceStore extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column private Long brandId;
    @Column(nullable=false,length=40) private String storeCode;
    @Column(nullable=false,length=100) private String storeName;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private StoreBusinessType businessType;
    @Column(nullable=false,length=100) private String brandName;
    @Column(nullable=false,length=200) private String description;
    @Column(nullable=false) private boolean active;

    public void update(Long brandId,String name,StoreBusinessType type,String brand,String description,boolean active){this.brandId=brandId;this.storeName=name;this.businessType=type;this.brandName=brand;this.description=description;this.active=active;}
    public void assignBrand(Long brandId){this.brandId=brandId;}
}
