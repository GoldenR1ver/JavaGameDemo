package com.gameengine.math;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Vector2Test {

    @Test
    void equalVectorsMustHaveEqualHashCode() {
        Vector2 a = new Vector2(1.5f, -2.5f);
        Vector2 b = new Vector2(1.5f, -2.5f);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        Map<Vector2, String> map = new HashMap<>();
        map.put(a, "value");
        assertEquals("value", map.get(b), "Vector2 作为 HashMap 键时必须可用");
    }

    @Test
    void operationsShouldNotMutateSource() {
        Vector2 v = new Vector2(3, 4);
        Vector2 normalized = v.normalize();

        assertEquals(3f, v.x);
        assertEquals(4f, v.y);
        assertEquals(1f, normalized.magnitude(), 1e-5f);
    }

    @Test
    void normalizeZeroVectorShouldBeSafe() {
        Vector2 zero = new Vector2(0, 0);
        assertEquals(0f, zero.normalize().magnitude(), 1e-6f);
    }
}
