package com.yeni.backoffice.core.commerce.entity;
import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="product_category", uniqueConstraints=@UniqueConstraint(name="uk_product_category_store_name", columnNames={"storeCode","categoryName"}))
public class ProductCategory extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(length=40) private String storeCode;
    @Column private Long brandId;
    @Column(nullable=false,length=100) private String categoryName;
    @Column(nullable=false) private int sortOrder;
    @Column(nullable=false) private boolean exposed;
    public void rename(String name){categoryName=name;}
    public void changeExposure(boolean value){exposed=value;}
    public void changeSortOrder(int value){sortOrder=value;}
    public void assignStore(String value){if(storeCode==null)storeCode=value;}
    public void assignBrand(Long value){if(brandId==null)brandId=value;}
}
