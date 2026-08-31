package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CommerceStoreRepository extends JpaRepository<CommerceStore,Long> {
    List<CommerceStore> findAllByOrderByIdAsc();
    Optional<CommerceStore> findByStoreCode(String storeCode);
}
