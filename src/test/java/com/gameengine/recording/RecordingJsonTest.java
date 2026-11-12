package com.gameengine.recording;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 轻量 JSON 解析器测试。
 * 旧实现 field() 遇到嵌套对象会截断，导致回放的技能状态解析错误。
 */
class RecordingJsonTest {

    private static final String LINE = "{\"type\":\"keyframe\",\"t\":1.5,\"entities\":["
            + "{\"id\":\"obj_1\",\"name\":\"Player\",\"x\":10.5,\"y\":20,"
            + "\"skills\":{\"MELEE_ATTACK\":{\"state\":\"READY\",\"cooldown\":0.5},"
            + "\"RANGED_ATTACK\":{\"state\":\"COOLDOWN\",\"cooldown\":1}},"
            + "\"color\":[1,0,0,1]}]}";

    @Test
    void shouldReadScalarField() {
        assertEquals("1.5", RecordingJson.field(LINE, "t"));
        assertEquals("obj_1", RecordingJson.stripQuotes(RecordingJson.field(LINE, "id")));
    }

    @Test
    void shouldReadNestedObjectCompletely() {
        String skills = RecordingJson.field(LINE, "skills");
        assertNotNull(skills);
        assertTrue(skills.startsWith("{") && skills.endsWith("}"));
        assertTrue(skills.contains("MELEE_ATTACK"));
        assertTrue(skills.contains("RANGED_ATTACK"));
        // 旧实现会在 MELEE_ATTACK 内部的第一个逗号处截断
        assertEquals("READY", RecordingJson.stripQuotes(RecordingJson.field(skills, "state")));
    }

    @Test
    void shouldReadNestedArrayCompletely() {
        String color = RecordingJson.field(LINE, "color");
        assertEquals("[1,0,0,1]", color);
    }

    @Test
    void shouldNotMatchKeyInsideStringValue() {
        String json = "{\"name\":\"health\",\"health\":42}";
        assertEquals("42", RecordingJson.field(json, "health"));
    }

    @Test
    void extractArrayShouldRespectNesting() {
        int idx = LINE.indexOf("\"entities\":[");
        String arr = RecordingJson.extractArray(LINE, LINE.indexOf('[', idx));
        assertTrue(arr.startsWith("{\"id\":\"obj_1\""));
        assertEquals(1, RecordingJson.splitTopLevel(arr).length);
    }
}
