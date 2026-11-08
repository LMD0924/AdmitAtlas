package com.admitatlas.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 选科要求表达式解析：F-C-03 / F-C-38 的资格判断入口。 */
class SubjectRequirementsTest {

    @Test
    @DisplayName("不限 / NONE / 空表达式一律放行")
    void unlimited() {
        List<String> combo = List.of("物理", "化学", "生物");
        assertAll(
                () -> assertTrue(SubjectRequirements.check("不限", "不限", combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("NONE", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("  ", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check(null, null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("不限", null, null).eligible())
        );
    }

    @Test
    @DisplayName("单科要求：满足 / 缺失都能正确判断")
    void singleSubject() {
        List<String> physics = List.of("物理", "化学", "生物");
        List<String> history = List.of("历史", "政治", "地理");
        SubjectRequirements.CheckResult ok = SubjectRequirements.check("物理", "首选物理", physics);
        SubjectRequirements.CheckResult bad = SubjectRequirements.check("物理", "首选物理", history);
        assertAll(
                () -> assertTrue(ok.eligible()),
                () -> assertEquals(List.of("物理"), ok.required()),
                () -> assertTrue(ok.missing().isEmpty()),
                () -> assertFalse(bad.eligible()),
                () -> assertEquals(List.of("物理"), bad.missing()),
                () -> assertTrue(bad.description().contains("首选物理"))
        );
    }

    @Test
    @DisplayName("组合要求：所有科目都必须具备，缺一即列出缺失项")
    void combined() {
        List<String> physicsOnly = List.of("物理", "生物", "地理");
        List<String> full = List.of("物理", "化学", "生物");
        assertAll(
                () -> assertFalse(SubjectRequirements.check("物理+化学", "物化捆绑", physicsOnly).eligible()),
                () -> assertEquals(List.of("化学"),
                        SubjectRequirements.check("物理+化学", null, physicsOnly).missing()),
                () -> assertTrue(SubjectRequirements.check("物理+化学", null, full).eligible())
        );
    }

    @Test
    @DisplayName("全角加号、逗号、顿号、斜杠均可作为分隔符")
    void separators() {
        List<String> combo = List.of("物理", "化学", "生物");
        assertAll(
                () -> assertTrue(SubjectRequirements.check("物理＋化学", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("物理,化学", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("物理，化学", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("物理、化学", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check("物理/化学", null, combo).eligible()),
                () -> assertTrue(SubjectRequirements.check(" 物理 + 化学 ", null, combo).eligible())
        );
    }

    @Test
    @DisplayName("重复科目去重，避免重复报缺失")
    void distinct() {
        SubjectRequirements.CheckResult r =
                SubjectRequirements.check("物理+物理", null, List.of("历史", "政治", "地理"));
        assertEquals(List.of("物理"), r.required());
        assertEquals(List.of("物理"), r.missing());
    }

    @Test
    @DisplayName("选科组合为 null 时按空组合处理，不抛异常")
    void nullCombo() {
        SubjectRequirements.CheckResult r =
                SubjectRequirements.check("物理+化学", "物化捆绑", null);
        assertFalse(r.eligible());
        assertEquals(List.of("物理", "化学"), r.missing());
    }
}
