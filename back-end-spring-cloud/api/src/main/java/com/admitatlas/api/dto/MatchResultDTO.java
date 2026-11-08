package com.admitatlas.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 匹配结果。tier 取值：CHONG(冲) / WEN(稳) / BAO(保)。
 *
 * <p>对外只暴露「匹配度(0-100)」与「风险等级」，禁止输出「录取率 / 保证录取」
 * 等承诺性表述；historyMinRank 是近三年加权后的最低录取位次，不是分数。
 */
public record MatchResultDTO(
        String taskId,
        String status,
        String provinceCode,
        int examYear,
        Integer rank,
        Boolean rankEstimated,
        String rankMessage,
        String dataVersion,
        Boolean demoData,
        String examMode,
        String volunteerUnit,
        int volunteerCount,
        int recalledCount,
        int filteredCount,
        Map<String, Integer> tierCounts,
        List<String> warnings,
        String disclaimer,
        List<SuggestionItem> items
) {
    public record SuggestionItem(
            String schoolCode,
            String schoolName,
            String groupOrMajorCode,
            String unitName,
            String tier,
            int matchScore,
            Integer historyMinRank,
            List<Integer> historyRanks,
            Integer rankDelta,
            Double relativeDelta,
            String subjectRequirement,
            Integer planCount,
            String sourceUrl,
            String reason
    ) {
    }
}
