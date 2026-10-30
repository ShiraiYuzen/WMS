package com.freshmart.wms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 * 启动后访问：http://localhost:8080/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI wmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("仓库拣货与波次管理系统 API")
                        .description("""
                                面向电商仓 / 配送仓的拣货与波次作业平台。
                                
                                **业务闭环**：订单汇聚 → 波次生成 → 拣货执行 → 复核打包 → 出库交接 → 效率复盘
                                
                                **角色**：波次员、拣货员、复核员
                                
                                **当前阶段**：框架搭建期，数据来自内存种子仓储，重启即复位。
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("WorkBuddy AI"))
                        .license(new License().name("Internal Use Only")));
    }
}
