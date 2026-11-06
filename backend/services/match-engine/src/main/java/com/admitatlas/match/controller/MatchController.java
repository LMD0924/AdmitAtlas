package com.admitatlas.match.controller;

import com.admitatlas.api.dto.MatchRequestDTO;
import com.admitatlas.api.dto.MatchResultDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.match.strategy.MatchStrategy;
import com.admitatlas.match.strategy.MatchStrategyFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P0 骨架：验证参数契约与策略选择链路；匹配算法（位次换算、过滤、打分、分档）在 P1 实现。
 */
@RestController
@RequestMapping("/api/v1/match")
public class MatchController {

    private final MatchStrategyFactory strategyFactory;

    public MatchController(MatchStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    @GetMapping("/strategies")
    public Result<Map<String, Object>> strategies() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("supportedModes", strategyFactory.supportedModes());
        meta.put("stage", "P0-skeleton");
        return Result.ok(meta);
    }

    @PostMapping("/analyze")
    public Result<MatchResultDTO> analyze(@RequestBody(required = false) MatchRequestDTO request) {
        if (request == null || request.score() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "score 不能为空");
        }
        MatchStrategy strategy = strategyFactory.of(request.score().examMode());
        Map<String, Object> hint = Map.of(
                "examMode", strategy.examMode(),
                "volunteerUnit", strategy.volunteerUnit(),
                "defaultVolunteerCount", strategy.defaultVolunteerCount(),
                "requiresSubjectCheck", strategy.requiresSubjectCheck());
        return Result.fail(ResultCode.NOT_IMPLEMENTED,
                "匹配算法待 P1 实现；策略解析成功: " + hint);
    }
}
