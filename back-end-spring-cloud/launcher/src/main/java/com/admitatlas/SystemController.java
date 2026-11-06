package com.admitatlas;

import com.admitatlas.common.ModuleProbe;
import com.admitatlas.common.Result;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单应用自检接口：列出被唯一启动类扫描到的全部模块。
 * 用于验证「模块平铺 + 单启动类」的装配是否正确，P1 可保留作健康检查。
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final List<ModuleProbe> probes;
    private final Environment environment;

    public SystemController(List<ModuleProbe> probes, Environment environment) {
        this.probes = probes;
        this.environment = environment;
    }

    @GetMapping("/modules")
    public Result<Map<String, Object>> modules() {
        List<Map<String, String>> modules = probes.stream()
                .map(p -> Map.of("module", p.moduleName(), "status", p.status()))
                .sorted(Comparator.comparing(m -> m.get("module")))
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", environment.getProperty("spring.application.name", "admitatlas"));
        data.put("port", environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8080")));
        data.put("deployMode", "single-application");
        data.put("moduleCount", modules.size());
        data.put("modules", modules);
        return Result.ok(data);
    }
}
