package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;import jakarta.persistence.*;import lombok.*;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor @Entity @Table(name="product_addon_group")
public class ProductAddonGroup extends BaseTimeEntity{@Id @GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@Column(nullable=false)private Long productId;@Column(nullable=false,length=100)private String groupName;@Column(nullable=false)private int minQuantity;@Column(nullable=false)private int maxQuantity;@Column(nullable=false)private int sortOrder;@Column(nullable=false)private boolean exposed;public void update(String n,int min,int max,boolean e){groupName=n;minQuantity=min;maxQuantity=max;exposed=e;}}
