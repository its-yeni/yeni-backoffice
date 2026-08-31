package com.yeni.backoffice.core.commerce.repository;
import com.yeni.backoffice.core.commerce.entity.CommerceBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommerceBrandRepository extends JpaRepository<CommerceBrand,Long>{Optional<CommerceBrand> findByBrandCode(String code);List<CommerceBrand> findAllByOrderByIdAsc();}
