package com.gameengine.recording;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 录制服务核心类：负责游戏状态和输入事件的录制
 * 使用生产者-消费者模式，异步写入录像文件
 */
public class RecordingService {
    private final RecordingConfig config;   // 录制配置
    private final BlockingQueue<String> lineQueue;  // 数据队列
    private volatile boolean recording; // 录制状态
    private Thread writerThread;    // 文件写入线程
    private RecordingStorage storage = new FileRecordingStorage();  // 存储后端
    private double elapsed; // 累计时间
    private double keyframeElapsed; // 关键帧计时器
    private double sampleAccumulator;   // 采样累加器
    private final double warmupSec = 0.1; // 等待一帧让场景对象完成初始化
    private final DecimalFormat qfmt;    // 数值格式化器
    private Scene lastScene;    // 最后处理的场景
    /** 鼠标采样累积器：不再每帧写一行，显著减小录像体积 */
    private double mouseAccumulator;
    private static final double MOUSE_SAMPLE_INTERVAL = 0.1;

    // 修复：使用对象实例作为唯一标识符，避免名称重复问题
    private final Map<GameObject, String> objectIdMap = new HashMap<>();
    private int nextObjectId = 1;

    public RecordingService(RecordingConfig config) {
        this.config = config;
        this.lineQueue = new ArrayBlockingQueue<>(config.queueCapacity);
        this.recording = false;
        this.elapsed = 0.0;
        this.keyframeElapsed = 0.0;
        this.sampleAccumulator = 0.0;
        this.mouseAccumulator = 0.0;
        this.qfmt = new DecimalFormat();
        this.qfmt.setMaximumFractionDigits(Math.max(0, config.quantizeDecimals));
        this.qfmt.setGroupingUsed(false);
    }

    public boolean isRecording() {
        return recording;
    }

    /**
     * 开始录制：打开文件，启动写入线程
     */
    public void start(Scene scene, int width, int height) throws IOException {
        if (recording) return;

        this.objectIdMap.clear();
        this.nextObjectId = 1;
        this.lastScene = null;
        this.elapsed = 0.0;
        this.keyframeElapsed = 0.0;
        this.sampleAccumulator = 0.0;
        this.mouseAccumulator = 0.0;

        storage.openWriter(config.outputPath);
        writerThread = new Thread(() -> {
            try {
                // 消费者线程：从队列中取出数据并写入文件
                while (recording || !lineQueue.isEmpty()) {
                    String s = lineQueue.poll();
                    if (s == null) {
                        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    storage.writeLine(s);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { storage.closeWriter(); } catch (Exception ignored) {}
            }
        }, "record-writer");
        writerThread.setDaemon(true);
        recording = true;
        writerThread.start();

        // header
        enqueue("{\"type\":\"header\",\"version\":1,\"w\":" + width + ",\"h\":" + height + "}");
        keyframeElapsed = 0.0;
    }

    /**
     * 停止录制：写入最后一帧，关闭文件
     */
    public void stop() {
        if (!recording) return;
        try {
            // 确保写入最后一帧关键帧
            if (lastScene != null) {
                writeKeyframe(lastScene);
            }
        } catch (Exception ignored) {}
        recording = false;
        // 给写线程足够时间把队列排空，避免录像末尾被截断
        try { writerThread.join(2000); } catch (InterruptedException ignored) {}

        // 修复：清理对象ID映射
        objectIdMap.clear();
    }

    /**
     * 每帧更新：记录输入事件和关键帧
     */
    public void update(double deltaTime, Scene scene, InputManager input) {
        if (!recording) return;
        elapsed += deltaTime;
        keyframeElapsed += deltaTime;
        sampleAccumulator += deltaTime;
        lastScene = scene;

        // 记录鼠标位置（10Hz 采样即可，旧实现每帧写一行导致录像迅速膨胀）
        mouseAccumulator += deltaTime;
        if (mouseAccumulator >= MOUSE_SAMPLE_INTERVAL) {
            mouseAccumulator = 0.0;
            Vector2 mousePos = input.getMousePosition();
            StringBuilder mouseSb = new StringBuilder();
            mouseSb.append("{\"type\":\"mouse\",\"t\":").append(qfmt.format(elapsed))
                    .append(",\"x\":").append(qfmt.format(mousePos.x))
                    .append(",\"y\":").append(qfmt.format(mousePos.y))
                    .append("}");
            enqueue(mouseSb.toString());
        }

        // 记录瞬时按键输入
        Set<Integer> just = input.getJustPressedKeysSnapshot();
        if (!just.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"input\",\"t\":").append(qfmt.format(elapsed)).append(",\"keys\":[");
            boolean first = true;
            for (Integer k : just) {
                if (!first) sb.append(',');
                sb.append(k);
                first = false;
            }
            sb.append("]}");
            enqueue(sb.toString());
        }

        // 定期记录关键帧（跳过暖机阶段）
        if (elapsed >= warmupSec && keyframeElapsed >= config.keyframeIntervalSec) {
            if (writeKeyframe(scene)) {
                keyframeElapsed = 0.0;
            }
        }
    }

    /**
     * 获取对象的唯一ID
     */
    private String getObjectId(GameObject obj) {
        // 修复：为每个对象实例分配唯一ID，而不是使用名称
        return objectIdMap.computeIfAbsent(obj, k -> "obj_" + (nextObjectId++));
    }

    /**
     * 写入关键帧：记录所有实体的状态
     */
    private boolean writeKeyframe(Scene scene) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"keyframe\",\"t\":").append(qfmt.format(elapsed)).append(",\"entities\":[");
        List<GameObject> objs = scene.getGameObjects();
        boolean first = true;
        int count = 0;

        for (GameObject obj : objs) {
            TransformComponent tc = obj.getComponent(TransformComponent.class);
            if (tc == null) continue;

            float x = tc.getPosition().x;
            float y = tc.getPosition().y;

            if (!first) sb.append(',');

            // 修复：使用唯一对象ID而不是名称
            String objectId = getObjectId(obj);
            sb.append('{')
                    .append("\"id\":\"").append(objectId).append("\",")
                    .append("\"name\":\"").append(obj.getName()).append("\",")  // 保留名称用于显示
                    .append("\"x\":").append(qfmt.format(x)).append(',')
                    .append("\"y\":").append(qfmt.format(y));

            // 记录血量信息
            HealthComponent hc = obj.getComponent(HealthComponent.class);
            if (hc != null) {
                sb.append(',')
                        .append("\"health\":").append(qfmt.format(hc.getCurrentHealth())).append(',')
                        .append("\"maxHealth\":").append(qfmt.format(hc.getMaxHealth())).append(',')
                        .append("\"invulnerable\":").append(hc.isInvulnerable()).append(',')
                        .append("\"invulnerableTime\":").append(qfmt.format(hc.getInvulnerableTimeRemaining()));
            }

            // 记录技能信息
            SkillComponent sc = obj.getComponent(SkillComponent.class);
            if (sc != null) {
                sb.append(',')
                        .append("\"mana\":").append(qfmt.format(sc.getCurrentMana())).append(',')
                        .append("\"maxMana\":").append(qfmt.format(sc.getMaxMana())).append(',')
                        .append("\"skills\":{");

                // 记录每个技能的状态
                SkillComponent.SkillType[] skillTypes = SkillComponent.SkillType.values();
                boolean firstSkill = true;
                for (SkillComponent.SkillType skillType : skillTypes) {
                    if (!firstSkill) sb.append(',');
                    sb.append("\"").append(skillType.name()).append("\":{")
                            .append("\"state\":\"").append(sc.getSkillState(skillType).name()).append("\",")
                            .append("\"cooldown\":").append(qfmt.format(sc.getSkillCooldown(skillType))).append(',')
                            .append("\"totalCooldown\":").append(qfmt.format(sc.getSkillTotalCooldown(skillType))).append(',')
                            .append("\"chargeTime\":").append(qfmt.format(sc.getSkillChargeTime(skillType))).append(',')
                            .append("\"maxChargeTime\":").append(qfmt.format(sc.getSkillMaxChargeTime(skillType)))
                            .append("}");
                    firstSkill = false;
                }
                sb.append("}");
            }

            // 记录子弹信息（如果是子弹对象）
            if (obj.getName().startsWith("Bullet")) {
                sb.append(',')
                        .append("\"bullet\":true");
            }

            // 可选渲染信息（若对象带有 RenderComponent，则记录形状、尺寸、颜色）
            com.gameengine.components.RenderComponent rc = obj.getComponent(com.gameengine.components.RenderComponent.class);
            if (rc != null) {
                com.gameengine.components.RenderComponent.RenderType rt = rc.getRenderType();
                com.gameengine.math.Vector2 sz = rc.getSize();
                com.gameengine.components.RenderComponent.Color col = rc.getColor();
                sb.append(',')
                        .append("\"rt\":\"").append(rt.name()).append("\",")
                        .append("\"w\":").append(qfmt.format(sz.x)).append(',')
                        .append("\"h\":").append(qfmt.format(sz.y)).append(',')
                        .append("\"color\":[")
                        .append(qfmt.format(col.r)).append(',')
                        .append(qfmt.format(col.g)).append(',')
                        .append(qfmt.format(col.b)).append(',')
                        .append(qfmt.format(col.a)).append(']');
            } else {
                // 标记自定义渲染（如 Player），方便回放做近似还原
                sb.append(',').append("\"rt\":\"CUSTOM\"");
            }

            sb.append('}');
            first = false;
            count++;
        }
        sb.append("]}");
        if (count == 0) return false;
        enqueue(sb.toString());
        return true;
    }

    private void enqueue(String line) {
        if (!lineQueue.offer(line)) {
            // 简单丢弃策略：队列满时丢弃低优先级数据（此处直接丢弃）
        }
    }
}
