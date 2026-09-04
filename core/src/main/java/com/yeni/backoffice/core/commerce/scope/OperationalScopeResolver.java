package com.yeni.backoffice.core.commerce.scope;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.repository.CommerceBrandRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OperationalScopeResolver {
    private final CommerceBrandRepository brands;
    private final CommerceStoreRepository stores;

    public OperationalScopeResolver(CommerceBrandRepository brands, CommerceStoreRepository stores) {
        this.brands = brands;
        this.stores = stores;
    }

    @Transactional(readOnly = true)
    public OperationalScope resolve(Long requestedBrandId, Long requestedStoreId) {
        Long brandId = positiveOrNull(requestedBrandId);
        Long storeId = positiveOrNull(requestedStoreId);
        if (brandId == null && storeId == null) return OperationalScope.all();

        if (brandId != null && !brands.existsById(brandId)) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        if (storeId != null) {
            CommerceStore store = stores.findById(storeId)
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "매장을 찾을 수 없습니다."));
            if (brandId != null && !brandId.equals(store.getBrandId())) {
                throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,
                        "선택한 매장은 해당 브랜드에 속하지 않습니다.");
            }
            return OperationalScope.stores(store.getBrandId(), storeId, Set.of(storeId));
        }

        Set<Long> storeIds = stores.findAllByOrderByIdAsc().stream()
                .filter(store -> brandId.equals(store.getBrandId()))
                .map(CommerceStore::getId)
                .collect(Collectors.toSet());
        return OperationalScope.stores(brandId, null, storeIds);
    }

    private Long positiveOrNull(Long value) {
        return value == null || value <= 0 ? null : value;
    }
}
