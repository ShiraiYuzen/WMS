package com.example.wms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动时自动执行 schema.sql：建表（IF NOT EXISTS）+ 插入种子数据（INSERT IGNORE），幂等可重复启动。
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final String jdbcUrl;

    public DatabaseInitializer(DataSource dataSource, @Value("${spring.datasource.url}") String jdbcUrl) {
        this.dataSource = dataSource;
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql:")) {
            log.warn("未使用 MySQL 数据源，跳过自动建表初始化");
            return;
        }
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("schema.sql"));
            populator.setContinueOnError(false);
            DatabasePopulatorUtils.execute(populator, dataSource);
            log.info("schema.sql 执行完成（建表 + 种子数据，幂等）");
        } catch (Exception e) {
            log.error("schema.sql 执行失败", e);
        }
    }
}
