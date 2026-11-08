package com.admitatlas.authuser;

import com.admitatlas.api.dto.ScoreInputDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
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
 * 成绩档案。P2 起写入方为当前登录用户（AuthContext），保存成绩时服务端自动换算位次，
 * 前端不得指定 rank。游客访问由 AuthInterceptor 在 /api/v1/student/** 处拦截为 401。
 */
@RestController
@RequestMapping("/api/v1")
public class StudentController {

    private final AdmitAtlasRepository repo;
    private final AuthService authService;

    public StudentController(AdmitAtlasRepository repo, AuthService authService) {
        this.repo = repo;
        this.authService = authService;
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

        long id = repo.saveScore(AuthContext.userId(), score.provinceCode(), score.examYear(),
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

    /** 读取当前用户最近一份成绩档案（含换算后的位次）。 */
    @GetMapping("/student/scores")
    public Result<Map<String, Object>> latestScore(
            @RequestParam(defaultValue = "2026") int examYear) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", repo.latestScore(AuthContext.userId(), examYear).orElse(null));
        data.put("dataVersion", repo.latestDataVersion());
        return Result.ok(data);
    }

    /** 兼容 P1 前端的 /users/me：现在返回真实身份，游客返回 guest。 */
    @GetMapping("/users/me")
    public Result<Map<String, Object>> me() {
        var user = authService.current();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.id());
        data.put("username", user.username());
        data.put("displayName", user.displayName());
        data.put("provinceCode", user.provinceCode());
        data.put("role", user.role());
        data.put("admin", user.admin());
        data.put("guest", user.guest());
        data.put("loginRequired", false);
        data.put("note", user.guest()
                ? "游客可浏览公开数据；录入成绩、智能匹配与保存方案需登录学生账号"
                : "已登录；成绩与方案按账号隔离");
        return Result.ok(data);
    }
}
