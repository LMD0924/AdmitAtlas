package com.admitatlas.common.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** VARCHAR 列存 JSON 字符串，解析失败必须安全降级为空集合而不是抛异常。 */
class JsonTest {

    @Test
    @DisplayName("写入后可读回 List<String>")
    void stringListRoundTrip() {
        String text = Json.write(List.of("物理", "化学", "生物"));
        assertEquals(List.of("物理", "化学", "生物"), Json.stringList(text));
    }

    @Test
    @DisplayName("写入后可读回 Map<String,Integer>，用于单科分")
    void intMapRoundTrip() {
        String text = Json.write(Map.of("语文", 118, "数学", 132));
        assertEquals(118, Json.intMap(text).get("语文"));
        assertEquals(132, Json.intMap(text).get("数学"));
    }

    @Test
    @DisplayName("objectMap 可解析嵌套结构")
    void objectMap() {
        Map<String, Object> value = Json.objectMap(
                "{\"provinceCode\":\"42\",\"examYear\":2026,\"nested\":{\"a\":1}}");
        assertEquals("42", value.get("provinceCode"));
        assertEquals(2026, value.get("examYear"));
        assertTrue(value.get("nested") instanceof Map);
    }

    @Test
    @DisplayName("null / 空白 / 非法 JSON 一律降级为空集合或 null")
    void safeFallback() {
        assertAll(
                () -> assertNull(Json.write(null)),
                () -> assertTrue(Json.stringList(null).isEmpty()),
                () -> assertTrue(Json.intMap("  ").isEmpty()),
                () -> assertTrue(Json.objectMap("not-json").isEmpty()),
                () -> assertTrue(Json.stringList("{broken").isEmpty())
        );
    }
}
