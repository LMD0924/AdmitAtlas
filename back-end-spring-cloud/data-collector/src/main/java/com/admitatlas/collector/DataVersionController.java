package com.admitatlas.collector;

import com.admitatlas.common.Disclaimer;
import com.admitatlas.common.ModuleProbe;
import com.admitatlas.common.Result;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F-C-24 / F-S-11：数据版本与规模公示，首页统计数字不再硬编码。 */
@RestController
@RequestMapping("/api/v1/system")
public class DataVersionController {

    private final AdmitAtlasRepository repo;
    private final List<ModuleProbe> probes;

    @Value("${admitatlas.demo-data:true}")
    private boolean demoData;
    @Value("${admitatlas.stage:P1-pilot}")
    private String stage;

    public DataVersionController(AdmitAtlasRepository repo, List<ModuleProbe> probes) {
        this.repo = repo;
        this.probes = probes;
    }

    @GetMapping("/data-version")
    public Result<Models.DataVersion> dataVersion() {
        return Result.ok(repo.dataVersion(demoData));
    }

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Models.DataVersion v = repo.dataVersion(demoData);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage);
        data.put("moduleCount", probes.size());
        data.put("schoolCount", v.schoolCount());
        data.put("majorCount", v.majorCount());
        data.put("admissionRowCount", v.admissionRowCount());
        data.put("segmentRowCount", v.segmentRowCount());
        data.put("policyCount", v.policyCount());
        data.put("dataVersion", v.dataVersion());
        data.put("demoData", v.demoData());
        data.put("sourceNote", v.sourceNote());
        data.put("disclaimer", Disclaimer.TEXT);
        return Result.ok(data);
    }
}
