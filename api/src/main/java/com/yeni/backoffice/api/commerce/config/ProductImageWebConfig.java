package com.yeni.backoffice.api.commerce.config;
import com.yeni.backoffice.api.commerce.service.ProductImageStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class ProductImageWebConfig implements WebMvcConfigurer {
    private final ProductImageStorageService storage;
    public ProductImageWebConfig(ProductImageStorageService storage){this.storage=storage;}
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/uploads/products/**").addResourceLocations(storage.root().toUri().toString()).setCachePeriod(3600);}
}
