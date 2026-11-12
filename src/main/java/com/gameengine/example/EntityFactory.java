package com.gameengine.example;

import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;

/**
 * 实体工厂：集中管理"对象长什么样"。
 *
 * 场景只负责组装组件与行为，不再各自复制一套渲染代码。
 */
public final class EntityFactory {
    private EntityFactory() {}

    /** 本地玩家（红色） */
    public static GameObject createPlayer(String name) {
        return new AvatarObject(name, 1.0f, 0.0f, 0.0f);
    }

    /** 远端玩家（青色），用于客户端显示服务器上的其他玩家 */
    public static GameObject createRemotePlayer(String name, float r, float g, float b) {
        return new AvatarObject(name, r, g, b);
    }

    /**
     * 创建玩家视觉对象（保留旧 API，供 ReplayScene 使用）。
     */
    public static GameObject createPlayerVisual(IRenderer renderer) {
        return createPlayer("Player");
    }

    /**
     * 创建标准矩形/圆形渲染实体（AI、装饰物、回放对象、网络子弹等）。
     */
    public static GameObject createRenderable(String name, IRenderer renderer,
                                              RenderComponent.RenderType type,
                                              float width, float height,
                                              float r, float g, float b, float a) {
        GameObject obj = new GameObject(name);
        obj.addComponent(new TransformComponent(new Vector2(0, 0)));
        RenderComponent rc = obj.addComponent(new RenderComponent(
                type,
                new Vector2(Math.max(1f, width), Math.max(1f, height)),
                new RenderComponent.Color(r, g, b, a)
        ));
        rc.setRenderer(renderer);
        return obj;
    }

    /** AI 玩家（保留旧签名） */
    public static GameObject createAIVisual(IRenderer renderer, float w, float h,
                                            float r, float g, float b, float a) {
        return createRenderable("AIPlayer", renderer, RenderComponent.RenderType.RECTANGLE,
                w, h, r, g, b, a);
    }
}
