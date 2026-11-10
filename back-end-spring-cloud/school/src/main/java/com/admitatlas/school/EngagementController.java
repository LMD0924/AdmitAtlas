package com.admitatlas.school;

import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F-C-18 / F-C-19 / F-C-14、数据大屏：院校对比、收藏、专业详情与公开统计聚合。
 *
 * <p>收藏写接口由 AuthInterceptor 的 PROTECTED_PATHS 保护，按 {@link AuthContext#userId()}
 * 隔离；其余端点为游客可读的公开资源。SQL 聚合结果统一经 {@link #rows(List)} 规范化成
 * camelCase，屏蔽 H2 / MySQL 列名大小写差异。
 */
@RestController
@RequestMapping("/api/v1")
public class EngagementController {

    private final AdmitAtlasRepository repo;
    private final boolean demoData;

    public EngagementController(AdmitAtlasRepository repo,
                                @Value("${admitatlas.demo-data:true}") boolean demoData) {
        this.repo = repo;
        this.demoData = demoData;
    }

    // -------------------------------------------------------------- 收藏 F-C-19

    @GetMapping("/favorites")
    public Result<Map<String, Object>> favorites() {
        long userId = AuthContext.userId();
        List<Models.Favorite> items = repo.favorites(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("codes", items.stream().map(Models.Favorite::schoolCode).toList());
        data.put("total", items.size());
        return Result.ok(data);
    }

    @PostMapping("/favorites/{code}")
    public Result<Map<String, Object>> addFavorite(@PathVariable String code) {
        long userId = AuthContext.userId();
        repo.school(code).orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                "未找到院校档案: " + code));
        boolean added = repo.addFavorite(userId, code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favorited", true);
        data.put("created", added);
        data.put("total", repo.countFavorites(userId));
        return Result.ok(data);
    }

    @DeleteMapping("/favorites/{code}")
    public Result<Map<String, Object>> removeFavorite(@PathVariable String code) {
        long userId = AuthContext.userId();
        repo.removeFavorite(userId, code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favorited", false);
        data.put("total", repo.countFavorites(userId));
        return Result.ok(data);
    }

    // -------------------------------------------------------------- 院校对比 F-C-18

    /** 最多 4 所院校并排对比：档案 + 历年投档 + 位次摘要，公开可读。 */
    @GetMapping("/compare/schools")
    public Result<Map<String, Object>> compareSchools(
            @RequestParam String codes,
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "物理类") String category) {
        List<String> list = Arrays.stream(codes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(4).toList();
        if (list.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请至少选择一所院校");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : list) {
            Models.School school = repo.school(code).orElse(null);
            if (school == null) {
                continue;
            }
            List<Models.AdmissionScore> history = new ArrayList<>(
                    repo.schoolAdmissionScores(provinceCode, code, category, 2023, 2026));
            history.sort(Comparator.comparingInt(Models.AdmissionScore::examYear).reversed());

            Map<String, Object> summary = new LinkedHashMap<>();
            Models.AdmissionScore latest = history.isEmpty() ? null : history.get(0);
            summary.put("latestYear", latest == null ? null : latest.examYear());
            summary.put("latestMinScore", latest == null ? null : latest.minScore());
            summary.put("latestMinRank", latest == null ? null : latest.minRank());
            summary.put("latestPlanCount", latest == null ? null : latest.planCount());
            summary.put("bestRank", history.stream().mapToInt(Models.AdmissionScore::minRank)
                    .min().orElse(0));
            summary.put("avgRank", history.isEmpty() ? null
                    : Math.round(history.stream().mapToInt(Models.AdmissionScore::minRank)
                    .average().orElse(0)));
            summary.put("historyCount", history.size());
            summary.put("tierTags", school.tierTags());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("school", school);
            row.put("history", history);
            row.put("summary", summary);
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("count", items.size());
        data.put("provinceCode", provinceCode);
        data.put("category", category);
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        data.put("sourceNote", "演示数据：录取分数与位次为合成值，最终以各省教育考试院公布为准");
        return Result.ok(data);
    }

    // -------------------------------------------------------------- 专业详情 F-C-14

    @GetMapping("/majors/{code}")
    public Result<Map<String, Object>> majorDetail(@PathVariable String code) {
        Models.Major major = repo.major(code)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到专业: " + code));
        List<Map<String, Object>> schools = rows(repo.majorSchools(code));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("major", major);
        data.put("schools", schools);
        data.put("schoolCount", schools.size());
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    // -------------------------------------------------------------- 数据大屏

    /** 公开统计聚合：一分一段分布、层次/地域占比、历年趋势与位次榜。 */
    @GetMapping("/insights")
    public Result<Map<String, Object>> insights(
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "2026") int examYear,
            @RequestParam(defaultValue = "物理类") String category) {
        Models.DataVersion version = repo.dataVersion(demoData);
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("schoolCount", version.schoolCount());
        overview.put("majorCount", version.majorCount());
        overview.put("admissionRowCount", version.admissionRowCount());
        overview.put("segmentRowCount", version.segmentRowCount());
        overview.put("policyCount", version.policyCount());

        // 维度统计只有十几行，直接在应用层按标签拆分，避免依赖字符串函数的方言差异。
        List<Models.School> schools = repo.searchSchools(null, null, null, 1, 200);
        Map<String, Integer> tier = new LinkedHashMap<>();
        Map<String, Integer> city = new LinkedHashMap<>();
        Map<String, Integer> type = new LinkedHashMap<>();
        for (Models.School s : schools) {
            for (String t : String.valueOf(s.tierTags() == null ? "" : s.tierTags()).split(",")) {
                if (!t.isBlank()) tier.merge(t.trim(), 1, Integer::sum);
            }
            city.merge(s.city() == null ? "未知" : s.city(), 1, Integer::sum);
            type.merge(s.schoolType() == null ? "未知" : s.schoolType(), 1, Integer::sum);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provinceCode", provinceCode);
        data.put("examYear", examYear);
        data.put("category", category);
        data.put("overview", overview);
        data.put("scoreDistribution", rows(repo.scoreBuckets(provinceCode, examYear, category)));
        data.put("tierDistribution", distribution(tier));
        data.put("cityDistribution", distribution(city));
        data.put("typeDistribution", distribution(type));
        data.put("scoreTrend", rows(repo.admissionTrend(provinceCode, category)));
        // 一分一段可能已发布到 examYear，但投档记录尚未录入；位次榜自动落到最新有数据的年份，
        // 并把实际年份回传，避免大屏出现「有效数据却空图」的假象。
        int rankYear = repo.latestAdmissionYear(provinceCode, category);
        data.put("rankYear", rankYear);
        data.put("topSchools", rows(repo.topSchools(provinceCode, rankYear, category, 10)));
        data.put("dataVersion", version.dataVersion());
        data.put("demoData", demoData);
        data.put("sourceNote", version.sourceNote());
        return Result.ok(data);
    }

    // -------------------------------------------------------------- helpers

    private static List<Map<String, Object>> distribution(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                }).toList();
    }

    /** SQL 列名（H2 可能返回大写）统一转 camelCase，数值原样保留。 */
    private static List<Map<String, Object>> rows(List<Map<String, Object>> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                normalized.put(toCamel(e.getKey()), e.getValue());
            }
            out.add(normalized);
        }
        return out;
    }

    private static String toCamel(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i += 1) {
            if (parts[i].isEmpty()) continue;
            sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
