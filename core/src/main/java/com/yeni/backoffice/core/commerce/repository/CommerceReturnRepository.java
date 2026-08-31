package com.yeni.backoffice.core.commerce.repository;

import com.yeni.backoffice.core.commerce.entity.CommerceReturn;
import com.yeni.backoffice.core.commerce.enums.ReturnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommerceReturnRepository extends JpaRepository<CommerceReturn, Long> {
    List<CommerceReturn> findAllByOrderIdOrderByIdDesc(Long orderId);
    List<CommerceReturn> findByStatusOrderByIdDesc(ReturnStatus status);
    List<CommerceReturn> findAllByOrderByIdDesc();
}
