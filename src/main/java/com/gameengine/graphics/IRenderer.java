package com.gameengine.graphics;

/**
 * 渲染器接口：抽象渲染操作，支持不同后端（如 GPU）。
 */
public interface IRenderer {
    void beginFrame();  // 开始渲染帧
    void endFrame();    // 结束渲染帧
    
    void drawRect(float x, float y, float width, float height, float r, float g, float b, float a);
    void drawCircle(float x, float y, float radius, int segments, float r, float g, float b, float a);
    void drawLine(float x1, float y1, float x2, float y2, float r, float g, float b, float a);
    void drawText(float x, float y, String text, float r, float g, float b, float a);
    
    boolean shouldClose();  // 窗口是否应该关闭
    void pollEvents();  // 处理输入事件
    void cleanup(); // 清理资源
    
    int getWidth();
    int getHeight();
    String getTitle();
}

