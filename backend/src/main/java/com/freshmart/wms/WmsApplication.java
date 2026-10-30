package com.freshmart.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 仓库拣货与波次管理系统 —— 应用启动类。
 *
 * <p>业务闭环：订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘。
 *
 * <p><b>当前阶段说明</b>：项目处于「框架搭建期」，尚未接入数据库。
 * 所有业务数据由 {@code com.freshmart.wms.repository} 下的内存仓储承载，
 * 应用重启后数据回到种子状态。后续接入 MySQL 时，只需把 Service 层依赖的
 * 仓储接口替换为 MyBatis-Plus 的 Mapper 实现即可，Controller / Service 无需改动。
 *
 * @author WorkBuddy AI
 */
@EnableAsync
@EnableCaching
@SpringBootApplication
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
        System.out.println("""

                ============================================================
                  仓库拣货与波次管理系统 后端已启动
                  接口文档: http://localhost:8080/swagger-ui.html
                  健康检查: http://localhost:8080/actuator/health
                ============================================================
                """);
    }
}
