package com.gameengine.example;

import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;

/**
 * 玩家/网络玩家头像：用一个简单的"人形"矩形组合渲染。
 *
 * 之前 GameScene、NetworkGameScene 里各写了一份几乎相同的匿名内部类，
 * 修改颜色/比例需要改三处。这里统一成一个类，由颜色参数区分敌我。
 */
public class AvatarObject extends GameObject {
    private final float bodyR, bodyG, bodyB;
    private final Vector2 basePosition = new Vector2();

    public AvatarObject(String name, float bodyR, float bodyG, float bodyB) {
        super(name);
        this.bodyR = bodyR;
        this.bodyG = bodyG;
        this.bodyB = bodyB;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        TransformComponent transform = getComponent(TransformComponent.class);
        if (transform != null) {
            basePosition.set(transform.getPosition());
        }
    }

    @Override
    public void render() {
        IRenderer renderer = getScene() != null ? getScene().getRenderer() : null;
        if (renderer == null) return;

        // 身体
        renderer.drawRect(basePosition.x - 8, basePosition.y - 10, 16, 20,
                bodyR, bodyG, bodyB, 1.0f);
        // 头
        renderer.drawRect(basePosition.x - 6, basePosition.y - 22, 12, 12,
                mix(bodyR, 0.55f), mix(bodyG, 0.45f), mix(bodyB, 0.15f), 1.0f);
        // 左右手
        renderer.drawRect(basePosition.x - 13, basePosition.y - 5, 6, 12,
                bodyR, mix(bodyG, 0.8f), bodyB, 1.0f);
        renderer.drawRect(basePosition.x + 7, basePosition.y - 5, 6, 12,
                mix(bodyR, 0.2f), bodyG, bodyB, 1.0f);
    }

    /** 让头部/手臂颜色比身体略亮，视觉上更有层次 */
    private static float mix(float base, float delta) {
        return Math.max(0f, Math.min(1f, base + delta));
    }
}
