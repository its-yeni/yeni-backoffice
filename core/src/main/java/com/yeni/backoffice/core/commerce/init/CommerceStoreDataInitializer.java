package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Order(2)
public class CommerceStoreDataInitializer implements CommandLineRunner {
    private final CommerceStoreRepository stores;
    public CommerceStoreDataInitializer(CommerceStoreRepository stores){this.stores=stores;}
    @Override @Transactional public void run(String... args){
        createIfMissing("YENI-FOOD-01","Yeni Table 강남점",StoreBusinessType.FOOD_SERVICE,"Yeni Table","테이블 오더와 메뉴 옵션을 운영하는 외식업 예제 매장");
        createIfMissing("YENI-SHOP-01","Yeni Select 온라인몰",StoreBusinessType.ONLINE_RETAIL,"Yeni Select","의류와 리빙 상품을 판매하는 일반 쇼핑몰 예제 브랜드");
        createIfMissing("YENI-FOOD-02","Yeni Table 판교점",StoreBusinessType.FOOD_SERVICE,"Yeni Table","점심 피크와 포장 주문을 함께 운영하는 판교 매장");
        createIfMissing("YENI-FOOD-03","Yeni Table 성수점",StoreBusinessType.FOOD_SERVICE,"Yeni Table","시즌 메뉴와 사이드 구성을 테스트하는 성수 매장");
        createIfMissing("YENI-FOOD-04","Yeni Table 여의도점",StoreBusinessType.FOOD_SERVICE,"Yeni Table","오피스 상권 배달 주문 중심의 운영 매장");
        createIfMissing("YENI-FOOD-05","Yeni Table 잠실점",StoreBusinessType.FOOD_SERVICE,"Yeni Table","주말 가족 주문과 세트 메뉴를 운영하는 매장");
        createIfMissing("YENI-SHOP-02","Yeni Select 패션관",StoreBusinessType.ONLINE_RETAIL,"Yeni Select","의류와 패션 잡화 중심의 온라인 판매 채널");
        createIfMissing("YENI-SHOP-03","Yeni Select 리빙관",StoreBusinessType.ONLINE_RETAIL,"Yeni Select","주방과 데스크 리빙 상품 전문 판매 채널");
        createIfMissing("YENI-SHOP-04","Yeni Select 기프트관",StoreBusinessType.ONLINE_RETAIL,"Yeni Select","선물 포장과 메시지 옵션을 제공하는 채널");
        createIfMissing("YENI-SHOP-05","Yeni Select 아울렛",StoreBusinessType.ONLINE_RETAIL,"Yeni Select","시즌 종료 상품과 한정 재고를 운영하는 채널");
    }
    private void createIfMissing(String code,String name,StoreBusinessType type,String brand,String description){
        if(stores.findByStoreCode(code).isEmpty())stores.save(CommerceStore.builder().storeCode(code).storeName(name).businessType(type).brandName(brand).description(description).active(true).build());
    }
}
