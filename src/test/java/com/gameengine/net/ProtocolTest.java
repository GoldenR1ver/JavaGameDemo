package com.gameengine.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议编解码的回归测试。
 * 这些断言直接覆盖旧实现最致命的 bug：客户端发 2 个字段、服务端要求 7 个字段，
 * 导致所有上行输入被静默丢弃。
 */
class ProtocolTest {

    @Test
    void inputCommandShouldRoundTrip() {
        PlayerCommand original = new PlayerCommand(42L, 1f, -1f, true, false, true, 123.4f, 567.8f);

        String line = Protocol.input(original);
        assertTrue(line.startsWith(Protocol.CMD_INPUT + ":"));

        PlayerCommand parsed = Protocol.parseInput(line.substring(Protocol.CMD_INPUT.length() + 1));
        assertNotNull(parsed);
        assertEquals(42L, parsed.sequence);
        assertEquals(1f, parsed.moveX);
        assertEquals(-1f, parsed.moveY);
        assertTrue(parsed.melee);
        assertFalse(parsed.ranged);
        assertTrue(parsed.dodge);
        assertEquals(123.4f, parsed.aimX, 0.01f);
        assertEquals(567.8f, parsed.aimY, 0.01f);
    }

    @Test
    void malformedInputShouldBeRejected() {
        assertNull(Protocol.parseInput(""));
        assertNull(Protocol.parseInput("1,2"));
        assertNull(Protocol.parseInput("a,b,c,d,e,f,g,h"));
    }

    @Test
    void valuesOutOfRangeShouldBeClamped() {
        PlayerCommand parsed = Protocol.parseInput("1,999,-999,false,false,false,NaN,Infinity");
        assertNotNull(parsed);
        assertEquals(1f, parsed.moveX);
        assertEquals(-1f, parsed.moveY);
        assertEquals(0f, parsed.aimX);
        assertEquals(0f, parsed.aimY);
    }

    @Test
    void playerNameShouldBeSanitized() {
        assertEquals("Player", Protocol.sanitizeName(null));
        assertEquals("Player", Protocol.sanitizeName("\n\r:,"));
        assertEquals("Alice1", Protocol.sanitizeName("Ali:ce,1"));
        assertFalse(Protocol.sanitizeName("a:b\nc").contains(":"));
        assertTrue(Protocol.sanitizeName("x".repeat(100)).length() <= 24);
    }
}
