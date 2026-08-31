package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.SupplierDtos.SupplierResponse;
import com.yeni.backoffice.core.commerce.dto.SupplierDtos.SupplierSaveRequest;
import com.yeni.backoffice.core.commerce.entity.Supplier;
import com.yeni.backoffice.core.commerce.repository.SupplierRepository;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) { this.suppliers = suppliers; }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return suppliers.findAllByOrderByActiveDescNameAsc().stream().map(SupplierResponse::from).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierSaveRequest request) {
        String code = request.supplierCode() == null || request.supplierCode().isBlank()
                ? generateCode() : request.supplierCode().trim().toUpperCase();
        if (suppliers.existsBySupplierCode(code))
            throw new ConflictException(ErrorCode.CONFLICT, "이미 사용 중인 공급처 코드입니다.");
        Supplier saved = suppliers.save(Supplier.builder()
                .supplierCode(code).name(request.name().trim())
                .managerName(blankToNull(request.managerName())).contact(blankToNull(request.contact()))
                .leadTimeDays(Math.max(1, request.leadTimeDays())).active(true)
                .memo(blankToNull(request.memo())).build());
        return SupplierResponse.from(saved);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierSaveRequest request) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "공급처를 찾을 수 없습니다."));
        supplier.update(request.name(), request.managerName(), request.contact(),
                Math.max(1, request.leadTimeDays()), request.memo());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public SupplierResponse changeActive(Long id, boolean active) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "공급처를 찾을 수 없습니다."));
        supplier.changeActive(active);
        return SupplierResponse.from(supplier);
    }

    private String generateCode() {
        String base = "SUP-" + String.format("%03d", suppliers.count() + 1);
        String code = base;
        int n = 1;
        while (suppliers.existsBySupplierCode(code)) code = base + "-" + (n++);
        return code;
    }
    private static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
