package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.OptionGroupTemplate;
import com.yeni.backoffice.core.commerce.entity.OptionValueTemplate;
import com.yeni.backoffice.core.commerce.enums.OptionSelectionType;
import com.yeni.backoffice.core.commerce.repository.OptionGroupTemplateRepository;
import com.yeni.backoffice.core.commerce.repository.OptionValueTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Profile("fly | test | demo")
@Order(125)
public class DemoOptionTemplateSeedInitializer implements CommandLineRunner {
    private final OptionGroupTemplateRepository groups;
    private final OptionValueTemplateRepository values;

    public DemoOptionTemplateSeedInitializer(OptionGroupTemplateRepository groups,
                                             OptionValueTemplateRepository values) {
        this.groups = groups;
        this.values = values;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Set<String> existing = new HashSet<>();
        groups.findAllByOrderBySortOrderAscIdAsc().forEach(group -> existing.add(group.getTemplateName()));
        List<Seed> seeds = List.of(
                new Seed("사이즈 선택", "사이즈", OptionSelectionType.SINGLE, true, 1, 1, new String[][]{{"S","0"},{"M","0"},{"L","2000"}}),
                new Seed("색상 선택", "색상", OptionSelectionType.SINGLE, true, 1, 1, new String[][]{{"Black","0"},{"White","0"},{"Beige","0"}}),
                new Seed("선물 포장", "선물 포장", OptionSelectionType.SINGLE, false, 0, 1, new String[][]{{"포장 안 함","0"},{"기본 포장","500"},{"프리미엄 포장","2000"}}),
                new Seed("도우 선택", "도우", OptionSelectionType.SINGLE, true, 1, 1, new String[][]{{"오리지널","0"},{"씬 도우","0"},{"치즈 크러스트","3000"}}),
                new Seed("토핑 추가", "추가 토핑", OptionSelectionType.MULTIPLE, false, 0, 3, new String[][]{{"치즈 추가","1500"},{"페퍼로니 추가","2000"},{"올리브 추가","1000"}}),
                new Seed("맵기 선택", "맵기", OptionSelectionType.SINGLE, true, 1, 1, new String[][]{{"순한맛","0"},{"보통맛","0"},{"매운맛","0"}}),
                new Seed("온도 선택", "음료 온도", OptionSelectionType.SINGLE, true, 1, 1, new String[][]{{"HOT","0"},{"ICE","500"}}),
                new Seed("샷 추가", "샷 추가", OptionSelectionType.MULTIPLE, false, 0, 2, new String[][]{{"에스프레소 샷","500"},{"디카페인 샷","700"}}),
                new Seed("각인 여부", "각인", OptionSelectionType.SINGLE, false, 0, 1, new String[][]{{"각인 안 함","0"},{"한글 각인","3000"},{"영문 각인","3000"}}),
                new Seed("구성품 추가", "추가 구성품", OptionSelectionType.MULTIPLE, false, 0, 3, new String[][]{{"쇼핑백","500"},{"메시지 카드","300"},{"보관 케이스","2500"}})
        );
        int sortOrder = groups.findAllByOrderBySortOrderAscIdAsc().size();
        for (Seed seed : seeds) {
            if (existing.contains(seed.name())) continue;
            OptionGroupTemplate group = groups.save(OptionGroupTemplate.builder()
                    .templateName(seed.name()).customerDisplayName(seed.displayName())
                    .selectionType(seed.type()).requiredOption(seed.required())
                    .minSelection(seed.min()).maxSelection(seed.max()).sortOrder(++sortOrder).build());
            for (int index = 0; index < seed.values().length; index++) {
                String[] value = seed.values()[index];
                values.save(OptionValueTemplate.builder().templateGroupId(group.getId())
                        .valueName(value[0]).defaultAdditionalPrice(new BigDecimal(value[1]))
                        .sortOrder(index + 1).build());
            }
        }
    }

    private record Seed(String name, String displayName, OptionSelectionType type, boolean required,
                        int min, int max, String[][] values) {}
}
