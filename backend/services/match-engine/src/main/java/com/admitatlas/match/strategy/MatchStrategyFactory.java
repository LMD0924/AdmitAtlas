package com.admitatlas.match.strategy;

import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 策略工厂。P1 起改为按 (provinceCode, examYear) 查询 rule_policy 后再选择策略，
 * 避免同一 examMode 下不同省份的结构差异被抹平。
 */
@Component
public class MatchStrategyFactory {

    private final Map<String, MatchStrategy> strategies;

    public MatchStrategyFactory(List<MatchStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(MatchStrategy::examMode, Function.identity()));
    }

    public MatchStrategy of(String examMode) {
        MatchStrategy strategy = strategies.get(examMode);
        if (strategy == null) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "不支持的考试模式: " + examMode + "，可用值 " + strategies.keySet());
        }
        return strategy;
    }

    public List<String> supportedModes() {
        return List.copyOf(strategies.keySet());
    }
}
