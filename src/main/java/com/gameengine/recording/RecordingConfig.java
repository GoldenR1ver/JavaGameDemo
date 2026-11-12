package com.gameengine.recording;

/**
 * 录制配置类：定义录像文件的参数和输出设置
 */
public class RecordingConfig {
    public String outputPath;
    public float keyframeIntervalSec = 0.5f;    // 关键帧间隔时间（秒）
    public int sampleFps = 30;  // 采样帧
    public float positionThreshold = 0.5f; // 位置变化阈值（像素），小于此值不记录
    public int quantizeDecimals = 2;    // 数值量化精度（小数位数）
    public int queueCapacity = 2048;     // 录制队列容量

    /**
     * 构造函数：指定输出路径
     */
    public RecordingConfig(String outputPath) {
        this.outputPath = outputPath;
    }
}


