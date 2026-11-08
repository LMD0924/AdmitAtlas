package com.admitatlas.common.repository;

import com.admitatlas.common.model.Models;
import com.admitatlas.common.support.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Single data-access facade for the modular monolith.
 *
 * <p>Every module shares one DataSource (dev: H2 file DB, prod: MySQL 8), so the
 * SQL lives here instead of being duplicated. Candidate recall deliberately
 * filters on {@code min_rank BETWEEN ...} inside SQL so the composite index
 * {@code idx_query} is used — never load all rows and filter in Java.
 */
@Repository
public class AdmitAtlasRepository {

    private final JdbcTemplate jdbc;

    public AdmitAtlasRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------- dimensions

    public List<Models.Province> provinces() {
        return jdbc.query("SELECT province_code, province_name, exam_mode, policy_version "
                + "FROM dim_province ORDER BY province_code", this::provinceRow);
    }

    public Optional<Models.Province> province(String code) {
        return first(jdbc.query("SELECT province_code, province_name, exam_mode, policy_version "
                + "FROM dim_province WHERE province_code = ?", this::provinceRow, code));
    }

    public Optional<Models.RulePolicy> policy(String provinceCode, int year) {
        return first(jdbc.query("SELECT province_code, exam_year, exam_mode, volunteer_unit, "
                        + "volunteer_count, group_major_count, batches, sort_fields, subject_rules, "
                        + "effective_from, data_version FROM rule_policy "
                        + "WHERE province_code = ? AND exam_year = ?",
                this::policyRow, provinceCode, year));
    }

    public List<Models.RulePolicy> policies() {
        return jdbc.query("SELECT province_code, exam_year, exam_mode, volunteer_unit, "
                + "volunteer_count, group_major_count, batches, sort_fields, subject_rules, "
                + "effective_from, data_version FROM rule_policy ORDER BY province_code, exam_year DESC",
                this::policyRow);
    }

    public long countSchools(String keyword, String provinceCode, String tier) {
        List<Object> args = new ArrayList<>();
        String where = schoolWhere(keyword, provinceCode, tier, args);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM dim_school " + where,
                Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    public List<Models.School> searchSchools(String keyword, String provinceCode, String tier,
                                             int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = schoolWhere(keyword, provinceCode, tier, args);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        return jdbc.query("SELECT school_code, school_name, supervisor, province_code, city, "
                        + "tier_tags, school_type, school_level, intro FROM dim_school " + where
                        + " ORDER BY school_code LIMIT ? OFFSET ?",
                this::schoolRow, args.toArray());
    }

    public Optional<Models.School> school(String code) {
        return first(jdbc.query("SELECT school_code, school_name, supervisor, province_code, city, "
                        + "tier_tags, school_type, school_level, intro FROM dim_school "
                        + "WHERE school_code = ?",
                this::schoolRow, code));
    }

    public List<Models.Major> majors(String keyword, String category) {
        StringBuilder sql = new StringBuilder("SELECT major_code, major_name, category, "
                + "major_class, degree_years FROM dim_major WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (has(keyword)) {
            sql.append(" AND (major_name LIKE ? OR major_code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (has(category)) {
            sql.append(" AND category = ?");
            args.add(category.trim());
        }
        sql.append(" ORDER BY category, major_code");
        return jdbc.query(sql.toString(), this::majorRow, args.toArray());
    }

    // -------------------------------------------------------------- score facts

    public List<Models.Segment> segments(String provinceCode, int year, String category) {
        return jdbc.query("SELECT exam_year, category, score, segment_count, cumulative_count, "
                        + "data_version FROM fact_score_segment "
                        + "WHERE province_code = ? AND exam_year = ? AND category = ? "
                        + "ORDER BY score",
                this::segmentRow, provinceCode, year, category);
    }

    public List<Models.BatchLine> batchLines(String provinceCode, int year, String category) {
        return jdbc.query("SELECT exam_year, category, batch, line_score, data_version "
                        + "FROM fact_batch_line WHERE province_code = ? AND exam_year = ? "
                        + "AND category = ? ORDER BY line_score DESC",
                this::batchLineRow, provinceCode, year, category);
    }

    /** School detail page: all historical admission rows for one school. */
    public List<Models.AdmissionScore> schoolAdmissionScores(String provinceCode, String schoolCode,
                                                             String category, int fromYear, int toYear) {
        return jdbc.query("SELECT s.id, s.province_code, s.exam_year, s.category, s.batch, "
                        + "s.school_code, sc.school_name, s.group_code, s.major_code, s.unit_name, "
                        + "s.min_score, s.min_rank, s.avg_score, s.max_score, s.plan_count, "
                        + "s.data_version, s.source_url FROM fact_admission_score s "
                        + "LEFT JOIN dim_school sc ON sc.school_code = s.school_code "
                        + "WHERE s.province_code = ? AND s.school_code = ? AND s.category = ? "
                        + "AND s.exam_year BETWEEN ? AND ? AND s.audit_status = 1 "
                        + "ORDER BY s.exam_year DESC, s.min_rank",
                this::admissionRow, provinceCode, schoolCode, category, fromYear, toYear);
    }

    /**
     * Candidate recall for the matching engine. The rank window is pushed into
     * SQL so idx_query (province, year, category, batch, min_rank) is used.
     */
    public List<Models.AdmissionScore> candidateScores(String provinceCode, String category,
                                                       String batch, List<Integer> years,
                                                       int minRank, int maxRank,
                                                       String dataVersion, int limit) {
        if (years.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(years.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(provinceCode);
        args.add(category);
        args.add(batch);
        args.addAll(years);
        args.add(minRank);
        args.add(maxRank);
        args.add(dataVersion);
        args.add(Math.min(500, Math.max(1, limit)));
        return jdbc.query("SELECT s.id, s.province_code, s.exam_year, s.category, s.batch, "
                        + "s.school_code, sc.school_name, s.group_code, s.major_code, s.unit_name, "
                        + "s.min_score, s.min_rank, s.avg_score, s.max_score, s.plan_count, "
                        + "s.data_version, s.source_url FROM fact_admission_score s "
                        + "LEFT JOIN dim_school sc ON sc.school_code = s.school_code "
                        + "WHERE s.province_code = ? AND s.category = ? AND s.batch = ? "
                        + "AND s.exam_year IN (" + placeholders + ") "
                        + "AND s.min_rank BETWEEN ? AND ? "
                        + "AND s.data_version = ? AND s.audit_status = 1 "
                        + "ORDER BY s.min_rank LIMIT ?",
                this::admissionRow, args.toArray());
    }

    public List<Models.PlanUnit> planUnits(String provinceCode, int year, String category) {
        return jdbc.query("SELECT p.school_code, p.group_code, p.major_code, p.category, "
                        + "p.req_code, r.req_name, r.req_expr, p.plan_count, p.data_version "
                        + "FROM fact_admission_plan p "
                        + "LEFT JOIN dim_subject_requirement r ON r.req_code = p.req_code "
                        + "WHERE p.province_code = ? AND p.exam_year = ? AND p.category = ? "
                        + "AND p.audit_status = 1",
                this::planUnitRow, provinceCode, year, category);
    }

    public Optional<Models.Requirement> requirement(String reqCode) {
        return first(jdbc.query("SELECT req_code, req_name, req_expr "
                        + "FROM dim_subject_requirement WHERE req_code = ?",
                this::requirementRow, reqCode));
    }

    public List<Models.Requirement> requirements() {
        return jdbc.query("SELECT req_code, req_name, req_expr FROM dim_subject_requirement "
                + "ORDER BY req_code", this::requirementRow);
    }

    // -------------------------------------------------------------- student

    public long saveScore(long userId, String provinceCode, int year, String examMode,
                          String category, List<String> subjectCombo, int totalScore,
                          java.util.Map<String, Integer> subjectScores,
                          Integer rankValue, boolean rankEstimated) {
        long id = com.admitatlas.common.support.Ids.next();
        jdbc.update("INSERT INTO student_score (id, user_id, province_code, exam_year, exam_mode, "
                        + "category, subject_combo, total_score, subject_scores, rank_value, "
                        + "rank_estimated) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, userId, provinceCode, year, examMode, category,
                Json.write(subjectCombo), totalScore, Json.write(subjectScores),
                rankValue, rankEstimated ? 1 : 0);
        return id;
    }

    public Optional<Models.ScoreProfile> latestScore(long userId, int year) {
        return first(jdbc.query("SELECT id, user_id, province_code, exam_year, exam_mode, "
                        + "category, subject_combo, total_score, subject_scores, rank_value, "
                        + "rank_estimated, created_at FROM student_score "
                        + "WHERE user_id = ? AND exam_year = ? ORDER BY id DESC LIMIT 1",
                this::scoreRow, userId, year));
    }

    public void saveTask(String taskId, long userId, String provinceCode, int year,
                         String status, String snapshot, String dataVersion) {
        jdbc.update("INSERT INTO match_task (task_id, user_id, province_code, exam_year, "
                        + "task_status, input_snapshot, data_version) VALUES (?,?,?,?,?,?,?)",
                taskId, userId, provinceCode, year, status, snapshot, dataVersion);
    }

    public Optional<Models.TaskRecord> task(String taskId) {
        return first(jdbc.query("SELECT task_id, user_id, province_code, exam_year, task_status, "
                        + "data_version, created_at FROM match_task WHERE task_id = ?",
                this::taskRow, taskId));
    }

    // -------------------------------------------------------------- plans

    @Transactional
    public long savePlan(Models.PlanRecord plan, List<Models.PlanItemRecord> items) {
        long planId = com.admitatlas.common.support.Ids.next();
        jdbc.update("INSERT INTO suggestion_plan (plan_id, user_id, task_id, plan_name, "
                        + "volunteer_unit, volunteer_count, data_version) VALUES (?,?,?,?,?,?,?)",
                planId, plan.userId(), plan.taskId(), plan.planName(), plan.volunteerUnit(),
                plan.volunteerCount(), plan.dataVersion());
        for (Models.PlanItemRecord item : items) {
            jdbc.update("INSERT INTO suggestion_item (id, plan_id, position_no, school_code, "
                            + "school_name, group_or_major_code, unit_name, tier, match_score, "
                            + "history_min_rank, reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    com.admitatlas.common.support.Ids.next(), planId, item.positionNo(),
                    item.schoolCode(), item.schoolName(), item.groupOrMajorCode(),
                    item.unitName(), item.tier(), item.matchScore(), item.historyMinRank(),
                    item.reason());
        }
        return planId;
    }

    public Optional<Models.PlanRecord> plan(long planId) {
        return first(jdbc.query("SELECT plan_id, user_id, task_id, plan_name, volunteer_unit, "
                        + "volunteer_count, data_version, created_at FROM suggestion_plan "
                        + "WHERE plan_id = ?",
                this::planRow, planId));
    }

    public List<Models.PlanRecord> plans(long userId) {
        return jdbc.query("SELECT plan_id, user_id, task_id, plan_name, volunteer_unit, "
                        + "volunteer_count, data_version, created_at FROM suggestion_plan "
                        + "WHERE user_id = ? ORDER BY plan_id DESC LIMIT 20",
                this::planRow, userId);
    }

    public List<Models.PlanItemRecord> planItems(long planId) {
        return jdbc.query("SELECT id, plan_id, position_no, school_code, school_name, "
                        + "group_or_major_code, unit_name, tier, match_score, history_min_rank, "
                        + "reason FROM suggestion_item WHERE plan_id = ? ORDER BY position_no",
                this::planItemRow, planId);
    }

    // -------------------------------------------------------------- meta

    public String latestDataVersion() {
        List<String> versions = jdbc.queryForList(
                "SELECT DISTINCT data_version FROM fact_admission_score ORDER BY data_version DESC",
                String.class);
        return versions.isEmpty() ? "uninitialized" : versions.get(0);
    }

    public Models.DataVersion dataVersion(boolean demoData) {
        String version = latestDataVersion();
        return new Models.DataVersion(
                version, demoData,
                count("dim_school"), count("dim_major"),
                count("fact_admission_score"), count("fact_score_segment"),
                count("rule_policy"),
                demoData ? "演示数据：全部数值为合成值，非真实录取数据；最终以各省教育考试院公布为准"
                        : "数据来自公开公示渠道并保留来源链接；最终以各省教育考试院公布为准");
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    // -------------------------------------------------------------- helpers

    private String schoolWhere(String keyword, String provinceCode, String tier,
                               List<Object> args) {
        StringBuilder sql = new StringBuilder("WHERE 1=1");
        if (has(keyword)) {
            sql.append(" AND (school_name LIKE ? OR school_code LIKE ? OR city LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (has(provinceCode)) {
            sql.append(" AND province_code = ?");
            args.add(provinceCode.trim());
        }
        if (has(tier)) {
            sql.append(" AND tier_tags LIKE ?");
            args.add("%" + tier.trim() + "%");
        }
        return sql.toString();
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static <T> Optional<T> first(List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    // -------------------------------------------------------------- row mappers

    private Models.Province provinceRow(ResultSet rs, int i) throws SQLException {
        return new Models.Province(rs.getString("province_code"), rs.getString("province_name"),
                rs.getString("exam_mode"), rs.getString("policy_version"));
    }

    private Models.RulePolicy policyRow(ResultSet rs, int i) throws SQLException {
        return new Models.RulePolicy(rs.getString("province_code"), rs.getInt("exam_year"),
                rs.getString("exam_mode"), rs.getString("volunteer_unit"),
                rs.getInt("volunteer_count"), nullableInt(rs, "group_major_count"),
                rs.getString("batches"), rs.getString("sort_fields"),
                rs.getString("subject_rules"), rs.getString("effective_from"),
                rs.getString("data_version"));
    }

    private Models.School schoolRow(ResultSet rs, int i) throws SQLException {
        return new Models.School(rs.getString("school_code"), rs.getString("school_name"),
                rs.getString("supervisor"), rs.getString("province_code"), rs.getString("city"),
                rs.getString("tier_tags"), rs.getString("school_type"),
                rs.getString("school_level"), rs.getString("intro"));
    }

    private Models.Major majorRow(ResultSet rs, int i) throws SQLException {
        return new Models.Major(rs.getString("major_code"), rs.getString("major_name"),
                rs.getString("category"), rs.getString("major_class"),
                nullableInt(rs, "degree_years"));
    }

    private Models.Segment segmentRow(ResultSet rs, int i) throws SQLException {
        return new Models.Segment(rs.getInt("exam_year"), rs.getString("category"),
                rs.getInt("score"), rs.getInt("segment_count"), rs.getInt("cumulative_count"),
                rs.getString("data_version"));
    }

    private Models.BatchLine batchLineRow(ResultSet rs, int i) throws SQLException {
        return new Models.BatchLine(rs.getInt("exam_year"), rs.getString("category"),
                rs.getString("batch"), rs.getInt("line_score"), rs.getString("data_version"));
    }

    private Models.AdmissionScore admissionRow(ResultSet rs, int i) throws SQLException {
        return new Models.AdmissionScore(rs.getLong("id"), rs.getString("province_code"),
                rs.getInt("exam_year"), rs.getString("category"), rs.getString("batch"),
                rs.getString("school_code"), rs.getString("school_name"),
                rs.getString("group_code"), rs.getString("major_code"), rs.getString("unit_name"),
                rs.getInt("min_score"), rs.getInt("min_rank"), nullableInt(rs, "avg_score"),
                nullableInt(rs, "max_score"), nullableInt(rs, "plan_count"),
                rs.getString("data_version"), rs.getString("source_url"));
    }

    private Models.PlanUnit planUnitRow(ResultSet rs, int i) throws SQLException {
        return new Models.PlanUnit(rs.getString("school_code"), rs.getString("group_code"),
                rs.getString("major_code"), rs.getString("category"), rs.getString("req_code"),
                rs.getString("req_name"), rs.getString("req_expr"), rs.getInt("plan_count"),
                rs.getString("data_version"));
    }

    private Models.Requirement requirementRow(ResultSet rs, int i) throws SQLException {
        return new Models.Requirement(rs.getString("req_code"), rs.getString("req_name"),
                rs.getString("req_expr"));
    }

    private Models.ScoreProfile scoreRow(ResultSet rs, int i) throws SQLException {
        return new Models.ScoreProfile(rs.getLong("id"), rs.getLong("user_id"),
                rs.getString("province_code"), rs.getInt("exam_year"), rs.getString("exam_mode"),
                rs.getString("category"), Json.stringList(rs.getString("subject_combo")),
                rs.getInt("total_score"), Json.intMap(rs.getString("subject_scores")),
                nullableInt(rs, "rank_value"), rs.getInt("rank_estimated") == 1,
                rs.getString("created_at"));
    }

    private Models.TaskRecord taskRow(ResultSet rs, int i) throws SQLException {
        return new Models.TaskRecord(rs.getString("task_id"), rs.getLong("user_id"),
                rs.getString("province_code"), rs.getInt("exam_year"),
                rs.getString("task_status"), rs.getString("data_version"),
                rs.getString("created_at"));
    }

    private Models.PlanRecord planRow(ResultSet rs, int i) throws SQLException {
        return new Models.PlanRecord(rs.getLong("plan_id"), rs.getLong("user_id"),
                rs.getString("task_id"), rs.getString("plan_name"), rs.getString("volunteer_unit"),
                rs.getInt("volunteer_count"), rs.getString("data_version"),
                rs.getString("created_at"));
    }

    private Models.PlanItemRecord planItemRow(ResultSet rs, int i) throws SQLException {
        return new Models.PlanItemRecord(rs.getLong("id"), rs.getLong("plan_id"),
                rs.getInt("position_no"), rs.getString("school_code"), rs.getString("school_name"),
                rs.getString("group_or_major_code"), rs.getString("unit_name"),
                rs.getString("tier"), rs.getInt("match_score"),
                nullableInt(rs, "history_min_rank"), rs.getString("reason"));
    }
}
