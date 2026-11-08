package com.admitatlas.common.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** JSON columns are VARCHAR in the portable schema; parse/serialise here. */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Integer>> INT_MAP = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private Json() {
    }

    public static String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 序列化失败", e);
        }
    }

    public static List<String> stringList(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(text, STRING_LIST);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static Map<String, Object> objectMap(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(text, OBJECT_MAP);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public static Map<String, Integer> intMap(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(text, INT_MAP);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
