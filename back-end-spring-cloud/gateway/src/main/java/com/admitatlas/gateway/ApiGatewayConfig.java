package com.admitatlas.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * In the P0 single-application layout there is no reverse-proxy gateway: all controllers
 * live in one Spring MVC context. This module owns shared web-entry concerns (CORS, and later
 * interceptors / rate limiting). When services are split out, it becomes a real Spring Cloud
 * Gateway module and the launcher disappears.
 */
@Configuration
public class ApiGatewayConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
