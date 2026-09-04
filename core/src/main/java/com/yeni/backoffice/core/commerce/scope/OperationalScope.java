package com.yeni.backoffice.core.commerce.scope;

import java.util.Set;

/** 브랜드·매장 선택을 모든 운영 조회에서 재사용할 수 있도록 정규화한 범위 값 객체. */
public record OperationalScope(Long brandId, Long storeId, Set<Long> storeIds, boolean unrestricted) {
    public OperationalScope {
        storeIds = storeIds == null ? Set.of() : Set.copyOf(storeIds);
    }

    public static OperationalScope all() {
        return new OperationalScope(null, null, Set.of(), true);
    }

    public static OperationalScope stores(Long brandId, Long storeId, Set<Long> storeIds) {
        return new OperationalScope(brandId, storeId, storeIds, false);
    }

    public boolean includes(Long candidateStoreId) {
        return unrestricted || candidateStoreId != null && storeIds.contains(candidateStoreId);
    }
}
