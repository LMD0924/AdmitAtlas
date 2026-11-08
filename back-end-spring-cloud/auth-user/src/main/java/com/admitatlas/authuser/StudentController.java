package com.admitatlas.authuser;

import com.admitatlas.api.dto.ScoreInputDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.RankCalculator;
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
 * 成绩档案。P1 未接登录，统一挂到演示账号 id=1；
 * 保存成绩时同步完成位次换算，避免前端把 rank 传进来（契约禁止前端指定位次）。
 */
@RestController
@RequestMapping("/api/v1")
public class StudentController {

    /** 演示账号；接入 JWT 后替换为 SecurityContext 中的用户 id。 */
    public static final long DEMO_USER_ID = 1L;

    private final AdmitAtlasRepository repo;

    public StudentController(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    /** F-C-01 / F-C-04 / F-C-08：保存成绩档案并自动换算位次。 */
    @PostMapping("/student/scores")
    public Result<Map<String, Object>> saveScore(
            @RequestBody(required = false) ScoreInputDTO score) {
        if (score == null || score.provinceCode() == null || score.provinceCode().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "provinceCode 不能为空");
        }
        if (score.totalScore() < 0 || score.totalScore() > 750) {
            throw new BizException(ResultCode.BAD_REQUEST, "总分须在 0~750 之间");
        }
        String category = score.category() == null || score.category().isBlank()
                ? "物理类" : score.category();
        List<String> combo = score.subjectCombo() == null ? List.of() : score.subjectCombo();
        Map<String, Integer> subjectScores =
                score.subjectScores() == null ? Map.of() : score.subjectScores();

        List<Models.Segment> segments =
                repo.segments(score.provinceCode(), score.examYear(), category);
        Models.RankResult rank = RankCalculator.convert(
                segments, score.totalScore(), repo.latestDataVersion());

        long id = repo.saveScore(DEMO_USER_ID, score.provinceCode(), score.examYear(),
                score.examMode(), category, combo, score.totalScore(), subjectScores,
                rank.rank(), rank.estimated());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("rank", rank.rank());
        data.put("rankFrom", rank.rankFrom());
        data.put("rankTo", rank.rankTo());
        data.put("rankEstimated", rank.estimated());
        data.put("message", rank.message());
        data.put("dataVersion", rank.dataVersion());
        data.put("saved", true);
        return Result.ok(data);
    }

    /** 读取最近一份成绩档案（含换算后的位次）。 */
    @GetMapping("/student/scores")
    public Result<Map<String, Object>> latestScore(
            @RequestParam(defaultValue = "2026") int examYear) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", repo.latestScore(DEMO_USER_ID, examYear).orElse(null));
        data.put("dataVersion", repo.latestDataVersion());
        return Result.ok(data);
    }

    /** F-S-01/02 占位：P1 未接登录，返回固定的演示用户。 */
    @GetMapping("/users/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", DEMO_USER_ID);
        data.put("username", "demo");
        data.put("displayName", "访客");
        data.put("provinceCode", "42");
        data.put("role", "STUDENT");
        data.put("loginRequired", false);
        data.put("note", "P1 单省试点未接入登录鉴权，使用演示账号");
        return Result.ok(data);
    }
}
