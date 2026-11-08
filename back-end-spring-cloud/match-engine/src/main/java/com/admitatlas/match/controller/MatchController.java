package com.admitatlas.match.controller;

import com.admitatlas.api.dto.MatchRequestDTO;
import com.admitatlas.api.dto.MatchResultDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.match.MatchService;
import com.admitatlas.match.strategy.MatchStrategyFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** P1：真实匹配链路入口。 */
@RestController
@RequestMapping("/api/v1/match")
public class MatchController {

    private final MatchStrategyFactory strategyFactory;
    private final MatchService matchService;
    private final AdmitAtlasRepository repo;

    public MatchController(MatchStrategyFactory strategyFactory,
                           MatchService matchService,
                           AdmitAtlasRepository repo) {
        this.strategyFactory = strategyFactory;
        this.matchService = matchService;
        this.repo = repo;
    }

    @GetMapping("/strategies")
    public Result<Map<String, Object>> strategies() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("supportedModes", strategyFactory.supportedModes());
        meta.put("stage", "P1-pilot");
        meta.put("dataVersion", repo.latestDataVersion());
        meta.put("policyCount", repo.policies().size());
        meta.put("note", "志愿单位与志愿数量由 rule_policy(省×年) 决定，策略工厂只负责校验考试模式");
        return Result.ok(meta);
    }

    /** F-C-25 / F-C-28 / F-C-29 / F-C-30：位次换算 → 召回 → 过滤 → 打分 → 冲稳保。 */
    @PostMapping("/analyze")
    public Result<MatchResultDTO> analyze(@RequestBody(required = false) MatchRequestDTO request) {
        if (request == null || request.score() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "score 不能为空");
        }
        if (request.score().provinceCode() == null || request.score().provinceCode().isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "provinceCode 不能为空");
        }
        return Result.ok(matchService.analyze(request));
    }

    /** F-C-34：匹配任务查询（P1 为同步执行，任务落库后直接处于 DONE）。 */
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> task(@PathVariable String taskId) {
        Models.TaskRecord task = repo.task(taskId)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "未找到匹配任务: " + taskId));
        var user = AuthContext.require();
        if (task.userId() != user.id() && !user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看其他账号的匹配任务");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.taskId());
        data.put("status", task.status());
        data.put("provinceCode", task.provinceCode());
        data.put("examYear", task.examYear());
        data.put("dataVersion", task.dataVersion());
        data.put("createdAt", task.createdAt());
        data.put("executionMode", "SYNCHRONOUS");
        return Result.ok(data);
    }
}
