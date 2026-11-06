package com.admitatlas.api.dto;

import java.util.List;
import java.util.Map;

/**
 * 学生成绩录入。examMode 取值：TRADITIONAL / GROUP_3_3 / GROUP_3_1_2。
 * subjectScores 用于同分排序与专业单科门槛校验，不参与跨年分数比较。
 */
public record ScoreInputDTO(
        String provinceCode,
        int examYear,
        String examMode,
        String category,
        List<String> subjectCombo,
        int totalScore,
        Map<String, Integer> subjectScores
) {
    public record Preference(
            List<String> regions,
            List<String> majorKeywords,
            List<String> schoolTiers,
            String priority
    ) {
    }
}
