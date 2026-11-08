package com.admitatlas.match;

import com.admitatlas.api.dto.MatchRequestDTO;
import com.admitatlas.api.dto.MatchResultDTO;
import com.admitatlas.api.dto.ScoreInputDTO;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.auth.AuthUser;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import com.admitatlas.common.support.Json;
import com.admitatlas.match.strategy.MatchStrategy;
import com.admitatlas.match.strategy.MatchStrategyFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** P1 匹配引擎：规则驱动、服务端位次、选科过滤、冲稳保分档与合规口径。 */
class MatchServiceTest {

    private static final String VERSION = "demo-2026-hb-v1";
    private static final String PROVINCE = "42";
    private static final int YEAR = 2026;
    private static final String CATEGORY = "物理类";
    private static final String MODE = "GROUP_3_1_2";

    private AdmitAtlasRepository repo;
    private MatchService service;

    private static Models.RulePolicy rule(int count, String unit) {
        return new Models.RulePolicy(PROVINCE, YEAR, MODE, unit, count, 6,
                "本科批", "总分,数学,语文", "", null, VERSION);
    }

    private static Models.AdmissionScore row(long id, int year, String school, String group,
                                             String unit, int minRank) {
        return new Models.AdmissionScore(id, PROVINCE, year, CATEGORY, "本科批",
                school, "演示院校 " + school, group, "M01", unit,
                600, minRank, null, null, 30, VERSION, "https://example.org/demo");
    }

    /** 三组各三年、同组位次稳定：11000(CHONG) / 12900(WEN) / 16000(BAO)。 */
    private static List<Models.AdmissionScore> recalled() {
        List<Models.AdmissionScore> rows = new ArrayList<>();
        int[] ranks = {11000, 12900, 16000};
        List<String> names = List.of("计算机类", "材料类", "经管类");
        long id = 1;
        for (int i = 0; i < ranks.length; i++) {
            String school = "S00" + (i + 1);
            String group = "G0" + (i + 1);
            for (int year = YEAR - 3; year < YEAR; year++) {
                rows.add(row(id++, year, school, group, names.get(i), ranks[i]));
            }
        }
        return rows;
    }

    private static Models.PlanUnit unit(String school, String group, String reqExpr) {
        return new Models.PlanUnit(school, group, "M01", CATEGORY,
                "R01", "选科要求", reqExpr, 30, VERSION);
    }

    private static MatchRequestDTO request(Integer frontendRank) {
        return new MatchRequestDTO(
                new ScoreInputDTO(PROVINCE, YEAR, MODE, CATEGORY,
                        List.of("物理", "化学", "生物"), 612, Map.of()),
                null, frontendRank, Map.of());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @BeforeEach
    void setUp() {
        AuthContext.set(new AuthUser(1L, "demo", "演示学生", "STUDENT", "42"));
        repo = mock(AdmitAtlasRepository.class);
        MatchStrategyFactory factory = mock(MatchStrategyFactory.class);
        when(factory.of(MODE)).thenReturn(mock(MatchStrategy.class));

        when(repo.latestDataVersion()).thenReturn(VERSION);
        when(repo.policy(PROVINCE, YEAR)).thenReturn(Optional.of(rule(45, "SCHOOL_GROUP")));
        when(repo.segments(PROVINCE, YEAR, CATEGORY))
                .thenReturn(List.of(new Models.Segment(YEAR, CATEGORY, 612, 425, 12900, VERSION)));
        when(repo.batchLines(PROVINCE, YEAR, CATEGORY))
                .thenReturn(List.of(new Models.BatchLine(YEAR, CATEGORY, "本科批", 400, VERSION)));
        when(repo.candidateScores(eq(PROVINCE), eq(CATEGORY), eq("本科批"), anyList(),
                anyInt(), anyInt(), eq(VERSION), anyInt())).thenReturn(recalled());
        when(repo.planUnits(PROVINCE, YEAR, CATEGORY)).thenReturn(List.of());
        when(repo.searchSchools(isNull(), isNull(), isNull(), eq(1), eq(200)))
                .thenReturn(List.of());

        service = new MatchService(repo, factory);
        ReflectionTestUtils.setField(service, "historyYears", 3);
        ReflectionTestUtils.setField(service, "rankWindowLow", 0.55);
        ReflectionTestUtils.setField(service, "rankWindowHigh", 1.9);
        ReflectionTestUtils.setField(service, "chongBoundary", -0.10);
        ReflectionTestUtils.setField(service, "baoBoundary", 0.15);
        ReflectionTestUtils.setField(service, "maxCandidates", 60);
        ReflectionTestUtils.setField(service, "demoData", true);
    }

    @Test
    @DisplayName("位次由服务端按一分一段表换算，忽略前端传入的 rank")
    void serverSideRankConversion() {
        MatchResultDTO result = service.analyze(request(999));
        assertAll(
                () -> assertEquals(12900, result.rank()),
                () -> assertFalse(result.rankEstimated()),
                () -> assertTrue(result.rankMessage().contains("精确换算"))
        );
    }

    @Test
    @DisplayName("志愿单位与数量从 rule_policy 读取，改配置即改结果，不硬编码 45 / 96")
    void ruleDrivenVolunteerStructure() {
        assertEquals(45, service.analyze(request(null)).volunteerCount());
        assertEquals("SCHOOL_GROUP", service.analyze(request(null)).volunteerUnit());

        when(repo.policy(PROVINCE, YEAR)).thenReturn(Optional.of(rule(96, "MAJOR_SCHOOL")));
        MatchResultDTO changed = service.analyze(request(null));
        assertAll(
                () -> assertEquals(96, changed.volunteerCount()),
                () -> assertEquals("MAJOR_SCHOOL", changed.volunteerUnit())
        );
    }

    @Test
    @DisplayName("规则未发布时降级到策略默认值并给出 warning")
    void missingPolicyFallsBack() {
        when(repo.policy(PROVINCE, YEAR)).thenReturn(Optional.empty());
        MatchResultDTO result = service.analyze(request(null));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("规则配置未发布")));
    }

    @Test
    @DisplayName("冲 / 稳 / 保三档按近三年加权位次差划分")
    void tierClassification() {
        MatchResultDTO result = service.analyze(request(null));
        assertAll(
                () -> assertEquals(9, result.recalledCount()),
                () -> assertEquals(3, result.items().size()),
                () -> assertEquals(1, result.tierCounts().get("CHONG")),
                () -> assertEquals(1, result.tierCounts().get("WEN")),
                () -> assertEquals(1, result.tierCounts().get("BAO")),
                () -> assertTrue(result.items().stream().anyMatch(i -> i.historyMinRank() == 11000)),
                () -> assertTrue(result.items().stream()
                        .allMatch(i -> i.matchScore() >= 35 && i.matchScore() <= 98))
        );
    }

    @Test
    @DisplayName("选科不符的候选被剔除，并计入 filteredCount")
    void subjectEligibilityFilter() {
        when(repo.planUnits(PROVINCE, YEAR, CATEGORY)).thenReturn(List.of(
                unit("S001", "G01", "物理"),
                unit("S002", "G02", "不限"),
                unit("S003", "G03", "历史")     // 考生是物理类组合，应被过滤
        ));
        MatchResultDTO result = service.analyze(request(null));
        assertAll(
                () -> assertEquals(2, result.items().size()),
                () -> assertEquals(1, result.filteredCount()),
                () -> assertTrue(result.items().stream()
                        .noneMatch(i -> "S003".equals(i.schoolCode()))),
                () -> assertTrue(result.items().stream()
                        .anyMatch(i -> i.subjectRequirement().contains("选科要求")))
        );
    }

    @Test
    @DisplayName("合规口径：只输出匹配度 / 风险等级，绝不出现录取率或保证录取")
    void complianceWording() {
        MatchResultDTO result = service.analyze(request(null));
        String payload = Json.write(result);
        assertAll(
                () -> assertTrue(result.disclaimer().contains("不构成录取承诺")),
                () -> assertTrue(result.disclaimer().contains("各省教育考试院")),
                () -> assertFalse(payload.contains("录取率")),
                () -> assertFalse(payload.contains("保证录取")),
                () -> assertTrue(result.warnings().stream()
                        .anyMatch(w -> w.contains("演示数据")))
        );
    }

    @Test
    @DisplayName("一次匹配读取同一数据版本，并落库留证任务")
    void singleDataVersionAndTaskAudit() {
        MatchResultDTO result = service.analyze(request(null));
        assertAll(
                () -> assertEquals(VERSION, result.dataVersion()),
                () -> assertEquals("DONE", result.status()),
                () -> assertTrue(result.taskId().startsWith("MT-"))
        );
        verify(repo).saveTask(startsWith("MT-"), eq(1L), eq(PROVINCE), eq(YEAR),
                eq("DONE"), anyString(), eq(VERSION));
        verify(repo).candidateScores(eq(PROVINCE), eq(CATEGORY), eq("本科批"), anyList(),
                anyInt(), anyInt(), eq(VERSION), anyInt());
    }

    @Test
    @DisplayName("位次窗口内无候选时给出可操作提示，不返回空黑盒")
    void emptyCandidates() {
        when(repo.candidateScores(eq(PROVINCE), eq(CATEGORY), eq("本科批"), anyList(),
                anyInt(), anyInt(), eq(VERSION), anyInt())).thenReturn(List.of());
        MatchResultDTO result = service.analyze(request(null));
        assertAll(
                () -> assertTrue(result.items().isEmpty()),
                () -> assertTrue(result.warnings().stream()
                        .anyMatch(w -> w.contains("没有符合选科与偏好条件")))
        );
    }

    @Test
    @DisplayName("成绩未达任何批次线时降级到本科批并明确提示")
    void belowBatchLine() {
        when(repo.batchLines(PROVINCE, YEAR, CATEGORY))
                .thenReturn(List.of(new Models.BatchLine(YEAR, CATEGORY, "本科批", 700, VERSION)));
        MatchResultDTO result = service.analyze(request(null));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("未达到任何批次线")));
    }
}
