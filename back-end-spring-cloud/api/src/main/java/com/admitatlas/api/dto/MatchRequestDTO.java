package com.admitatlas.api.dto;

import java.util.Map;

/**
 * 智能匹配请求。rank 由 score 服务换算后回填，不允许前端直接传位次。
 */
public record MatchRequestDTO(
        ScoreInputDTO score,
        ScoreInputDTO.Preference preference,
        Integer rank,
        Map<String, Object> options
) {
}
