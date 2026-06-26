package com.company.knowledge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 发电知识中心 · 知识管理平台后端启动类。
 */
@SpringBootApplication
@MapperScan("com.company.knowledge.**.mapper")
@EnableAsync
public class KnowledgePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgePlatformApplication.class, args);
    }
}
