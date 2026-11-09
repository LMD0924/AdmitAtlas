package com.admitatlas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 暴露上传目录为静态资源：{@code /files/**} → {@code admitatlas.upload-dir}（默认 ./uploads）。
 *
 * <p>与 /api/** 的鉴权拦截器互不干扰：/files 不走 API 拦截器，且院校配图与头像本就是公开可读资源。
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${admitatlas.upload-dir:./uploads}")
    String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadDir).toAbsolutePath().normalize().toString() + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
