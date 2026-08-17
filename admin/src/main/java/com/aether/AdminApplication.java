package com.aether;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 表示管理员Application。
 */
@SpringBootApplication
@EnableScheduling
public class AdminApplication {

    /**
     * 启动应用程序。
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

}
