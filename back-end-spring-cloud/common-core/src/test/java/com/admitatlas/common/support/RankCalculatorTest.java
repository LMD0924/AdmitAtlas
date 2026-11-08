package com.admitatlas.common.support;

import com.admitatlas.common.model.Models.RankResult;
import com.admitatlas.common.model.Models.Segment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 分数 → 位次换算：跨年比较的唯一可比口径，必须精确且可解释。 */
class RankCalculatorTest {

    private static final String VERSION = "demo-2026-hb-v1";

    private static Segment seg(int score, int segmentCount, int cumulativeCount) {
        return new Segment(2026, "物理类", score, segmentCount, cumulativeCount, VERSION);
    }

    @Test
    @DisplayName("命中精确行：位次取累计人数，并给出该分段覆盖的位次区间")
    void exactRow() {
        // 演示数据里 612 分对应 12900 名，同分段 425 人 → 12476 ~ 12900
        RankResult r = RankCalculator.convert(List.of(seg(612, 425, 12900)), 612, VERSION);
        assertAll(
                () -> assertEquals(12900, r.rank()),
                () -> assertEquals(612, r.score()),
                () -> assertFalse(r.estimated()),
                () -> assertEquals(12476, r.rankFrom()),
                () -> assertEquals(12900, r.rankTo()),
                () -> assertEquals(425, r.segmentCount()),
                () -> assertEquals(VERSION, r.dataVersion()),
                () -> assertTrue(r.message().contains("精确换算"))
        );
    }

    @Test
    @DisplayName("一分一段表乱序传入时仍能命中精确行")
    void unsortedRows() {
        RankResult r = RankCalculator.convert(
                List.of(seg(600, 100, 20000), seg(612, 425, 12900), seg(590, 80, 26000)),
                612, VERSION);
        assertEquals(12900, r.rank());
        assertFalse(r.estimated());
    }

    @Test
    @DisplayName("缺少精确行：在相邻两个分段之间线性插值并标记为估算")
    void interpolation() {
        // 分数越高累计人数越少；609→14000，611→13000，610 应落在 13500
        RankResult r = RankCalculator.convert(
                List.of(seg(609, 120, 14000), seg(611, 110, 13000)), 610, VERSION);
        assertAll(
                () -> assertEquals(13500, r.rank()),
                () -> assertTrue(r.estimated()),
                () -> assertEquals(13500, r.rankFrom()),
                () -> assertEquals(13500, r.rankTo()),
                () -> assertEquals(0, r.segmentCount()),
                () -> assertTrue(r.message().contains("609")),
                () -> assertTrue(r.message().contains("611"))
        );
    }

    @Test
    @DisplayName("分数超出表范围：返回边界估算，不得伪称精确位次")
    void outOfRange() {
        RankResult r = RankCalculator.convert(
                List.of(seg(600, 100, 20000), seg(610, 90, 18000)), 700, VERSION);
        assertAll(
                () -> assertTrue(r.estimated()),
                () -> assertTrue(r.message().contains("边界估算")),
                () -> assertTrue(r.rank() > 0)
        );
    }

    @Test
    @DisplayName("没有一分一段表：给出明确标注的粗略估算，禁止当成正式位次")
    void emptyTable() {
        RankResult r = RankCalculator.convert(List.of(), 612, VERSION);
        assertAll(
                () -> assertTrue(r.estimated()),
                () -> assertTrue(r.message().contains("粗略估算")),
                () -> assertTrue(r.message().contains("不可用于正式决策")),
                () -> assertTrue(r.rank() > 0)
        );
    }

    @Test
    @DisplayName("null 表 / null 版本不抛异常；分段人数为 0 时位次区间仍合法")
    void nullInputsAndDegenerateSegment() {
        assertDoesNotThrow(() -> RankCalculator.convert(null, 612, null));
        RankResult r = RankCalculator.convert(List.of(seg(612, 0, 12900)), 612, VERSION);
        assertAll(
                () -> assertEquals(12900, r.rankFrom()),
                () -> assertEquals(12900, r.rankTo()),
                () -> assertTrue(r.rankFrom() <= r.rankTo(),
                        "分段人数缺失时不得出现 rankFrom > rankTo 的非法区间"),
                () -> assertEquals(0, r.segmentCount())
        );
    }

    @Test
    @DisplayName("分段自带数据版本时优先使用分段版本")
    void segmentVersionWins() {
        String rowVersion = "hb-2026-published";
        Segment row = new Segment(2026, "物理类", 612, 10, 12900, rowVersion);
        assertEquals(rowVersion, RankCalculator.convert(List.of(row), 612, VERSION).dataVersion());
    }
}
