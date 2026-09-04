package com.yeni.backoffice.core.commerce.scope;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.enums.StoreBusinessType;
import com.yeni.backoffice.core.commerce.repository.CommerceBrandRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalScopeResolverTest {
    private CommerceBrandRepository brands;
    private CommerceStoreRepository stores;
    private OperationalScopeResolver resolver;

    @BeforeEach
    void setUp() {
        brands = mock(CommerceBrandRepository.class);
        stores = mock(CommerceStoreRepository.class);
        resolver = new OperationalScopeResolver(brands, stores);
    }

    @Test
    void noSelectionMeansUnrestrictedScope() {
        OperationalScope scope = resolver.resolve(null, null);

        assertThat(scope.unrestricted()).isTrue();
        assertThat(scope.storeIds()).isEmpty();
    }

    @Test
    void brandSelectionContainsOnlyStoresOwnedByBrand() {
        when(brands.existsById(10L)).thenReturn(true);
        when(stores.findAllByOrderByIdAsc()).thenReturn(List.of(store(1L, 10L), store(2L, 10L), store(3L, 20L)));

        OperationalScope scope = resolver.resolve(10L, null);

        assertThat(scope.unrestricted()).isFalse();
        assertThat(scope.brandId()).isEqualTo(10L);
        assertThat(scope.storeIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void storeSelectionNarrowsScopeToOneStore() {
        when(brands.existsById(10L)).thenReturn(true);
        when(stores.findById(2L)).thenReturn(Optional.of(store(2L, 10L)));

        OperationalScope scope = resolver.resolve(10L, 2L);

        assertThat(scope.storeId()).isEqualTo(2L);
        assertThat(scope.storeIds()).containsExactly(2L);
    }

    @Test
    void storeFromAnotherBrandIsRejected() {
        when(brands.existsById(10L)).thenReturn(true);
        when(stores.findById(3L)).thenReturn(Optional.of(store(3L, 20L)));

        assertThatThrownBy(() -> resolver.resolve(10L, 3L))
                .isInstanceOf(ValidationBusinessException.class)
                .hasMessageContaining("해당 브랜드");
    }

    private CommerceStore store(Long id, Long brandId) {
        return CommerceStore.builder()
                .id(id).brandId(brandId).storeCode("STORE-" + id).storeName("매장 " + id)
                .businessType(StoreBusinessType.ONLINE_RETAIL).brandName("브랜드").description("테스트").active(true)
                .build();
    }
}
