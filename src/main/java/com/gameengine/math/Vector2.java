package com.gameengine.math;

import java.util.Objects;

/**
 * 二维向量。
 *
 * 注意：本类是不可变运算 + 可变字段的混合设计：
 * add/subtract/multiply/normalize 都会返回新对象，不会修改自身。
 * 因此可以安全地把它当作值对象放进 HashMap / HashSet（已实现 equals/hashCode）。
 */
public class Vector2 {
    public float x;
    public float y;

    public Vector2() {
        this(0, 0);
    }

    public Vector2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2(Vector2 other) {
        if (other == null) {
            this.x = 0;
            this.y = 0;
        } else {
            this.x = other.x;
            this.y = other.y;
        }
    }

    public void set(Vector2 other) { this.x = other.x; this.y = other.y; }

    public void set(float x, float y) { this.x = x; this.y = y; }

    public Vector2 copy() { return new Vector2(this); }

    public Vector2 add(Vector2 other) {
        return new Vector2(this.x + other.x, this.y + other.y);
    }

    public Vector2 subtract(Vector2 other) {
        return new Vector2(this.x - other.x, this.y - other.y);
    }

    public Vector2 multiply(float scalar) {
        return new Vector2(this.x * scalar, this.y * scalar);
    }

    public float magnitude() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public Vector2 normalize() {
        float mag = magnitude();
        if (mag < 1e-6f) return new Vector2(0, 0);
        return new Vector2(x / mag, y / mag);
    }

    public float dot(Vector2 other) {
        return this.x * other.x + this.y * other.y;
    }

    public float distance(Vector2 other) {
        return this.subtract(other).magnitude();
    }

    @Override
    public String toString() {
        return String.format("Vector2(%.2f, %.2f)", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vector2 vector2 = (Vector2) obj;
        return Float.compare(vector2.x, x) == 0 && Float.compare(vector2.y, y) == 0;
    }

    /**
     * 重写 equals 必须同时重写 hashCode，否则作为 HashMap 键时行为错误。
     */
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
