package com.gameengine.graphics;

/**
 * 渲染器工厂：根据后端类型创建渲染器实例。
 */
public class RendererFactory {
    public static IRenderer createRenderer(RenderBackend backend, int width, int height, String title) {
        if (backend == RenderBackend.GPU) {
            return new GPURenderer(width, height, title);
        }
        throw new IllegalArgumentException("不支持的渲染后端: " + backend);
    }
}

