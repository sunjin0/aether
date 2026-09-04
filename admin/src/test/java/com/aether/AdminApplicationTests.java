package com.aether;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

/**
 * 表示管理员ApplicationTests。
 */
@SpringBootTest
@TestPropertySource(properties = "springfox.documentation.enabled=false")
@Import(SpringfoxCompatibilityTestConfiguration.class)
class AdminApplicationTests {

    /**
     * 验证 Spring 应用上下文可正常加载。
     */
    @Test
    void contextLoads() {
    }

}
