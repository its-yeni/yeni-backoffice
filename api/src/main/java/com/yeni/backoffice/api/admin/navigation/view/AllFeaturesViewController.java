package com.yeni.backoffice.api.admin.navigation.view;

import com.yeni.backoffice.core.admin.auth.AdminRole;
import com.yeni.backoffice.core.admin.navigation.service.AdminNavigationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 사이드바에 없는 화면까지 전부 그룹별로 나열하는 인덱스. */
@Controller
public class AllFeaturesViewController {

    private final AdminNavigationService navigationService;

    public AllFeaturesViewController(AdminNavigationService navigationService) {
        this.navigationService = navigationService;
    }

    @GetMapping("/admin/all-features")
    public String allFeatures(Model model) {
        model.addAttribute("featureGroups", navigationService.getAllFeatureGroups(AdminRole.ADMIN));
        return "admin/all-features";
    }
}
