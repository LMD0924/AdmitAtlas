package com.admitatlas.match.strategy;

import org.springframework.stereotype.Component;

/** 3+1+2 / 部分 3+3 省份：志愿单位为「院校专业组」，组内再填若干专业。 */
@Component
public class SchoolGroupStrategy implements MatchStrategy {

    @Override
    public String examMode() {
        return "GROUP_3_1_2";
    }

    @Override
    public String volunteerUnit() {
        return "SCHOOL_GROUP";
    }

    @Override
    public int defaultVolunteerCount() {
        return 45;
    }

    @Override
    public boolean requiresSubjectCheck() {
        return true;
    }
}
