package com.admitatlas.match;

import com.admitatlas.api.dto.MatchRequestDTO;
import com.admitatlas.api.dto.MatchResultDTO;
import com.admitatlas.api.dto.ScoreInputDTO;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.Ids;
import com.admitatlas.common.support.Json;
import com.admitatlas.common.support.RankCalculator;
import com.admitatlas.common.support.SubjectRequirements;
import com.admitatlas.match.strategy.MatchStrategy;
import com.admitatlas.match.strategy.MatchStrategyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * P1 真实匹配链路：
 * 分数换算位次 → 批次线过滤 → 候选位次区间召回(SQL 索引) → 选科资格过滤
 * → 近三年加权位次差 → 冲稳保分档 → 匹配度打分 → 偏好过滤 → 推荐依据。
 *
 * <p>跨年只比 min_rank；输出只有「匹配度 / 风险等级」，绝不输出录取率。
 * 志愿单位与数量来自 rule_policy，不从代码常量取。
 */
@Service
public class MatchService {

    public static final String DISCLAIMER = com.admitatlas.common.Disclaimer.TEXT;

    /** 与 auth-user 的演示账号一致；P1 未接登录，接入 JWT 后从安全上下文取。 */
    private static final long DEMO_USER_ID = 1L;

    private final AdmitAtlasRepository repo;
    private final MatchStrategyFactory factory;

    @Value("${admitatlas.match.history-years:3}")
    private int historyYears;
    @Value("${admitatlas.match.rank-window-low:0.55}")
    private double rankWindowLow;
    @Value("${admitatlas.match.rank-window-high:1.9}")
    private double rankWindowHigh;
    @Value("${admitatlas.match.chong-boundary:-0.10}")
    private double chongBoundary;
    @Value("${admitatlas.match.bao-boundary:0.15}")
    private double baoBoundary;
    @Value("${admitatlas.match.max-candidates:60}")
    private int maxCandidates;
    @Value("${admitatlas.demo-data:true}")
    private boolean demoData;

    public MatchService(AdmitAtlasRepository repo, MatchStrategyFactory factory) {
        this.repo = repo;
        this.factory = factory;
    }

    public MatchResultDTO analyze(MatchRequestDTO request) {
        ScoreInputDTO score = request.score();
        String province = score.provinceCode();
        int year = score.examYear();
        String category = blankTo(score.category(), "物理类");
        String examMode = score.examMode();
        List<String> combo = score.subjectCombo() == null ? List.of() : score.subjectCombo();
        List<String> warnings = new ArrayList<>();
        String dataVersion = repo.latestDataVersion();

        // 1) 规则驱动：志愿单位 / 数量来自 rule_policy；策略工厂负责校验考试模式合法性
        MatchStrategy strategy = factory.of(examMode);
        Optional<Models.RulePolicy> policyOpt = repo.policy(province, year);
        String volunteerUnit = policyOpt.map(Models.RulePolicy::volunteerUnit)
                .orElse(strategy.volunteerUnit());
        int volunteerCount = policyOpt.map(Models.RulePolicy::volunteerCount)
                .orElse(strategy.defaultVolunteerCount());
        if (policyOpt.isEmpty()) {
            warnings.add("该省 / 年份的规则配置未发布，志愿结构暂时采用策略默认值");
        }

        // 2) 分数 → 位次（服务端换算，忽略前端传入的 rank）
        List<Models.Segment> segments = repo.segments(province, year, category);
        Models.RankResult rankResult =
                RankCalculator.convert(segments, score.totalScore(), dataVersion);
        int rank = rankResult.rank();
        if (rankResult.estimated()) {
            warnings.add(rankResult.message());
        }

        // 3) 批次线过滤：只保留考生成绩达线的批次
        List<Models.BatchLine> lines = repo.batchLines(province, year, category);
        List<String> batches = lines.stream()
                .filter(l -> l.lineScore() <= score.totalScore())
                .map(Models.BatchLine::batch)
                .distinct()
                .toList();
        if (batches.isEmpty()) {
            batches = List.of("本科批");
            warnings.add("未检索到该省 / 年份的批次线，或成绩未达到任何批次线；结果仅供参考");
        }

        // 4) 候选召回：位次窗口下推到 SQL，命中 idx_query 索引
        int minRank = Math.max(1, (int) Math.floor(rank * rankWindowLow));
        int maxRank = (int) Math.ceil(rank * rankWindowHigh);
        List<Integer> years = IntStream.rangeClosed(1, historyYears)
                .map(i -> year - i).boxed().toList();
        int recallLimit = Math.max(200, maxCandidates * 8);
        List<Models.AdmissionScore> recalled = new ArrayList<>();
        for (String batch : batches) {
            recalled.addAll(repo.candidateScores(province, category, batch, years,
                    minRank, maxRank, dataVersion, recallLimit));
        }

        // 5) 选科资格过滤依据：当年招生计划 + 选科要求表达式
        Map<String, Models.PlanUnit> units = new LinkedHashMap<>();
        for (Models.PlanUnit u : repo.planUnits(province, year, category)) {
            units.put(key(u.schoolCode(), u.groupCode(), u.majorCode()), u);
        }

        Map<String, List<Models.AdmissionScore>> grouped = recalled.stream()
                .collect(Collectors.groupingBy(s ->
                        key(s.schoolCode(), s.groupCode(), s.majorCode()), LinkedHashMap::new,
                        Collectors.toList()));

        List<Candidate> candidates = new ArrayList<>();
        int filteredCount = 0;
        for (Map.Entry<String, List<Models.AdmissionScore>> entry : grouped.entrySet()) {
            List<Models.AdmissionScore> rows = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(Models.AdmissionScore::examYear))
                    .toList();
            Models.AdmissionScore latest = rows.get(rows.size() - 1);
            Models.PlanUnit unit = units.get(entry.getKey());

            String requirementLabel = "不限";
            if (unit != null) {
                SubjectRequirements.CheckResult check = SubjectRequirements.check(
                        unit.reqExpr(), unit.reqName(), combo);
                if (!check.eligible()) {
                    filteredCount++;
                    continue;   // 选科不符，不进入候选（F-E-04 / F-C-38）
                }
                requirementLabel = check.description();
            }

            List<Integer> historyRanks = rows.stream()
                    .map(Models.AdmissionScore::minRank).toList();
            double weighted = weightedRank(rows);
            int historyMinRank = (int) Math.round(weighted);
            int delta = historyMinRank - rank;
            double rel = (double) delta / Math.max(1, rank);
            String tier = classify(rel);
            double volatility = (max(historyRanks) - min(historyRanks))
                    / Math.max(1.0, weighted);

            int planCount = latest.planCount() == null ? 0 : latest.planCount();
            double raw = 100 - Math.abs(rel - 0.05) * 120 - volatility * 18
                    + Math.min(5.0, planCount / 40.0);
            int matchScore = (int) Math.max(35, Math.min(98, Math.round(raw)));

            String reason = "近三年最低位次 " + historyRanks.stream()
                    .map(String::valueOf).collect(Collectors.joining(" / "))
                    + "，加权 " + historyMinRank + "；与你位次 " + rank + " 相差 "
                    + (delta >= 0 ? "+" : "") + delta + " 名（相对 "
                    + String.format("%.1f%%", rel * 100) + "）；选科要求 "
                    + requirementLabel + " 符合；" + planCount + " 个计划";

            candidates.add(new Candidate(new MatchResultDTO.SuggestionItem(
                    latest.schoolCode(), latest.schoolName(), latest.groupCode(),
                    latest.unitName(), tier, matchScore, historyMinRank, historyRanks,
                    delta, Math.round(rel * 1000.0) / 1000.0, requirementLabel,
                    planCount, latest.sourceUrl(), reason),
                    latest.schoolCode(), latest.unitName()));
        }

        // 6) 偏好过滤
        ScoreInputDTO.Preference pref = request.preference();
        if (pref != null) {
            Map<String, Models.School> schoolMap = new LinkedHashMap<>();
            for (Models.School s : repo.searchSchools(null, null, null, 1, 200)) {
                schoolMap.put(s.schoolCode(), s);
            }
            List<String> regions = nullToEmpty(pref.regions());
            List<String> keywords = nullToEmpty(pref.majorKeywords());
            List<String> tiers = nullToEmpty(pref.schoolTiers());
            List<Candidate> before = new ArrayList<>(candidates);
            candidates.removeIf(c -> {
                Models.School school = schoolMap.get(c.schoolCode);
                if (!regions.isEmpty()) {
                    String home = school == null ? "" : nullToText(school.provinceCode());
                    if (regions.stream().noneMatch(r -> home.equals(r))) {
                        return true;
                    }
                }
                if (!keywords.isEmpty()) {
                    String unit = nullToText(c.unitName);
                    if (keywords.stream().noneMatch(unit::contains)) {
                        return true;
                    }
                }
                if (!tiers.isEmpty()) {
                    String tags = school == null ? "" : nullToText(school.tierTags());
                    if (tiers.stream().noneMatch(tags::contains)) {
                        return true;
                    }
                }
                return false;
            });
            filteredCount += before.size() - candidates.size();
        }

        // 7) 排序与限量；SCHOOL_FIRST 时给高层次院校轻微加权
        boolean schoolFirst = pref != null && "SCHOOL_FIRST".equals(pref.priority());
        Map<String, Models.School> tiersMap = schoolFirst
                ? repo.searchSchools(null, null, null, 1, 200).stream()
                .collect(Collectors.toMap(Models.School::schoolCode, s -> s, (a, b) -> a))
                : Map.of();
        candidates.sort(Comparator.comparingInt((Candidate c) -> {
            int bonus = 0;
            if (schoolFirst) {
                Models.School s = tiersMap.get(c.item.schoolCode());
                if (s != null && nullToText(s.tierTags()).contains("985")) {
                    bonus = 4;
                }
            }
            return c.item.matchScore() + bonus;
        }).reversed());

        int limit = maxCandidates;
        Object override = request.options() == null ? null : request.options().get("maxResults");
        if (override instanceof Number n) {
            limit = Math.min(200, Math.max(1, n.intValue()));
        }
        List<MatchResultDTO.SuggestionItem> items = candidates.stream()
                .limit(limit).map(c -> c.item).toList();

        Map<String, Integer> tierCounts = new LinkedHashMap<>();
        tierCounts.put("CHONG", 0);
        tierCounts.put("WEN", 0);
        tierCounts.put("BAO", 0);
        for (MatchResultDTO.SuggestionItem item : items) {
            tierCounts.merge(item.tier(), 1, Integer::sum);
        }
        if (items.isEmpty()) {
            warnings.add("当前位次窗口内没有符合选科与偏好条件的候选，可放宽偏好或调整成绩");
        }
        if (demoData) {
            warnings.add("当前使用演示数据集，所有录取数值均为合成值，不是真实录取数据");
        }

        String taskId = Ids.taskId();
        repo.saveTask(taskId, DEMO_USER_ID,
                province, year, "DONE", Json.write(Map.of(
                        "provinceCode", province, "examYear", year, "category", category,
                        "totalScore", score.totalScore(), "examMode", examMode)), dataVersion);

        return new MatchResultDTO(taskId, "DONE", province, year, rank,
                rankResult.estimated(), rankResult.message(), dataVersion, demoData,
                examMode, volunteerUnit, volunteerCount, recalled.size(), filteredCount,
                tierCounts, warnings, DISCLAIMER, items);
    }

    private String classify(double rel) {
        if (rel <= chongBoundary) {
            return "CHONG";
        }
        if (rel >= baoBoundary) {
            return "BAO";
        }
        return "WEN";
    }

    /** 越近的年份权重越高：3 / 2 / 1。 */
    private double weightedRank(List<Models.AdmissionScore> rows) {
        double sum = 0;
        double weights = 0;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            double w = i + 1;
            sum += rows.get(i).minRank() * w;
            weights += w;
        }
        return weights == 0 ? 0 : sum / weights;
    }

    private static int min(List<Integer> xs) {
        return xs.stream().mapToInt(Integer::intValue).min().orElse(0);
    }

    private static int max(List<Integer> xs) {
        return xs.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static String key(String school, String group, String major) {
        return nullToText(school) + "|" + nullToText(group) + "|" + nullToText(major);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToText(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> nullToEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record Candidate(MatchResultDTO.SuggestionItem item,
                             String schoolCode, String unitName) {
    }
}
