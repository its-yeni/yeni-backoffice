package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderAddonCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderItemCreateRequest;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductAddonGroup;
import com.yeni.backoffice.core.commerce.entity.ProductAddonItem;
import com.yeni.backoffice.core.commerce.entity.ProductOptionGroup;
import com.yeni.backoffice.core.commerce.entity.ProductOptionValue;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.OptionSelectionType;
import com.yeni.backoffice.core.commerce.enums.ProductSaleStatus;
import com.yeni.backoffice.core.commerce.repository.ProductAddonGroupRepository;
import com.yeni.backoffice.core.commerce.repository.ProductAddonItemRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionGroupRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionValueRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a server-authoritative order plan from a request.
 *
 * <p>The plan is shared by web and POS entry points. Clients submit selections and
 * quantities only; current products, options, SKUs and prices are resolved here.</p>
 */
@Service
public class OrderCreationPlanningService {
    private final ProductRepository productRepository;
    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductOptionValueRepository optionValueRepository;
    private final ProductAddonGroupRepository addonGroupRepository;
    private final ProductAddonItemRepository addonItemRepository;
    private final ProductVariantRepository variantRepository;

    public OrderCreationPlanningService(ProductRepository productRepository,
            ProductOptionGroupRepository optionGroupRepository,
            ProductOptionValueRepository optionValueRepository,
            ProductAddonGroupRepository addonGroupRepository,
            ProductAddonItemRepository addonItemRepository,
            ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.optionValueRepository = optionValueRepository;
        this.addonGroupRepository = addonGroupRepository;
        this.addonItemRepository = addonItemRepository;
        this.variantRepository = variantRepository;
    }

    public OrderPlan plan(CommerceOrderCreateRequest request) {
        validateRequest(request);
        Set<Long> productIds = new HashSet<>();
        Set<Long> optionIds = new HashSet<>();
        request.items().forEach(item -> {
            productIds.add(item.productId());
            safe(item.optionValueIds()).forEach(optionIds::add);
            safe(item.addOns()).forEach(addon -> productIds.add(addon.productId()));
        });

        Map<Long, Product> products = productRepository.findAllByIdForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (products.size() != productIds.size()) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        Map<Long, ProductOptionValue> values = optionIds.isEmpty()
                ? Map.of()
                : optionValueRepository.findAllByIdForUpdate(optionIds).stream()
                        .collect(Collectors.toMap(ProductOptionValue::getId, Function.identity()));
        if (values.size() != optionIds.size()) {
            throw validation("존재하지 않는 옵션이 포함되어 있습니다.");
        }

        List<PlannedItem> items = new ArrayList<>();
        for (CommerceOrderItemCreateRequest item : request.items()) {
            Product product = products.get(item.productId());
            OptionSelection option = validateOptions(product, item, values);
            ProductVariant variant = findVariant(product.getId(), option.values());
            BigDecimal optionAmount = variant == null
                    ? option.amount()
                    : variant.getAdditionalPrice().add(extraOptionAmount(variant, option.values()));
            items.add(new PlannedItem(product, option.values(), variant, optionAmount, item.quantity(),
                    option.summary(), option.ids(), false));
            addAddons(product, item, products, items);
        }
        return new OrderPlan(List.copyOf(items));
    }

    private OptionSelection validateOptions(Product product, CommerceOrderItemCreateRequest item,
            Map<Long, ProductOptionValue> locked) {
        List<ProductOptionGroup> groups = optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        Map<Long, ProductOptionGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(ProductOptionGroup::getId, Function.identity()));
        List<ProductOptionValue> selected = safe(item.optionValueIds()).stream().distinct().map(locked::get).toList();
        Map<Long, List<ProductOptionValue>> byGroup = selected.stream()
                .collect(Collectors.groupingBy(ProductOptionValue::getOptionGroupId));
        if (byGroup.keySet().stream().anyMatch(id -> !groupMap.containsKey(id))) {
            throw validation("다른 상품의 옵션은 선택할 수 없습니다.");
        }
        if (selected.stream().anyMatch(value -> value.getSaleStatus() != ProductSaleStatus.ON_SALE)) {
            throw validation("판매 중이 아닌 옵션이 포함되어 있습니다.");
        }
        for (ProductOptionGroup group : groups) {
            int count = byGroup.getOrDefault(group.getId(), List.of()).size();
            int minimum = group.isRequiredOption() ? Math.max(1, group.getMinSelection()) : group.getMinSelection();
            if (count < minimum || count > group.getMaxSelection()
                    || (group.getSelectionType() == OptionSelectionType.SINGLE && count > 1)) {
                throw validation(group.getGroupName() + " 옵션 선택 개수가 올바르지 않습니다.");
            }
        }
        BigDecimal amount = selected.stream().map(ProductOptionValue::getAdditionalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String summary = groups.stream().filter(group -> byGroup.containsKey(group.getId()))
                .map(group -> group.getGroupName() + ": " + byGroup.get(group.getId()).stream()
                        .map(ProductOptionValue::getValueName).collect(Collectors.joining(", ")))
                .collect(Collectors.joining(" / "));
        String ids = selected.stream().map(value -> value.getId().toString()).collect(Collectors.joining(","));
        return new OptionSelection(selected, amount, summary.isBlank() ? null : summary, ids.isBlank() ? null : ids);
    }

    private void addAddons(Product base, CommerceOrderItemCreateRequest item, Map<Long, Product> products,
            List<PlannedItem> plans) {
        List<ProductAddonGroup> groups = addonGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(base.getId());
        Map<Long, ProductAddonGroup> groupMap = groups.stream()
                .collect(Collectors.toMap(ProductAddonGroup::getId, Function.identity()));
        Map<Long, Integer> totals = new HashMap<>();
        for (CommerceOrderAddonCreateRequest addon : safe(item.addOns())) {
            ProductAddonGroup group = groupMap.get(addon.addonGroupId());
            if (group == null) throw validation("다른 상품의 추가상품 그룹은 선택할 수 없습니다.");
            ProductAddonItem link = addonItemRepository
                    .findByAddonGroupIdAndAddonProductId(group.getId(), addon.productId())
                    .orElseThrow(() -> validation("등록되지 않은 추가상품입니다."));
            if (addon.quantity() > link.getMaxQuantity()) throw validation("추가상품 최대 선택 수량을 초과했습니다.");
            totals.merge(group.getId(), addon.quantity(), Integer::sum);
            int totalQuantity = Math.multiplyExact(addon.quantity(), item.quantity());
            plans.add(new PlannedItem(products.get(addon.productId()), List.of(), null, BigDecimal.ZERO,
                    totalQuantity, group.getGroupName(), null, true));
        }
        for (ProductAddonGroup group : groups) {
            int count = totals.getOrDefault(group.getId(), 0);
            if (count < group.getMinQuantity() || count > group.getMaxQuantity()) {
                throw validation(group.getGroupName() + " 추가상품 선택 수량이 올바르지 않습니다.");
            }
        }
    }

    private ProductVariant findVariant(Long productId, List<ProductOptionValue> selected) {
        List<ProductVariant> configured = variantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        if (configured.isEmpty()) return null;
        ProductVariant found;
        if (selected.isEmpty()) {
            found = configured.stream().filter(variant -> "BASE".equals(variant.getCombinationKey())).findFirst().orElse(null);
            return lockVariant(found);
        }
        Set<String> ids = selected.stream().map(value -> value.getId().toString()).collect(Collectors.toSet());
        found = configured.stream()
                .filter(variant -> ids.containsAll(Arrays.asList(variant.getCombinationKey().split(","))))
                .findFirst().orElse(null);
        if (found == null) {
            boolean allDefault = selected.stream()
                    .allMatch(value -> value.getAdditionalPrice() == null || value.getAdditionalPrice().signum() == 0);
            ProductVariant base = configured.stream().filter(variant -> "BASE".equals(variant.getCombinationKey()))
                    .findFirst().orElse(null);
            if (allDefault && base != null) found = base;
            else throw validation("선택한 옵션 조합은 판매 가능한 SKU가 아닙니다.");
        }
        return lockVariant(found);
    }

    private ProductVariant lockVariant(ProductVariant variant) {
        if (variant == null) return null;
        return variantRepository.findByIdForUpdate(variant.getId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));
    }

    private BigDecimal extraOptionAmount(ProductVariant variant, List<ProductOptionValue> selected) {
        Set<String> variantIds = new HashSet<>(Arrays.asList(variant.getCombinationKey().split(",")));
        return selected.stream().filter(value -> !variantIds.contains(value.getId().toString()))
                .map(ProductOptionValue::getAdditionalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateRequest(CommerceOrderCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.buyerName())) throw validation("구매자명은 필수입니다.");
        if (request.items() == null || request.items().isEmpty()) throw validation("주문 상품은 1개 이상이어야 합니다.");
        if (request.items().stream().anyMatch(item -> item.productId() == null || item.quantity() <= 0)) {
            throw validation("상품 ID와 1개 이상의 수량이 필요합니다.");
        }
        if (request.items().stream().flatMap(item -> safe(item.addOns()).stream())
                .anyMatch(addon -> addon.addonGroupId() == null || addon.productId() == null || addon.quantity() <= 0)) {
            throw validation("추가상품 정보가 올바르지 않습니다.");
        }
    }

    private ValidationBusinessException validation(String message) {
        return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record OptionSelection(List<ProductOptionValue> values, BigDecimal amount, String summary, String ids) {}

    public record OrderPlan(List<PlannedItem> items) {
        public BigDecimal productAmount() {
            return items.stream().map(PlannedItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record PlannedItem(Product product, List<ProductOptionValue> optionValues, ProductVariant variant,
            BigDecimal optionAmount, int quantity, String optionSummary, String optionValueIds, boolean addon) {
        public BigDecimal amount() {
            return product.getSalePrice().add(optionAmount).multiply(BigDecimal.valueOf(quantity));
        }
    }
}
