package com.backtoback.reseat.global.common;

import com.backtoback.reseat.global.service.TestDatabaseCleanUpService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

//통합 테스트
 //SpringBootTest, TestProfile, Testcontainers, DB Cleaner 격리 연동
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    @Autowired
    private TestDatabaseCleanUpService testDatabaseCleanUpService;

    @BeforeEach
    void setUpDatabase() {
        testDatabaseCleanUpService.cleanUpAll();
    }
}
