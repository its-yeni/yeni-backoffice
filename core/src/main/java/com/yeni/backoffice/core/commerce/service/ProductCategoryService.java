package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.ProductCategoryDtos.*;
import com.yeni.backoffice.core.commerce.entity.ProductCategory;
import com.yeni.backoffice.core.commerce.repository.ProductCategoryRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductCategoryService {
    private static final String DEFAULT_STORE="YENI-SHOP-01";
    private final ProductCategoryRepository repository; private final ProductRepository products;
    public ProductCategoryService(ProductCategoryRepository repository,ProductRepository products){this.repository=repository;this.products=products;}
    @Transactional(readOnly=true) public List<CategoryResponse> getAll(){return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::from).toList();}
    @Transactional(readOnly=true) public List<CategoryResponse> getAll(String storeCode){return storeCode==null||storeCode.isBlank()?getAll():repository.findByStoreCodeOrderBySortOrderAscIdAsc(storeCode).stream().map(CategoryResponse::from).toList();}
    @Transactional public CategoryResponse create(CategoryRequest request){String name=name(request),store=store(request);if(repository.findByStoreCodeAndCategoryName(store,name).isPresent())throw new ConflictException(ErrorCode.CONFLICT,"이미 존재하는 카테고리입니다.");int order=repository.findByStoreCodeOrderBySortOrderAscIdAsc(store).size()+1;return CategoryResponse.from(repository.save(ProductCategory.builder().storeCode(store).categoryName(name).sortOrder(order).exposed(request.exposed()==null||request.exposed()).build()));}
    @Transactional public CategoryResponse update(Long id,CategoryRequest request){ProductCategory category=get(id);String old=category.getCategoryName(),name=name(request);repository.findByStoreCodeAndCategoryName(category.getStoreCode(),name).filter(found->!found.getId().equals(id)).ifPresent(found->{throw new ConflictException(ErrorCode.CONFLICT,"이미 존재하는 카테고리입니다.");});category.rename(name);if(!old.equals(name))products.findByStoreCodeAndCategory(category.getStoreCode(),old).forEach(product->product.changeCategory(name));if(request.exposed()!=null)category.changeExposure(request.exposed());return CategoryResponse.from(category);}
    @Transactional public CategoryResponse exposure(Long id,boolean value){ProductCategory category=get(id);category.changeExposure(value);return CategoryResponse.from(category);}
    @Transactional public List<CategoryResponse> move(Long id,String direction){ProductCategory category=get(id);List<ProductCategory> list=repository.findByStoreCodeOrderBySortOrderAscIdAsc(category.getStoreCode());int index=list.indexOf(category),target="UP".equalsIgnoreCase(direction)?index-1:index+1;if(index<0||target<0||target>=list.size())return list.stream().map(CategoryResponse::from).toList();ProductCategory other=list.get(target);int order=category.getSortOrder();category.changeSortOrder(other.getSortOrder());other.changeSortOrder(order);return repository.findByStoreCodeOrderBySortOrderAscIdAsc(category.getStoreCode()).stream().map(CategoryResponse::from).toList();}
    private ProductCategory get(Long id){return repository.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"카테고리를 찾을 수 없습니다."));}
    private String name(CategoryRequest request){if(request==null||request.categoryName()==null||request.categoryName().isBlank())throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,"카테고리명을 입력해 주세요.");return request.categoryName().trim();}
    private String store(CategoryRequest request){return request.storeCode()==null||request.storeCode().isBlank()?DEFAULT_STORE:request.storeCode().trim();}
}
