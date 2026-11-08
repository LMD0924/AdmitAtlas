package com.admitatlas.policyrule;

import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.Json;
import com.admitatlas.common.support.SubjectRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 省 × 年 × 考试模式 的志愿规则，以及选科资格校验。
 * 志愿单位与志愿数量一律来自 rule_policy，禁止在前后端硬编码。
 */
@RestController
@RequestMapping("/api/v1")
public class PolicyController {

    private static final Set<String> PRIMARY_312 = Set.of("物理", "历史");
    private static final Set<String> SECONDARY_312 = Set.of("化学", "生物", "政治", "地理");
    private static final Set<String> SUBJECTS_33 =
            Set.of("物理", "化学", "生物", "历史", "政治", "地理", "技术");

    private final AdmitAtlasRepository repo;

    public PolicyController(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    /** F-C-02 / F-C-26 / F-C-35：本省当年志愿结构。 */
    @GetMapping("/rules/policy")
    public Result<Map<String, Object>> policy(
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "2026") int examYear) {
        Models.RulePolicy policy = requirePolicy(provinceCode, examYear);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provinceCode", policy.provinceCode());
        data.put("examYear", policy.examYear());
        data.put("examMode", policy.examMode());
        data.put("volunteerUnit", policy.volunteerUnit());
        data.put("volunteerCount", policy.volunteerCount());
        data.put("groupMajorCount", policy.groupMajorCount());
        data.put("batches", policy.batches() == null ? List.of()
                : List.of(policy.batches().split(",")));
        data.put("sortFields", Json.stringList(policy.sortFields()));
        data.put("subjectRules", Json.objectMap(policy.subjectRules()));
        data.put("effectiveFrom", policy.effectiveFrom());
        data.put("dataVersion", policy.dataVersion());
        return Result.ok(data);
    }

    /** F-C-47：政策百科（省份 + 规则 + 术语说明）。 */
    @GetMapping("/policies/{province}")
    public Result<Map<String, Object>> policyBook(
            @PathVariable String province,
            @RequestParam(defaultValue = "2026") int examYear) {
        Models.Province p = repo.province(province)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "未找到省份: " + province));
        Models.RulePolicy policy = requirePolicy(province, examYear);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("province", p);
        data.put("policy", policy);
        data.put("subjectRules", Json.objectMap(policy.subjectRules()));
        data.put("terms", List.of(
                Map.of("term", "位次", "desc", "本省同科类考生中的排名，是跨年比较的唯一口径"),
                Map.of("term", "院校专业组", "desc", "3+1+2 模式下的一志愿单位，组内专业共享选科要求"),
                Map.of("term", "冲 / 稳 / 保", "desc", "按你的位次与院校近三年最低位次的差距划分的风险梯度"),
                Map.of("term", "匹配度", "desc", "0~100 的相对参考值，不代表录取概率")));
        data.put("disclaimer", "所有建议仅供参考，最终以各省教育考试院公布数据为准");
        return Result.ok(data);
    }

    public record ComboRequest(String examMode, List<String> subjectCombo) {
    }

    /** F-C-03：选科组合校验（"物理+物理+化学" 这类非法组合必须拒绝并说明原因）。 */
    @PostMapping("/rules/subject-combo/validate")
    public Result<Map<String, Object>> validateCombo(
            @RequestBody(required = false) ComboRequest req) {
        if (req == null || req.examMode() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "examMode 不能为空");
        }
        List<String> combo = req.subjectCombo() == null ? List.of() : req.subjectCombo();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String mode = req.examMode();

        if ("TRADITIONAL".equals(mode)) {
            if (!combo.isEmpty()) {
                warnings.add("传统文理分科不采集选科组合，已忽略");
            }
        } else if ("GROUP_3_1_2".equals(mode)) {
            if (combo.size() != 3) {
                errors.add("3+1+2 模式必须选择 3 门科目（1 门首选 + 2 门再选），当前 " + combo.size() + " 门");
            }
            long primary = combo.stream().filter(PRIMARY_312::contains).count();
            if (primary != 1) {
                errors.add("首选科目必须且只能从「物理 / 历史」中选 1 门，当前 " + primary + " 门");
            }
            if (combo.contains("物理") && combo.contains("历史")) {
                errors.add("「物理」与「历史」不能同时作为首选科目");
            }
            for (String s : combo) {
                if (!PRIMARY_312.contains(s) && !SECONDARY_312.contains(s)) {
                    errors.add("未知科目: " + s);
                }
            }
            if (combo.size() != combo.stream().distinct().count()) {
                errors.add("选科组合存在重复科目");
            }
        } else if ("GROUP_3_3".equals(mode)) {
            if (combo.size() != 3) {
                errors.add("3+3 模式必须选择 3 门科目，当前 " + combo.size() + " 门");
            }
            for (String s : combo) {
                if (!SUBJECTS_33.contains(s)) {
                    errors.add("未知科目: " + s);
                }
            }
            if (combo.size() != combo.stream().distinct().count()) {
                errors.add("选科组合存在重复科目");
            }
        } else {
            errors.add("不支持的考试模式: " + mode);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", errors.isEmpty());
        data.put("errors", errors);
        data.put("warnings", warnings);
        data.put("combo", combo);
        return Result.ok(data);
    }

    public record PlanItemInput(String schoolCode, String groupCode, String majorCode) {
    }

    public record PlanCheckRequest(String provinceCode, Integer examYear, String category,
                                   List<String> subjectCombo, List<PlanItemInput> items) {
    }

    /** F-C-38：逐条选科资格校验，提示缺少哪个科目要求。 */
    @PostMapping("/rules/plan-items/validate")
    public Result<Map<String, Object>> validatePlanItems(
            @RequestBody(required = false) PlanCheckRequest req) {
        if (req == null || req.items() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "items 不能为空");
        }
        String province = req.provinceCode() == null ? "42" : req.provinceCode();
        int year = req.examYear() == null ? 2026 : req.examYear();
        String category = req.category() == null ? "物理类" : req.category();
        List<Models.PlanUnit> units = repo.planUnits(province, year, category);
        Map<String, Models.PlanUnit> byKey = new LinkedHashMap<>();
        for (Models.PlanUnit u : units) {
            byKey.put(key(u.schoolCode(), u.groupCode(), u.majorCode()), u);
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<String> combo = req.subjectCombo() == null ? List.of() : req.subjectCombo();
        for (int i = 0; i < req.items().size(); i++) {
            PlanItemInput item = req.items().get(i);
            Models.PlanUnit unit = byKey.get(
                    key(item.schoolCode(), item.groupCode(), item.majorCode()));
            if (unit == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i + 1);
                err.put("message", "未找到该志愿在 " + year + " 年的招生计划，无法确认选科要求");
                errors.add(err);
                continue;
            }
            SubjectRequirements.CheckResult check =
                    SubjectRequirements.check(unit.reqExpr(), unit.reqName(), combo);
            if (!check.eligible()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("index", i + 1);
                err.put("schoolCode", item.schoolCode());
                err.put("requirement", check.description());
                err.put("missing", check.missing());
                err.put("message", "选科不符：要求 " + check.description()
                        + "，缺少 " + String.join("、", check.missing()));
                errors.add(err);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", errors.isEmpty());
        data.put("errors", errors);
        data.put("checked", req.items().size());
        data.put("dataVersion", repo.latestDataVersion());
        return Result.ok(data);
    }

    private Models.RulePolicy requirePolicy(String provinceCode, int year) {
        return repo.policy(provinceCode, year)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_READY,
                        "该省 / 年份的志愿规则尚未发布: " + provinceCode + " / " + year));
    }

    private static String key(String school, String group, String major) {
        return school + "|" + group + "|" + major;
    }
}
