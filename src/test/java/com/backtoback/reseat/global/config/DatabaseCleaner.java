package com.backtoback.reseat.global.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;

// 통합 테스트 실행 후 DB 상태 자동 격리(Table Truncate)
@Component
public class DatabaseCleaner {

    private final List<String> tableNames = new ArrayList<>();
    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    public void findTableNames() {
        for (EntityType<?> entity : entityManager.getMetamodel().getEntities()) {
            Class<?> javaType = entity.getJavaType();
            if (javaType != null && javaType.getAnnotation(Entity.class) != null) {
                Table tableAnnotation = javaType.getAnnotation(Table.class);
                String tableName;

                // 테이블 어노테이션이 존재하면 해당 이름을 우선 사용
                if (tableAnnotation != null && !tableAnnotation.name().isBlank()) {
                    tableName = tableAnnotation.name();
                } else {
                    // 어노테이션에 테이블명이 명시되지 않은 경우만 fallback으로 snake_case 전환
                    tableName = convertToSnakeCase(entity.getName());
                }
                tableNames.add(tableName);
            }
        }
    }

    @Transactional
    public void execute() {
        entityManager.flush();

        // 외래키 제약 조건 잠시 해제
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        try {
            // 모든 테이블 TRUNCATE
            for (String tableName : tableNames) {
                entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();
            }
        } finally {
            // 외래키 제약 조건 원복
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        }
    }

    private String convertToSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
