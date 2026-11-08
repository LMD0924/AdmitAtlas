package com.admitatlas.school;

import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** F-C-10 / F-C-11 / F-C-14：院校档案检索、详情与专业目录。 */
@RestController
@RequestMapping("/api/v1")
public class SchoolController {

    private final AdmitAtlasRepository repo;
    private final boolean demoData;

    public SchoolController(AdmitAtlasRepository repo,
                            @Value("${admitatlas.demo-data:true}") boolean demoData) {
        this.repo = repo;
        this.demoData = demoData;
    }

    /** 多条件检索 + 分页；层次标签走 LIKE，避免把 985/211/双一流 硬编码进代码。 */
    @GetMapping("/schools")
    public Result<Map<String, Object>> schools(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String tier,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(200, Math.max(1, size));
        int safePage = Math.max(1, page);
        List<Models.School> items =
                repo.searchSchools(keyword, provinceCode, tier, safePage, safeSize);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", repo.countSchools(keyword, provinceCode, tier));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    /** 院校档案详情 + 近三年投档数据。 */
    @GetMapping("/schools/{code}")
    public Result<Map<String, Object>> school(
            @PathVariable String code,
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "物理类") String category) {
        Models.School school = repo.school(code)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "未找到院校档案: " + code));
        List<Models.AdmissionScore> history = repo.schoolAdmissionScores(
                provinceCode, code, category, 2023, 2026);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("school", school);
        data.put("admissionScores", history);
        data.put("total", history.size());
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        data.put("sourceNote", "演示数据：录取分数为合成值，最终以各省教育考试院公布为准");
        return Result.ok(data);
    }

    /** 专业目录（门类 → 专业类 → 专业）。 */
    @GetMapping("/majors")
    public Result<Map<String, Object>> majors(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        List<Models.Major> items = repo.majors(keyword, category);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    /** 省份与选科要求字典，供前端下拉/筛选使用（F-B-25 的只读部分）。 */
    @GetMapping("/dictionaries")
    public Result<Map<String, Object>> dictionaries() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provinces", repo.provinces());
        data.put("requirements", repo.requirements());
        data.put("policies", repo.policies());
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }
}
