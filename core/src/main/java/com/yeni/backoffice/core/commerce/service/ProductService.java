package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.ProductDtos.ProductPageResponse;
import com.yeni.backoffice.core.commerce.dto.ProductDtos.ProductResponse;
import com.yeni.backoffice.core.commerce.dto.ProductDtos.ProductSaveRequest;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductOptionService productOptionService;

    public ProductService(ProductRepository productRepository, ProductOptionService productOptionService) {
        this.productRepository = productRepository;
        this.productOptionService = productOptionService;
    }

    public ProductPageResponse search(String keyword, String saleStatus, int page, int size) {
        return search(keyword, saleStatus, null, page, size);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse search(String keyword, String saleStatus, String storeCode, int page, int size) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = size <= 0 ? 15 : Math.min(size, 100);
        Page<Product> result = productRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : null,
                parseStatus(saleStatus, false), StringUtils.hasText(storeCode) ? storeCode.trim() : null,
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "id")));
        return new ProductPageResponse(result.getContent().stream().map(ProductResponse::from).toList(),
                result.getTotalElements(), normalizedPage, normalizedSize);
    }

    @Transactional
    public ProductResponse create(ProductSaveRequest request) {
        String storeCode = StringUtils.hasText(request.storeCode()) ? request.storeCode().trim() : "YENI-SHOP-01";
        String productCode = resolveProductCode(request.productCode(), storeCode);
        if (productRepository.findByStoreCodeAndProductCode(storeCode, productCode).isPresent()) {
            throw new ConflictException(ErrorCode.PRODUCT_CODE_DUPLICATED);
        }
        ProductSaleStatus status = parseStatus(request.saleStatus(), true);
        Product.validate(request.productName(), request.salePrice(), request.stockQuantity(), status);
        boolean inventoryManaged = request.inventoryManaged() == null || request.inventoryManaged();
        Product product = Product.builder()
                .storeCode(storeCode)
                .productCode(productCode)
                .productName(request.productName().trim())
                .category(trimToNull(request.category()))
                .imageUrl(trimToNull(request.imageUrl()))
                .salePrice(request.salePrice())
                .stockQuantity(request.stockQuantity())
                .inventoryManaged(inventoryManaged)
                .saleStatus(inventoryManaged && request.stockQuantity() == 0 && ProductSaleStatus.ON_SALE.equals(status)
                        ? ProductSaleStatus.SOLD_OUT : status)
                .build();
        Product saved = productRepository.save(product);
        List<Long> templateIds = request.optionTemplateIds() == null ? List.of() : request.optionTemplateIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        templateIds.forEach(templateId -> productOptionService.applyTemplate(saved.getId(), templateId));
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long productId, ProductSaveRequest request) {
        Product product = get(productId);
        if (StringUtils.hasText(request.productCode()) && !product.getProductCode().equals(request.productCode().trim())) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "상품 코드는 등록 후 변경할 수 없습니다.");
        }
        product.update(request.productName().trim(), trimToNull(request.category()), trimToNull(request.imageUrl()),
                request.salePrice(), request.stockQuantity(), request.inventoryManaged() == null || request.inventoryManaged(),
                parseStatus(request.saleStatus(), true));
        List<Long> templateIds = request.optionTemplateIds() == null ? List.of() : request.optionTemplateIds().stream()
                .filter(Objects::nonNull).distinct().toList();
        templateIds.forEach(templateId -> productOptionService.applyTemplate(product.getId(), templateId));
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse changeStatus(Long productId, String status) {
        Product product = get(productId);
        product.changeSaleStatus(parseStatus(status, true));
        return ProductResponse.from(product);
    }

    private Product get(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductSaleStatus parseStatus(String value, boolean required) {
        if (!StringUtils.hasText(value)) {
            if (required) throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "판매 상태는 필수입니다.");
            return null;
        }
        try {
            return ProductSaleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 판매 상태입니다.");
        }
    }

    private String resolveProductCode(String requestedCode, String storeCode) {
        if (StringUtils.hasText(requestedCode)) return requestedCode.trim().toUpperCase(Locale.ROOT);
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 10; attempt++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
            String generated = "PRD-" + date + "-" + suffix;
            if (productRepository.findByStoreCodeAndProductCode(storeCode, generated).isEmpty()) return generated;
        }
        throw new ConflictException(ErrorCode.PRODUCT_CODE_DUPLICATED);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
