package com.yeni.backoffice.core.commerce.dto;

import com.yeni.backoffice.core.commerce.entity.Supplier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record SupplierSaveRequest(String supplierCode, @NotBlank String name, String managerName,
            String contact, @Positive int leadTimeDays, String memo) {}

    public record SupplierResponse(Long id, String supplierCode, String name, String managerName,
            String contact, int leadTimeDays, boolean active, String memo) {
        public static SupplierResponse from(Supplier s) {
            return new SupplierResponse(s.getId(), s.getSupplierCode(), s.getName(), s.getManagerName(),
                    s.getContact(), s.getLeadTimeDays(), s.isActive(), s.getMemo());
        }
    }
}
