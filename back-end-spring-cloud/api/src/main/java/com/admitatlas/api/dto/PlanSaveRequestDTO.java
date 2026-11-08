package com.admitatlas.api.dto;

import java.util.List;

/** 志愿方案保存请求。志愿结构由服务端的 rule_policy 校验，不信任前端声明的数量。 */
public record PlanSaveRequestDTO(
        String planName,
        String taskId,
        String provinceCode,
        Integer examYear,
        String category,
        List<String> subjectCombo,
        List<PlanItemDTO> items
) {
    public record PlanItemDTO(
            int positionNo,
            String schoolCode,
            String schoolName,
            String groupOrMajorCode,
            String unitName,
            String tier,
            Integer matchScore,
            Integer historyMinRank,
            String reason
    ) {
    }
}
