package com.backtoback.reseat.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 통합 테스트 실행 후 DB 상태 자동 격리(Table Truncate)
@Component
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager entityManager;

    private final List<String> tableNames = new ArrayList<>();

    @PostConstruct
    public void findTableNames() {
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            if (entity.getJavaType().getAnnotation(Entity.class) != null) {
                String tableName = convertToSnakeCase(entity.getName());
                tableNames.add(tableName);
            }
        }
    }

    @Transactional
    public void execute() {
        entityManager.flush();

        //외래키 제약 조건 잠시 해제
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        try {
            //모든 테이블 TRUNCATE
            for (String tableName : tableNames) {
                entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
            }
        } finally {
            //외래키 제약 조건 원복
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        }
    }
    private String convertToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
