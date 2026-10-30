package com.freshmart.wms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：跨域、静态资源。
 *
 * <p><b>关于跨域</b>：当前阶段前端尚未与后端联调，此处已提前放开 CORS，
 * 后续前端把 mock 切到真实接口时无需再改后端。生产环境请把
 * {@code allowedOriginPatterns} 收敛为具体域名。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
