package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="commerce_brand",uniqueConstraints=@UniqueConstraint(name="uk_commerce_brand_code",columnNames="brandCode"))
public class CommerceBrand extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=40) private String brandCode;
    @Column(nullable=false,length=100) private String brandName;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private StoreBusinessType businessType;
    @Column(nullable=false) private boolean active;
    public void update(String name,StoreBusinessType type,boolean active){brandName=name;businessType=type;this.active=active;}
}
