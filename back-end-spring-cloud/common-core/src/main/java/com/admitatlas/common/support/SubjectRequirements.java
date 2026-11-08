package com.admitatlas.common.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Parse the configurable subject requirement expression, e.g.
 * "物理", "物理+化学", "不限". Every "+"-separated subject must be present
 * in the student's combination.
 */
public final class SubjectRequirements {

    private SubjectRequirements() {
    }

    public record CheckResult(boolean eligible, List<String> required,
                              List<String> missing, String description) {
    }

    public static CheckResult check(String reqExpr, String reqName, List<String> combo) {
        List<String> mine = combo == null ? Collections.emptyList() : combo;
        String expr = reqExpr == null ? "" : reqExpr.trim();
        if (expr.isEmpty() || "不限".equals(expr) || "NONE".equalsIgnoreCase(expr)) {
            return new CheckResult(true, Collections.emptyList(), Collections.emptyList(),
                    reqName == null ? "不限" : reqName);
        }
        List<String> required = Arrays.stream(expr.split("[+＋,，、/]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        List<String> missing = new ArrayList<>();
        for (String subject : required) {
            if (!mine.contains(subject)) {
                missing.add(subject);
            }
        }
        String label = reqName == null ? expr : reqName + "（" + expr + "）";
        return new CheckResult(missing.isEmpty(), required, missing, label);
    }
}
