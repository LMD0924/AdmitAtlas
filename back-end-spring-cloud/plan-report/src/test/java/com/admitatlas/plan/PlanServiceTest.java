package com.admitatlas.plan;

import com.admitatlas.api.dto.PlanSaveRequestDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 志愿方案：结构校验、选科资格、梯度提示、导出留证。 */
class PlanServiceTest {

    private static final String VERSION = "demo-2026-hb-v1";
    private static final String PROVINCE = "42";
    private static final int YEAR = 2026;

    private AdmitAtlasRepository repo;
    private PlanService service;

    private static PlanSaveRequestDTO request(List<PlanSaveRequestDTO.PlanItemDTO> items,
                                              List<String> combo) {
        return new PlanSaveRequestDTO("我的志愿方案", "MT-1", PROVINCE, YEAR,
                "物理类", combo, items);
    }

    private static PlanSaveRequestDTO.PlanItemDTO item(int pos, String school, String group,
                                                       String tier) {
        return new PlanSaveRequestDTO.PlanItemDTO(pos, school, "演示院校 " + school,
                group, "专业组 " + group, tier, 88, 12000, "按位次匹配");
    }

    private static List<PlanSaveRequestDTO.PlanItemDTO> items(int count, String tier) {
        List<PlanSaveRequestDTO.PlanItemDTO> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(item(i, "S" + i, "G" + i, tier));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static List<String> errors(Map<String, Object> check) {
        return (List<String>) check.get("errors");
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(Map<String, Object> check) {
        return (List<String>) check.get("warnings");
    }

    @BeforeEach
    void setUp() {
        repo = mock(AdmitAtlasRepository.class);
        when(repo.policy(PROVINCE, YEAR)).thenReturn(Optional.of(
                new Models.RulePolicy(PROVINCE, YEAR, "GROUP_3_1_2", "SCHOOL_GROUP",
                        45, 6, "本科批", "", "", null, VERSION)));
        when(repo.planUnits(PROVINCE, YEAR, "物理类")).thenReturn(List.of());
        when(repo.latestDataVersion()).thenReturn(VERSION);
        when(repo.savePlan(any(), any())).thenReturn(1001L);
        service = new PlanService(repo);
        ReflectionTestUtils.setField(service, "demoData", true);
    }

    @Test
    @DisplayName("合法方案通过校验并落库，返回 planId 与规则中的志愿结构")
    void validPlanIsSaved() {
        List<PlanSaveRequestDTO.PlanItemDTO> rows = List.of(
                item(1, "S001", "G01", "CHONG"), item(2, "S002", "G02", "BAO"));
        Map<String, Object> check = service.save(request(rows, List.of("物理", "化学", "生物")));
        assertAll(
                () -> assertEquals(true, check.get("valid")),
                () -> assertTrue(errors(check).isEmpty()),
                () -> assertEquals(1001L, check.get("planId")),
                () -> assertEquals(true, check.get("saved")),
                () -> assertEquals(45, check.get("volunteerCount")),
                () -> assertEquals("SCHOOL_GROUP", check.get("volunteerUnit")),
                () -> assertEquals(VERSION, check.get("dataVersion"))
        );
        verify(repo).savePlan(any(), any());
    }

    @Test
    @DisplayName("超过本省志愿数上限时拒绝，且不落库")
    void rejectsTooManyVolunteers() {
        Map<String, Object> check = service.save(request(items(46, "BAO"), List.of()));
        assertAll(
                () -> assertEquals(false, check.get("valid")),
                () -> assertTrue(errors(check).stream().anyMatch(e -> e.contains("最多可填 45"))),
                () -> assertNull(check.get("planId"))
        );
        verify(repo, never()).savePlan(any(), any());
    }

    @Test
    @DisplayName("空方案、序号重复与不连续、志愿单位重复、档位非法均被拦截")
    void structuralErrors() {
        Map<String, Object> empty = service.validate(request(List.of(), List.of()));
        assertTrue(errors(empty).stream().anyMatch(e -> e.contains("至少包含 1 条")));

        Map<String, Object> duplicate = service.validate(request(List.of(
                item(1, "S001", "G01", "WEN"), item(1, "S001", "G01", "WEN")), List.of()));
        assertAll(
                () -> assertTrue(errors(duplicate).toString().contains("志愿序号重复")),
                () -> assertTrue(errors(duplicate).toString().contains("与前面重复")),
                () -> assertTrue(errors(duplicate).toString().contains("缺少序号 2"))
        );

        Map<String, Object> gap = service.validate(request(List.of(
                item(1, "S001", "G01", "WEN"), item(3, "S002", "G02", "BAO")), List.of()));
        assertTrue(errors(gap).toString().contains("缺少序号 2"));

        Map<String, Object> badTier = service.validate(request(List.of(
                item(1, "S001", "G01", "UNKNOWN")), List.of()));
        assertTrue(errors(badTier).toString().contains("档位不合法"));
    }

    @Test
    @DisplayName("逐条校验选科资格并指出缺少的科目")
    void subjectEligibility() {
        when(repo.planUnits(PROVINCE, YEAR, "物理类")).thenReturn(List.of(
                new Models.PlanUnit("S001", "G01", "M01", "物理类",
                        "R01", "物化捆绑", "物理+化学", 30, VERSION)));
        Map<String, Object> check = service.validate(request(List.of(
                item(1, "S001", "G01", "WEN")), List.of("物理", "生物", "地理")));
        assertAll(
                () -> assertEquals(false, check.get("valid")),
                () -> assertTrue(errors(check).stream().anyMatch(e -> e.contains("缺少 化学"))),
                () -> assertTrue(check.get("eligibility").toString().contains("eligible=false"))
        );
    }

    @Test
    @DisplayName("冲占比过高与缺少保档给出梯度提示，不阻断保存")
    void gradientWarnings() {
        Map<String, Object> check = service.validate(request(List.of(
                item(1, "S001", "G01", "CHONG"), item(2, "S002", "G02", "CHONG")), List.of()));
        assertAll(
                () -> assertEquals(true, check.get("valid")),
                () -> assertTrue(warnings(check).stream().anyMatch(w -> w.contains("冲」占比超过 40%"))),
                () -> assertTrue(warnings(check).stream().anyMatch(w -> w.contains("没有「保」档")))
        );
    }

    @Test
    @DisplayName("规则未发布时抛业务异常，禁止用默认数量代替本省规则")
    void missingRule() {
        when(repo.policy(PROVINCE, YEAR)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.validate(request(
                List.of(item(1, "S001", "G01", "WEN")), List.of())));
    }

    @Test
    @DisplayName("导出 CSV 带 UTF-8 BOM、数据版本、生成时间与免责声明")
    void exportCsvCarriesProvenance() {
        when(repo.plan(7L)).thenReturn(Optional.of(new Models.PlanRecord(
                7L, 1L, "MT-1", "我的志愿方案", "SCHOOL_GROUP", 45, VERSION, null)));
        when(repo.planItems(7L)).thenReturn(List.of(new Models.PlanItemRecord(
                1L, 7L, 1, "S001", "江城大学", "G01", "计算机类", "CHONG",
                88, 11000, "含逗号,和\"引号\"的理由")));

        byte[] bytes = service.exportCsv(7L);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals('\uFEFF', csv.charAt(0)),
                () -> assertTrue(csv.contains(VERSION)),
                () -> assertTrue(csv.contains("数据版本")),
                () -> assertTrue(csv.contains("生成时间")),
                () -> assertTrue(csv.contains("不构成录取承诺")),
                () -> assertTrue(csv.contains("冲")),
                () -> assertTrue(csv.contains("演示数据（合成值）")),
                () -> assertTrue(csv.contains("\"含逗号,和\"\"引号\"\"的理由\""),
                        "含逗号/引号的单元格必须按 CSV 规则转义")
        );
    }

    @Test
    @DisplayName("导出不存在的方案时抛业务异常")
    void exportMissingPlan() {
        when(repo.plan(999L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.exportCsv(999L));
    }
}
