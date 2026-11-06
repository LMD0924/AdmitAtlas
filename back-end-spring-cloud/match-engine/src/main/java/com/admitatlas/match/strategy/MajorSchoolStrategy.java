package com.admitatlas.match.strategy;

import org.springframework.stereotype.Component;

/** 浙江 / 山东 / 河北 / 重庆等：志愿单位为「专业(类) + 院校」，1 个专业即 1 个志愿。 */
@Component
public class MajorSchoolStrategy implements MatchStrategy {

    @Override
    public String examMode() {
        return "GROUP_3_3";
    }

    @Override
    public String volunteerUnit() {
        return "MAJOR_SCHOOL";
    }

    @Override
    public int defaultVolunteerCount() {
        return 96;
    }

    @Override
    public boolean requiresSubjectCheck() {
        return true;
    }
}
