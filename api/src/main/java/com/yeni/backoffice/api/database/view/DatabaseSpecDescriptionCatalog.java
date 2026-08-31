package com.yeni.backoffice.api.database.view;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

@Component
public class DatabaseSpecDescriptionCatalog {
    private static final String[] RESOURCE_PATHS={"database/database-spec-descriptions.properties","database/commerce-option-descriptions.properties"};
    private final Properties descriptions=new Properties();
    public DatabaseSpecDescriptionCatalog(){
        for(String path:RESOURCE_PATHS){
            ClassPathResource resource=new ClassPathResource(path);
            try(InputStreamReader reader=new InputStreamReader(resource.getInputStream(),StandardCharsets.UTF_8)){descriptions.load(reader);}
            catch(IOException e){throw new IllegalStateException("DB 명세 설명 파일을 읽을 수 없습니다: "+path,e);}
        }
    }
    public Optional<String> tableDescription(String tableName){return get("table."+normalize(tableName));}
    public Optional<String> columnDescription(String tableName,String columnName){String table=normalize(tableName),column=normalize(columnName);return get("column."+table+"."+column).or(()->get("column."+column));}
    public boolean hasTableDescription(String tableName){return tableDescription(tableName).isPresent();}
    public boolean hasColumnDescription(String tableName,String columnName){return columnDescription(tableName,columnName).isPresent();}
    private Optional<String> get(String key){String value=descriptions.getProperty(key);return StringUtils.hasText(value)?Optional.of(value.trim()):Optional.empty();}
    private String normalize(String name){return name==null?"":name.toLowerCase(Locale.ROOT);}
}
