package com.admitatlas.common.model;

import java.util.List;
import java.util.Map;

/**
 * Shared read models for the modular monolith.
 *
 * <p>All modules talk to the same DataSource, so the row shapes live in one place
 * instead of being duplicated per module. These are plain immutable records — no
 * JPA entities, no lazy loading, no ORM magic.
 */
public final class Models {

    private Models() {
    }

    public record Province(String provinceCode, String provinceName, String examMode,
                           String policyVersion) {
    }

    public record School(String schoolCode, String schoolName, String supervisor,
                         String provinceCode, String city, String tierTags,
                         String schoolType, String schoolLevel, String intro) {
    }

    public record Major(String majorCode, String majorName, String category,
                        String majorClass, Integer degreeYears) {
    }

    public record RulePolicy(String provinceCode, int examYear, String examMode,
                             String volunteerUnit, int volunteerCount, Integer groupMajorCount,
                             String batches, String sortFields, String subjectRules,
                             String effectiveFrom, String dataVersion) {
    }

    public record Segment(int examYear, String category, int score, int segmentCount,
                          int cumulativeCount, String dataVersion) {
    }

    public record BatchLine(int examYear, String category, String batch, int lineScore,
                            String dataVersion) {
    }

    /** One historical admission row. minRank is the only cross-year comparable field. */
    public record AdmissionScore(long id, String provinceCode, int examYear, String category,
                                 String batch, String schoolCode, String schoolName,
                                 String groupCode, String majorCode, String unitName,
                                 int minScore, int minRank, Integer avgScore, Integer maxScore,
                                 Integer planCount, String dataVersion, String sourceUrl) {
    }

    /** Current-year admission plan, used for subject-requirement eligibility checks. */
    public record PlanUnit(String schoolCode, String groupCode, String majorCode, String category,
                           String reqCode, String reqName, String reqExpr, int planCount,
                           String dataVersion) {
    }

    public record Requirement(String reqCode, String reqName, String reqExpr) {
    }

    /** Result of score -> province rank conversion. */
    public record RankResult(int rank, int score, boolean estimated, int rankFrom, int rankTo,
                             int segmentCount, String dataVersion, String message) {
    }

    public record ScoreProfile(long id, long userId, String provinceCode, int examYear,
                               String examMode, String category, List<String> subjectCombo,
                               int totalScore, Map<String, Integer> subjectScores,
                               Integer rankValue, boolean rankEstimated, String createdAt) {
    }

    public record PlanRecord(long planId, long userId, String taskId, String planName,
                             String volunteerUnit, int volunteerCount, String dataVersion,
                             String createdAt) {
    }

    public record PlanItemRecord(long id, long planId, int positionNo, String schoolCode,
                                 String schoolName, String groupOrMajorCode, String unitName,
                                 String tier, int matchScore, Integer historyMinRank,
                                 String reason) {
    }

    public record TaskRecord(String taskId, long userId, String provinceCode, int examYear,
                             String status, String dataVersion, String createdAt) {
    }

    public record DataVersion(String dataVersion, boolean demoData, int schoolCount,
                              int majorCount, int admissionRowCount, int segmentRowCount,
                              int policyCount, String sourceNote) {
    }
}
