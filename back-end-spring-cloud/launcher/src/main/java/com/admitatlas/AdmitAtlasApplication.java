package com.admitatlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 全工程唯一的启动类。
 *
 * <p>P0 采用模块化单体：一个进程、一个 Spring 上下文、一个端口（默认 8080）。
 * 启动类位于 com.admitatlas 根包，自动扫描所有平铺模块（common / api / 各业务模块）
 * 的 @Controller、@Service、@Component、@Configuration。
 *
 * <p>未来要把某个模块拆成独立微服务时，在该模块内新增启动类与配置、
 * 并从 launcher 的依赖中移除即可，业务代码无需改动。
 */
@SpringBootApplication(scanBasePackages = "com.admitatlas")
public class AdmitAtlasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdmitAtlasApplication.class, args);
    }
}
