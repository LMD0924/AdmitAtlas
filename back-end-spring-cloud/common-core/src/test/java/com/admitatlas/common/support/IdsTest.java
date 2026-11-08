package com.admitatlas.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ID 由应用生成（DDL 保持 H2 / MySQL 可移植），必须保证唯一与单调递增。 */
class IdsTest {

    @Test
    @DisplayName("连续生成的 ID 唯一且严格递增")
    void uniqueAndIncreasing() {
        long first = Ids.next();
        long second = Ids.next();
        long third = Ids.next();
        assertAll(
                () -> assertTrue(second > first),
                () -> assertTrue(third > second),
                () -> assertEquals(3, new HashSet<>(java.util.List.of(first, second, third)).size())
        );
    }

    @Test
    @DisplayName("匹配任务 ID 带 MT- 前缀且批量不重复")
    void taskIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String id = Ids.taskId();
            assertTrue(id.startsWith("MT-"));
            ids.add(id);
        }
        assertEquals(500, ids.size());
    }
}
