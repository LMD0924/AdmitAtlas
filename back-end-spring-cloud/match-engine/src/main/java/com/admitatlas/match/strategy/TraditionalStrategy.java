package com.admitatlas.match.strategy;

import org.springframework.stereotype.Component;

/** 传统高考：文理分科，志愿单位为「院校 + 专业」。 */
@Component
public class TraditionalStrategy implements MatchStrategy {

    @Override
    public String examMode() {
        return "TRADITIONAL";
    }

    @Override
    public String volunteerUnit() {
        return "SCHOOL_MAJOR";
    }

    @Override
    public int defaultVolunteerCount() {
        return 6;
    }

    @Override
    public boolean requiresSubjectCheck() {
        return false;
    }
}
