package com.admitatlas.api.dto;

import java.util.List;

/**
 * 匹配结果。tier 取值：CHONG(冲) / WEN(稳) / BAO(保)。
 * 对外只暴露匹配度与风险等级，禁止输出「录取率」等承诺性表述。
 */
public record MatchResultDTO(
        String taskId,
        String status,
        String provinceCode,
        int examYear,
        Integer rank,
        List<SuggestionItem> items
) {
    public record SuggestionItem(
            String schoolCode,
            String schoolName,
            String groupOrMajorCode,
            String tier,
            int matchScore,
            Integer historyMinRank,
            String reason
    ) {
    }
}
