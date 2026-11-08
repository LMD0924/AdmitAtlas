package com.admitatlas.common.support;

import com.admitatlas.common.model.Models;
import com.admitatlas.common.model.Models.Segment;

import java.util.Comparator;
import java.util.List;

/**
 * Score -> province rank (位次) conversion from the one-point-one-segment table.
 *
 * <p>Exact row: rank = cumulative_count, and the rank range is derived from
 * segment_count. Missing row: linear interpolation between the two nearest
 * published scores, flagged as estimated. Missing table entirely: a clearly
 * labelled rough estimate (never presented as a published rank).
 */
public final class RankCalculator {

    private RankCalculator() {
    }

    public static Models.RankResult convert(List<Segment> rows, int score, String dataVersion) {
        if (rows == null || rows.isEmpty()) {
            int rough = Math.max(1, (750 - score) * 320);
            return new Models.RankResult(
                    rough, score, true, rough, rough + 500, 0, dataVersion,
                    "该省 / 年份的一分一段表尚未发布，位次为粗略估算，不可用于正式决策");
        }

        List<Segment> sorted = rows.stream()
                .sorted(Comparator.comparingInt(Segment::score))
                .toList();

        for (Segment s : sorted) {
            if (s.score() == score) {
                int rankTo = s.cumulativeCount();
                // 即使分段人数缺失(0)也要保证区间至少覆盖 1 名，且 rankFrom <= rankTo
                int width = Math.max(1, s.segmentCount());
                int rankFrom = Math.max(1, rankTo - width + 1);
                return new Models.RankResult(
                        rankTo, score, false, rankFrom, rankTo, s.segmentCount(),
                        s.dataVersion() != null ? s.dataVersion() : dataVersion,
                        "按当年一分一段表精确换算");
            }
        }

        Segment below = null;   // highest score lower than target
        Segment above = null;   // lowest score higher than target
        for (Segment s : sorted) {
            if (s.score() < score && (below == null || s.score() > below.score())) {
                below = s;
            }
            if (s.score() > score && (above == null || s.score() < above.score())) {
                above = s;
            }
        }
        if (below == null || above == null) {
            Segment nearest = sorted.get(sorted.size() - 1);
            return new Models.RankResult(
                    nearest.cumulativeCount(), score, true,
                    nearest.cumulativeCount(), nearest.cumulativeCount(), 0,
                    dataVersion, "分数超出一分一段表范围，位次为边界估算");
        }

        double ratio = (double) (score - below.score()) / (above.score() - below.score());
        int rank = (int) Math.round(below.cumulativeCount()
                + ratio * (above.cumulativeCount() - below.cumulativeCount()));
        rank = Math.max(1, rank);
        return new Models.RankResult(
                rank, score, true, rank, rank, 0,
                below.dataVersion() != null ? below.dataVersion() : dataVersion,
                "一分一段表缺少 " + score + " 分精确行，由 " + below.score() + " / "
                        + above.score() + " 分线性插值估算");
    }

}
