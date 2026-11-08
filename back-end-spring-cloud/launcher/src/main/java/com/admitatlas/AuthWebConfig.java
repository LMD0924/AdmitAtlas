package com.admitatlas;

import com.admitatlas.common.auth.AuthInterceptor;
import com.admitatlas.common.auth.JwtUtil;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 唯一运行应用里挂载鉴权拦截器并暴露 JwtUtil 单例。
 *
 * <p>只拦 /api/**；/actuator/** 与 CORS 预检不受影响。密钥通过环境变量注入，
 * 仓库内的默认值仅供本地演示。
 */
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

    private final JwtUtil jwt;
    private final AuthInterceptor interceptor;

    public AuthWebConfig(AdmitAtlasRepository repo,
                         @Value("${admitatlas.auth.secret}") String secret,
                         @Value("${admitatlas.auth.token-hours:12}") long tokenHours) {
        this.jwt = new JwtUtil(secret, tokenHours * 3600L);
        this.interceptor = new AuthInterceptor(this.jwt, repo);
    }

    /** 供 auth-user 模块的登录 / 注册接口复用同一个签名器。 */
    @Bean
    public JwtUtil admitatlasJwt() {
        return jwt;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**").order(1);
    }
}
