package com.admitatlas.match.strategy;

/**
 * 志愿填报策略。差异不在条件判断，而在「志愿单位 + 志愿数量 + 投档结构」。
 * 实现类按 examMode 注册，由 MatchStrategyFactory 按 省份 + 年份 选择。
 */
public interface MatchStrategy {

    /** 考试模式：TRADITIONAL / GROUP_3_3 / GROUP_3_1_2 */
    String examMode();

    /** 志愿单位：SCHOOL_MAJOR / SCHOOL_GROUP / MAJOR_SCHOOL */
    String volunteerUnit();

    /** 该模式下常见志愿数量，仅作默认值，实际以 rule_policy 配置为准 */
    int defaultVolunteerCount();

    /** 该策略是否需要按专业组展开选科校验 */
    boolean requiresSubjectCheck();
}
