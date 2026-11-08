package com.admitatlas.score;

import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.RankCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分数 / 位次 / 一分一段 / 批次线 / 历年投档线。
 * 位次是跨年比较的唯一口径，min_score 只用于展示。
 */
@RestController
@RequestMapping("/api/v1")
public class ScoreController {

    private final AdmitAtlasRepository repo;
    private final boolean demoData;

    public ScoreController(AdmitAtlasRepository repo,
                           @Value("${admitatlas.demo-data:true}") boolean demoData) {
        this.repo = repo;
        this.demoData = demoData;
    }

    public record RankRequest(String provinceCode, Integer examYear, String category,
                              Integer totalScore) {
    }

    /** F-C-05 / F-E-01：分数 → 省排名（位次），缺精确行时返回插值估算标记。 */
    @PostMapping("/scores/convert-rank")
    public Result<Map<String, Object>> convertRank(@RequestBody(required = false) RankRequest req) {
        if (req == null || req.provinceCode() == null || req.provinceCode().isBlank()
                || req.examYear() == null || req.totalScore() == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "provinceCode / examYear / totalScore 不能为空");
        }
        String category = req.category() == null || req.category().isBlank()
                ? "物理类" : req.category();
        int score = req.totalScore();
        if (score < 0 || score > 750) {
            throw new BizException(ResultCode.BAD_REQUEST, "总分须在 0~750 之间");
        }
        List<Models.Segment> segments =
                repo.segments(req.provinceCode(), req.examYear(), category);
        Models.RankResult rank = RankCalculator.convert(
                segments, score, repo.latestDataVersion());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rank", rank.rank());
        data.put("rankFrom", rank.rankFrom());
        data.put("rankTo", rank.rankTo());
        data.put("estimated", rank.estimated());
        data.put("score", rank.score());
        data.put("segmentCount", rank.segmentCount());
        data.put("message", rank.message());
        data.put("dataVersion", rank.dataVersion());
        data.put("demoData", demoData);
        data.put("category", category);
        return Result.ok(data);
    }

    /** F-C-21：一分一段表，可用 from/to 截取分数区间。 */
    @GetMapping("/segments")
    public Result<Map<String, Object>> segments(
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "2026") int examYear,
            @RequestParam(defaultValue = "物理类") String category,
            @RequestParam(required = false) Integer from,
            @RequestParam(required = false) Integer to) {
        List<Models.Segment> all = repo.segments(provinceCode, examYear, category);
        int low = from == null ? Integer.MIN_VALUE : from;
        int high = to == null ? Integer.MAX_VALUE : to;
        List<Models.Segment> items = all.stream()
                .filter(s -> s.score() >= low && s.score() <= high)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("provinceCode", provinceCode);
        data.put("examYear", examYear);
        data.put("category", category);
        data.put("dataVersion", items.isEmpty() ? repo.latestDataVersion()
                : items.get(0).dataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    /** F-C-20：批次线 / 省控线。 */
    @GetMapping("/batch-lines")
    public Result<Map<String, Object>> batchLines(
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam(defaultValue = "2026") int examYear,
            @RequestParam(defaultValue = "物理类") String category) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", repo.batchLines(provinceCode, examYear, category));
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    /** F-C-12 / F-C-22：院校历年投档线与最低位次（详情页趋势图数据源）。 */
    @GetMapping("/admission/scores")
    public Result<Map<String, Object>> admissionScores(
            @RequestParam(defaultValue = "42") String provinceCode,
            @RequestParam String schoolCode,
            @RequestParam(defaultValue = "物理类") String category,
            @RequestParam(defaultValue = "2023") int fromYear,
            @RequestParam(defaultValue = "2026") int toYear) {
        List<Models.AdmissionScore> items = repo.schoolAdmissionScores(
                provinceCode, schoolCode, category, fromYear, toYear);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("schoolCode", schoolCode);
        data.put("dataVersion", repo.latestDataVersion());
        data.put("demoData", demoData);
        data.put("sourceNote", "按最低位次(min_rank)跨年比较；最低分仅作展示，不可跨年直接比较");
        return Result.ok(data);
    }
}
