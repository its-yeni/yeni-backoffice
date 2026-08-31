package com.yeni.backoffice.api.commerce.view;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class ProductCategoryViewController {
    @GetMapping("/admin/commerce/categories") public String categories(){return "commerce/categories";}
}
