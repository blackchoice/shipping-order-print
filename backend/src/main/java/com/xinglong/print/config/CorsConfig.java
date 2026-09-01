package com.xinglong.print.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置 - 允许浏览器从远程商城页面调用本地打印服务
 *
 * 使用场景：
 * - 商城网站运行在 119.29.98.147:8899
 * - 打印服务运行在本地 Windows (localhost:8080)
 * - 浏览器可以同时访问两者，但需要 CORS 授权
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许的来源（根据实际商城地址调整）
                .allowedOrigins(
                    "http://119.29.98.147:8899",
                    "https://119.29.98.147:8899",
                    "http://localhost:8899",
                    "http://127.0.0.1:8899"
                )
                // 允许的 HTTP 方法
                .allowedMethods("GET", "POST", "OPTIONS")
                // 允许的请求头
                .allowedHeaders("*")
                // 不需要携带凭证（Cookie）
                .allowCredentials(false)
                // 预检请求缓存时间（秒）
                .maxAge(3600);
    }
}
