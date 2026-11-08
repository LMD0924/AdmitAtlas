package com.admitatlas.plan;

import com.admitatlas.api.dto.PlanSaveRequestDTO;
import com.admitatlas.common.Result;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** F-C-35 ~ F-C-43：动态志愿表保存、校验、读取与导出。 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private static final long DEMO_USER_ID = 1L;

    private final PlanService planService;
    private final AdmitAtlasRepository repo;

    public PlanController(PlanService planService, AdmitAtlasRepository repo) {
        this.planService = planService;
        this.repo = repo;
    }

    /** 只校验不落库：前端拖拽 / 加入志愿时实时检查。 */
    @PostMapping("/validate")
    public Result<Map<String, Object>> validate(
            @RequestBody(required = false) PlanSaveRequestDTO req) {
        if (req == null) {
            throw new com.admitatlas.common.BizException(
                    com.admitatlas.common.ResultCode.BAD_REQUEST, "请求体不能为空");
        }
        return Result.ok(planService.validate(req));
    }

    /** 保存方案；校验不通过时返回 valid=false 与逐条错误，不落库。 */
    @PostMapping
    public Result<Map<String, Object>> save(
            @RequestBody(required = false) PlanSaveRequestDTO req) {
        if (req == null) {
            throw new com.admitatlas.common.BizException(
                    com.admitatlas.common.ResultCode.BAD_REQUEST, "请求体不能为空");
        }
        return Result.ok(planService.save(req));
    }

    @GetMapping
    public Result<Map<String, Object>> list() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", repo.plans(DEMO_USER_ID));
        data.put("dataVersion", repo.latestDataVersion());
        return Result.ok(data);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        Models.PlanRecord plan = repo.plan(id)
                .orElseThrow(() -> new com.admitatlas.common.BizException(
                        com.admitatlas.common.ResultCode.NOT_FOUND, "未找到方案: " + id));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("items", repo.planItems(id));
        data.put("dataVersion", plan.dataVersion());
        return Result.ok(data);
    }

    /**
     * F-C-43：CSV / Excel 导出。PDF 由前端浏览器打印生成（@media print），
     * 两者内容与页面方案逐行一致，且都带 data_version、生成时间与免责声明。
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable long id,
            @RequestParam(defaultValue = "csv") String format) {
        if (!"csv".equalsIgnoreCase(format) && !"excel".equalsIgnoreCase(format)) {
            throw new com.admitatlas.common.BizException(
                    com.admitatlas.common.ResultCode.BAD_REQUEST,
                    "仅支持 format=csv / excel（PDF 请用页面打印）");
        }
        byte[] body = planService.exportCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"admitatlas-plan-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(body.length)
                .body(body);
    }
}
