package com.example.wms.config;

import com.example.wms.common.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 启动初始化：
 * <ol>
 *   <li>执行 schema.sql：建表（IF NOT EXISTS），幂等可重复启动；</li>
 *   <li>如果是全新库（carrier 表为空），再执行 seed.sql 灌演示数据；</li>
 *   <li>把历史遗留的明文 / Base64 口令一次性升级为 BCrypt 散列（SEC-02）。</li>
 * </ol>
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
            log.info("schema.sql 执行完成（建表，幂等）");

            if (needSeed()) {
                ClassPathResource seed = new ClassPathResource("seed.sql");
                if (seed.exists()) {
                    ResourceDatabasePopulator sp = new ResourceDatabasePopulator();
                    sp.addScript(seed);
                    sp.setContinueOnError(false);
                    DatabasePopulatorUtils.execute(sp, dataSource);
                    log.info("全新库，seed.sql 执行完成（演示数据已灌入）");
                }
            }
        } catch (Exception e) {
            log.error("schema.sql 执行失败", e);
        }
        migrateLegacyPasswords();
    }

    /** 全新库判断：carrier 表还没有任何数据就认为需要灌种子 */
    private boolean needSeed() {
        try {
            Long n = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM carrier", Long.class);
            return n == null || n == 0;
        } catch (Exception e) {
            log.warn("判断是否需要灌种子失败，跳过：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 老库的 sys_user.password 是明文或明文做了一次 Base64（如 MTIzNDU2），
     * 这里按"能 Base64 解出可打印串就取解码值"的规则还原出原口令，
     * 再用 BCrypt 重新散列写回。执行一次后所有行都是 {@code $2a$} 开头，下次启动直接跳过。
     *
     * <p>注意：PBKDF2 格式（{@code pbkdf2$…}）无法还原明文，不在这里处理，
     * 由 {@code AuthController} 在用户下次登录成功时无感升级为 BCrypt。</p>
     */
    private void migrateLegacyPasswords() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, username, password FROM sys_user");
            int upgraded = 0;
            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                String stored = row.get("password") == null ? null : String.valueOf(row.get("password"));
                String plain = PasswordHasher.decodeLegacy(stored);   // BCrypt / PBKDF2 会返回 null
                if (plain == null || plain.isBlank()) {
                    continue;
                }
                int n = jdbc.update("UPDATE sys_user SET password=? WHERE id=? AND password=?",
                        PasswordHasher.hash(plain), id, stored);
                if (n > 0) {
                    upgraded++;
                    log.warn("账号 {} 的口令已从明文/Base64 升级为 BCrypt 散列", row.get("username"));
                }
            }
            if (upgraded > 0) {
                log.warn("共升级 {} 条历史口令，请通知相关账号重新确认口令强度", upgraded);
            }
        } catch (Exception e) {
            log.error("历史口令迁移失败", e);
        }
    }
}
