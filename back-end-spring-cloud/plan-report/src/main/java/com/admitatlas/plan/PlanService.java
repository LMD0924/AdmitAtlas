package com.admitatlas.plan;

import com.admitatlas.api.dto.PlanSaveRequestDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Disclaimer;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.SubjectRequirements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 志愿方案：保存前逐条校验（数量 / 序号 / 重复 / 选科资格），
 * 并计算冲稳保比例；导出文件强制携带数据版本、生成时间与免责声明。
 */
@Service
public class PlanService {

    private static final long DEMO_USER_ID = 1L;
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdmitAtlasRepository repo;

    @Value("${admitatlas.demo-data:true}")
    private boolean demoData;

    public PlanService(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    public Map<String, Object> validate(PlanSaveRequestDTO req) {
        String province = req.provinceCode() == null ? "42" : req.provinceCode();
        int year = req.examYear() == null ? 2026 : req.examYear();
        String category = req.category() == null || req.category().isBlank()
                ? "物理类" : req.category();
        List<PlanSaveRequestDTO.PlanItemDTO> items =
                req.items() == null ? List.of() : req.items();

        Models.RulePolicy rule = repo.policy(province, year)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_READY,
                        "该省 / 年份的志愿规则尚未发布，无法校验志愿结构"));
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();

        if (items.isEmpty()) {
            errors.add("方案至少包含 1 条志愿");
        }
        if (items.size() > rule.volunteerCount()) {
            errors.add("本省 " + year + " 年最多可填 " + rule.volunteerCount()
                    + " 个志愿，当前 " + items.size() + " 个");
        }

        Set<Integer> positions = new LinkedHashSet<>();
        Set<String> unitKeys = new LinkedHashSet<>();
        for (int i = 0; i < items.size(); i++) {
            PlanSaveRequestDTO.PlanItemDTO item = items.get(i);
            if (!positions.add(item.positionNo())) {
                errors.add("志愿序号重复: " + item.positionNo());
            }
            String unitKey = item.schoolCode() + "|" + item.groupOrMajorCode();
            if (!unitKeys.add(unitKey)) {
                errors.add("第 " + (i + 1) + " 条与前面重复: " + item.schoolCode()
                        + " / " + item.groupOrMajorCode());
            }
            if (!List.of("CHONG", "WEN", "BAO").contains(item.tier())) {
                errors.add("第 " + (i + 1) + " 条档位不合法: " + item.tier());
            }
        }
        for (int expected = 1; expected <= items.size(); expected++) {
            if (!positions.contains(expected)) {
                errors.add("志愿序号必须从 1 连续，缺少序号 " + expected);
                break;
            }
        }

        // 逐条选科资格校验：提示缺少哪个科目要求
        List<Map<String, Object>> eligibility = new ArrayList<>();
        List<Models.PlanUnit> units = repo.planUnits(province, year, category);
        Map<String, Models.PlanUnit> byKey = new LinkedHashMap<>();
        for (Models.PlanUnit u : units) {
            byKey.put(u.schoolCode() + "|" + u.groupCode() + "|" + u.majorCode(), u);
        }
        List<String> combo = req.subjectCombo() == null ? List.of() : req.subjectCombo();
        for (int i = 0; i < items.size(); i++) {
            PlanSaveRequestDTO.PlanItemDTO item = items.get(i);
            Models.PlanUnit unit = byKey.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(item.schoolCode() + "|"
                            + item.groupOrMajorCode() + "|"))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
            if (unit == null) {
                warnings.add("第 " + (i + 1) + " 条未找到当年招生计划，无法确认选科要求");
                continue;
            }
            SubjectRequirements.CheckResult check = SubjectRequirements.check(
                    unit.reqExpr(), unit.reqName(), combo);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i + 1);
            row.put("eligible", check.eligible());
            row.put("requirement", check.description());
            row.put("missing", check.missing());
            eligibility.add(row);
            if (!check.eligible()) {
                errors.add("第 " + (i + 1) + " 条选科不符：要求 " + check.description()
                        + "，缺少 " + String.join("、", check.missing()));
            }
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("CHONG", 0);
        counts.put("WEN", 0);
        counts.put("BAO", 0);
        for (PlanSaveRequestDTO.PlanItemDTO item : items) {
            counts.merge(item.tier(), 1, Integer::sum);
        }
        Map<String, Object> distribution = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double ratio = items.isEmpty() ? 0
                    : Math.round(e.getValue() * 1000.0 / items.size()) / 10.0;
            distribution.put(e.getKey(), Map.of("count", e.getValue(), "ratio", ratio));
        }
        if (!items.isEmpty() && counts.get("CHONG") * 100.0 / items.size() > 40) {
            warnings.add("「冲」占比超过 40%，梯度偏激进，建议补充「稳 / 保」志愿");
        }
        if (!items.isEmpty() && counts.get("BAO") == 0) {
            warnings.add("方案中没有「保」档志愿，缺少兜底");
        }

        data.put("valid", errors.isEmpty());
        data.put("errors", errors);
        data.put("warnings", warnings);
        data.put("eligibility", eligibility);
        data.put("distribution", distribution);
        data.put("volunteerUnit", rule.volunteerUnit());
        data.put("volunteerCount", rule.volunteerCount());
        data.put("itemCount", items.size());
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        return data;
    }

    public Map<String, Object> save(PlanSaveRequestDTO req) {
        Map<String, Object> check = validate(req);
        if (!Boolean.TRUE.equals(check.get("valid"))) {
            return check;   // 不落库，前端逐条展示 errors
        }
        String province = req.provinceCode() == null ? "42" : req.provinceCode();
        int year = req.examYear() == null ? 2026 : req.examYear();
        String dataVersion = String.valueOf(check.get("dataVersion"));
        Models.PlanRecord plan = new Models.PlanRecord(0L, DEMO_USER_ID, req.taskId(),
                req.planName() == null || req.planName().isBlank()
                        ? "我的志愿方案" : req.planName(),
                String.valueOf(check.get("volunteerUnit")),
                ((Number) check.get("volunteerCount")).intValue(),
                dataVersion, null);
        List<Models.PlanItemRecord> rows = new ArrayList<>();
        for (PlanSaveRequestDTO.PlanItemDTO item : req.items()) {
            rows.add(new Models.PlanItemRecord(0L, 0L, item.positionNo(),
                    item.schoolCode(), item.schoolName(), item.groupOrMajorCode(),
                    item.unitName(), item.tier(),
                    item.matchScore() == null ? 0 : item.matchScore(),
                    item.historyMinRank(), item.reason()));
        }
        long planId = repo.savePlan(plan, rows);
        check.put("planId", planId);
        check.put("saved", true);
        return check;
    }

    /** F-C-43 / F-S-10：CSV（Excel 可直接打开），强制包含数据版本与免责声明。 */
    public byte[] exportCsv(long planId) {
        Models.PlanRecord plan = repo.plan(planId)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "未找到方案: " + planId));
        List<Models.PlanItemRecord> items = repo.planItems(planId);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');   // UTF-8 BOM，避免 Excel 中文乱码
        sb.append("AdmitAtlas 志愿方案导出\n");
        sb.append(csvRow("方案名称", plan.planName(), "志愿单位", plan.volunteerUnit(),
                "志愿数上限", String.valueOf(plan.volunteerCount()))).append('\n');
        sb.append(csvRow("数据版本", plan.dataVersion(), "生成时间",
                LocalDateTime.now().format(TS),
                "数据性质", demoData ? "演示数据（合成值）" : "已发布数据")).append('\n');
        sb.append('\n');
        sb.append("序号,院校代码,院校名称,志愿单位代码,专业组/专业,档位,匹配度,历史最低位次,推荐依据\n");
        for (Models.PlanItemRecord item : items) {
            sb.append(csvRow(
                    String.valueOf(item.positionNo()), item.schoolCode(), item.schoolName(),
                    item.groupOrMajorCode(), item.unitName(), tierText(item.tier()),
                    String.valueOf(item.matchScore()),
                    item.historyMinRank() == null ? "" : String.valueOf(item.historyMinRank()),
                    item.reason())).append('\n');
        }
        sb.append('\n');
        sb.append(csvRow("免责声明", Disclaimer.TEXT)).append('\n');
        sb.append(csvRow("数据来源说明", "按最低位次(min_rank)跨年比较；最低分不可跨年直接比较；"
                + "最终以各省教育考试院公布数据为准")).append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvRow(String... cells) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            String value = cells[i] == null ? "" : cells[i];
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                row.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                row.append(value);
            }
        }
        return row.toString();
    }

    private static String tierText(String tier) {
        return switch (tier == null ? "" : tier) {
            case "CHONG" -> "冲";
            case "WEN" -> "稳";
            case "BAO" -> "保";
            default -> tier == null ? "" : tier;
        };
    }
}
