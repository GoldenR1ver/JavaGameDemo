package com.gameengine.example;

import com.gameengine.components.RenderComponent;
import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.recording.RecordingJson;
import com.gameengine.scene.Scene;
import com.gameengine.example.EntityFactory;

import java.io.File;
import java.util.*;

/**
 * 回放场景：加载和播放录像文件
 * 支持文件选择模式和回放播放模式
 */
public class ReplayScene extends Scene {
    private final GameEngine engine;
    private String recordingPath;   // 录像文件路径
    private IRenderer renderer;
    private InputManager input;
    private float time;
    private boolean DEBUG_REPLAY = false;   // 调试模式
    private float debugAccumulator = 0f;

    private GameObject replayPlayer;
    private Map<String, GameObject> playerStateObjects = new HashMap<>();

    /**
     * 关键帧数据结构：存储某一时刻的所有实体状态
     */
    private static class Keyframe {
        static class EntityInfo {
            Vector2 pos;     // 位置
            String rt;       // 渲染类型：RECTANGLE/CIRCLE/LINE/CUSTOM/null
            float w, h;      // 尺寸
            float r=0.9f,g=0.9f,b=0.2f,a=1.0f; // 颜色（默认值）
            String id;       // 实体唯一ID
            String name;     // 实体名称
            Float health;    // 血量（新增）
            Float maxHealth; // 最大血量（新增）
            Boolean invulnerable; // 无敌状态（新增）
            Float invulnerableTime; // 无敌时间（新增）
            Float mana;      // 魔力值（新增）
            Float maxMana;   // 最大魔力值（新增）
            Boolean bullet;  // 是否为子弹（新增）

            // 记录玩家状态
            static class SkillStateInfo {
                String state;
                Float cooldown;
                Float totalCooldown;
                Float chargeTime;
                Float maxChargeTime;
            }
            Map<String, SkillStateInfo> skillStates; // 技能状态映射

        }
        double t;   // 时间戳
        List<EntityInfo> entities = new ArrayList<>();    // 实体列表
    }

    // 解析玩家状态
    private Map<String, Keyframe.EntityInfo.SkillStateInfo> parseSkillStates(String skillsJson) {
        Map<String, Keyframe.EntityInfo.SkillStateInfo> skillStates = new HashMap<>();
        if (skillsJson == null || skillsJson.isEmpty()) return skillStates;

        try {
            // 解析技能JSON，例如：{"MELEE_ATTACK":{"state":"READY","cooldown":0,...},...}
            String[] skills = skillsJson.split("},");
            for (String skill : skills) {
                if (skill.contains("{")) {
                    // 提取技能类型
                    int typeStart = skill.indexOf("\"") + 1;
                    int typeEnd = skill.indexOf("\"", typeStart);
                    if (typeEnd > typeStart) {
                        String skillType = skill.substring(typeStart, typeEnd);
                        Keyframe.EntityInfo.SkillStateInfo stateInfo = new Keyframe.EntityInfo.SkillStateInfo();

                        // 解析技能状态
                        stateInfo.state = RecordingJson.stripQuotes(RecordingJson.field(skill, "state"));
                        stateInfo.cooldown = (float)RecordingJson.parseDouble(RecordingJson.field(skill, "cooldown"));
                        stateInfo.totalCooldown = (float)RecordingJson.parseDouble(RecordingJson.field(skill, "totalCooldown"));
                        stateInfo.chargeTime = (float)RecordingJson.parseDouble(RecordingJson.field(skill, "chargeTime"));
                        stateInfo.maxChargeTime = (float)RecordingJson.parseDouble(RecordingJson.field(skill, "maxChargeTime"));

                        skillStates.put(skillType, stateInfo);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("解析技能状态失败: " + e.getMessage());
        }
        return skillStates;
    }

    // 新增：鼠标位置数据结构
    private static class MouseEvent {
        double t;   // 时间戳
        float x, y; // 鼠标位置
    }



    private final List<Keyframe> keyframes = new ArrayList<>(); // 所有关键帧
    private final List<MouseEvent> mouseEvents = new ArrayList<>(); // 所有鼠标事件（新增）

    // 修复：使用ID映射来跟踪对象，而不是按顺序
    private final Map<String, GameObject> objectMap = new HashMap<>(); // ID到对象的映射
    private final List<GameObject> objectList = new ArrayList<>();    // 回放对象列表（用于渲染顺序）

    // 如果 path 为 null，则先展示 recordings 目录下的文件列表，供用户选择
    public ReplayScene(GameEngine engine, String path) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = path;
    }

    @Override
    public boolean usesPhysics() {
        return false;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        // 重置状态，防止从列表进入后残留
        this.time = 0f;
        this.keyframes.clear();
        this.mouseEvents.clear(); // 新增：清空鼠标事件
        this.objectMap.clear(); // 修复：清空对象映射
        this.objectList.clear();
        if (recordingPath != null) {
            loadRecording(recordingPath);   // 加载录像文件
            buildObjectsFromFirstKeyframe();    // 从第一帧构建对象

        } else {
            // 仅进入文件选择模式
            this.recordingFiles = null;
            this.selectedIndex = 0;
        }
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        // ESC 键返回菜单
        if (input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_ESCAPE)
                || input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_BACKSPACE)) {
            engine.setScene(new MenuScene(engine, "MainMenu"));
            return;
        }
        // 文件选择模式
        if (recordingPath == null) {
            handleFileSelection();
            return;
        }

        if (keyframes.size() < 1) return;
        time += deltaTime;
        // 限制在最后关键帧处停止（也可选择循环播放）
        double lastT = keyframes.get(keyframes.size() - 1).t;
        if (time > lastT) {
            time = (float)lastT;
        }

        // 查找区间
        Keyframe a = keyframes.get(0);
        Keyframe b = keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) { a = k1; b = k2; break; }
        }
        double span = Math.max(1e-6, b.t - a.t);
        double u = Math.min(1.0, Math.max(0.0, (time - a.t) / span));
        // 调试输出节流


        updateInterpolatedPositions(a, b, (float)u);
    }

    /**
     * 新增：渲染鼠标位置
     */
    private void renderMousePosition() {
        if (mouseEvents.isEmpty()) return;

        // 找到当前时间对应的鼠标位置
        MouseEvent currentMouse = mouseEvents.get(0);
        for (int i = 0; i < mouseEvents.size() - 1; i++) {
            MouseEvent m1 = mouseEvents.get(i);
            MouseEvent m2 = mouseEvents.get(i + 1);
            if (time >= m1.t && time <= m2.t) {
                double span = Math.max(1e-6, m2.t - m1.t);
                double u = (time - m1.t) / span;
                float x = (float)((1.0 - u) * m1.x + u * m2.x);
                float y = (float)((1.0 - u) * m1.y + u * m2.y);
                currentMouse = new MouseEvent();
                currentMouse.x = x;
                currentMouse.y = y;
                break;
            }
        }

        // 渲染鼠标光标
        float mouseX = currentMouse.x;
        float mouseY = currentMouse.y;
        renderer.drawLine(mouseX - 10, mouseY, mouseX + 10, mouseY, 1.0f, 1.0f, 1.0f, 0.8f);
        renderer.drawLine(mouseX, mouseY - 10, mouseX, mouseY + 10, 1.0f, 1.0f, 1.0f, 0.8f);
        renderer.drawCircle(mouseX, mouseY, 5, 12, 1.0f, 1.0f, 1.0f, 0.6f);
    }

    /**
     * 加载录像文件并解析关键帧
     */
    private void loadRecording(String path) {
        keyframes.clear();
        mouseEvents.clear(); // 新增：清空鼠标事件
        com.gameengine.recording.RecordingStorage storage = new com.gameengine.recording.FileRecordingStorage();
        try {
            for (String line : storage.readLines(path)) {
                // 新增：解析鼠标事件
                if (line.contains("\"type\":\"mouse\"")) {
                    MouseEvent mouseEvent = new MouseEvent();
                    mouseEvent.t = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "t"));
                    mouseEvent.x = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "x"));
                    mouseEvent.y = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "y"));
                    mouseEvents.add(mouseEvent);
                }

                if (line.contains("\"type\":\"keyframe\"")) {
                    Keyframe kf = new Keyframe();
                    kf.t = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(line, "t"));
                    // 解析 entities 列表中的若干 {"id":"name","x":num,"y":num}
                    int idx = line.indexOf("\"entities\":[");
                    if (idx >= 0) {
                        int bracket = line.indexOf('[', idx);
                        String arr = bracket >= 0 ? com.gameengine.recording.RecordingJson.extractArray(line, bracket) : "";
                        String[] parts = com.gameengine.recording.RecordingJson.splitTopLevel(arr);
                        for (String p : parts) {
                            Keyframe.EntityInfo ei = new Keyframe.EntityInfo();
                            ei.id = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "id"));
                            ei.name = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "name"));
                            // 修复：如果name为空，使用id作为name
                            if (ei.name == null) ei.name = ei.id;

                            double x = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "x"));
                            double y = com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "y"));
                            ei.pos = new Vector2((float)x, (float)y);
                            String rt = com.gameengine.recording.RecordingJson.stripQuotes(com.gameengine.recording.RecordingJson.field(p, "rt"));
                            ei.rt = rt;
                            ei.w = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "w"));
                            ei.h = (float)com.gameengine.recording.RecordingJson.parseDouble(com.gameengine.recording.RecordingJson.field(p, "h"));

                            // 新增：解析血量信息
                            String healthStr = com.gameengine.recording.RecordingJson.field(p, "health");
                            if (healthStr != null) {
                                ei.health = (float)com.gameengine.recording.RecordingJson.parseDouble(healthStr);
                            }
                            String maxHealthStr = com.gameengine.recording.RecordingJson.field(p, "maxHealth");
                            if (maxHealthStr != null) {
                                ei.maxHealth = (float)com.gameengine.recording.RecordingJson.parseDouble(maxHealthStr);
                            }
                            String invulnerableStr = com.gameengine.recording.RecordingJson.field(p, "invulnerable");
                            if (invulnerableStr != null) {
                                ei.invulnerable = Boolean.parseBoolean(invulnerableStr);
                            }
                            String invulnerableTimeStr = com.gameengine.recording.RecordingJson.field(p, "invulnerableTime");
                            if (invulnerableTimeStr != null) {
                                ei.invulnerableTime = (float)com.gameengine.recording.RecordingJson.parseDouble(invulnerableTimeStr);
                            }

                            // 新增：解析技能信息
                            String manaStr = com.gameengine.recording.RecordingJson.field(p, "mana");
                            if (manaStr != null) {
                                ei.mana = (float)com.gameengine.recording.RecordingJson.parseDouble(manaStr);
                            }
                            String maxManaStr = com.gameengine.recording.RecordingJson.field(p, "maxMana");
                            if (maxManaStr != null) {
                                ei.maxMana = (float)com.gameengine.recording.RecordingJson.parseDouble(maxManaStr);
                            }

                            // 新增：解析子弹信息
                            String bulletStr = com.gameengine.recording.RecordingJson.field(p, "bullet");
                            if (bulletStr != null) {
                                ei.bullet = Boolean.parseBoolean(bulletStr);
                            }

                            // 解析颜色信息
                            String colorArr = com.gameengine.recording.RecordingJson.field(p, "color");
                            if (colorArr != null && colorArr.startsWith("[")) {
                                String c = colorArr.substring(1, Math.max(1, colorArr.indexOf(']', 1)));
                                String[] cs = c.split(",");
                                if (cs.length >= 3) {
                                    try {
                                        ei.r = Float.parseFloat(cs[0].trim());
                                        ei.g = Float.parseFloat(cs[1].trim());
                                        ei.b = Float.parseFloat(cs[2].trim());
                                        if (cs.length >= 4) ei.a = Float.parseFloat(cs[3].trim());
                                    } catch (Exception ignored) {}
                                }
                            }

                            // 解析技能状态
                            String skillsJson = RecordingJson.field(p, "skills");
                            if (skillsJson != null) {
                                ei.skillStates = parseSkillStates(skillsJson);
                            }

                            kf.entities.add(ei);
                        }
                    }
                    keyframes.add(kf);
                }
            }
        } catch (Exception e) {
            System.err.println("Load Recording Failed");
            e.printStackTrace();
        }
        keyframes.sort(Comparator.comparingDouble(k -> k.t));
        mouseEvents.sort(Comparator.comparingDouble(m -> m.t)); // 新增：排序鼠标事件
    }

    /**
     * 从第一帧构建回放对象
     */
    private void buildObjectsFromFirstKeyframe() {
        if (keyframes.isEmpty()) return;
        Keyframe kf0 = keyframes.get(0);
        // 按实体构建对象（使用预制），实现与游戏内一致外观
        objectMap.clear();
        objectList.clear();
        clear();
        for (Keyframe.EntityInfo ei : kf0.entities) {
            GameObject obj = buildObjectFromEntity(ei);
            addGameObject(obj);
            objectMap.put(ei.id, obj);
            objectList.add(obj);
        }
        time = 0f;
    }

    /**
     * 修复：根据ID映射来管理对象，而不是按顺序
     */
    private void updateObjectMap(Keyframe kf) {
        // 收集当前帧中存在的所有ID
        Set<String> currentIds = new HashSet<>();
        for (Keyframe.EntityInfo ei : kf.entities) {
            currentIds.add(ei.id);
        }

        // 移除不存在的对象
        Iterator<Map.Entry<String, GameObject>> iterator = objectMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GameObject> entry = iterator.next();
            if (!currentIds.contains(entry.getKey())) {
                GameObject obj = entry.getValue();
                obj.setActive(false);
                objectList.remove(obj);
                iterator.remove();
            }
        }

        // 添加新对象
        for (Keyframe.EntityInfo ei : kf.entities) {
            if (!objectMap.containsKey(ei.id)) {
                GameObject obj = buildObjectFromEntity(ei);
                addGameObject(obj);
                objectMap.put(ei.id, obj);
                objectList.add(obj);
            }
        }
    }

    /**
     * 更新插值位置：在两个关键帧之间平滑过渡
     */
    private void updateInterpolatedPositions(Keyframe a, Keyframe b, float u) {
        // 修复：确保对象映射与当前帧同步
        updateObjectMap(a);

        // 创建ID到实体信息的映射，便于查找
        Map<String, Keyframe.EntityInfo> aMap = new HashMap<>();
        Map<String, Keyframe.EntityInfo> bMap = new HashMap<>();
        for (Keyframe.EntityInfo ei : a.entities) aMap.put(ei.id, ei);
        for (Keyframe.EntityInfo ei : b.entities) bMap.put(ei.id, ei);

        // 为每个对象插值位置
        for (Map.Entry<String, GameObject> entry : objectMap.entrySet()) {
            String id = entry.getKey();
            GameObject obj = entry.getValue();

            Keyframe.EntityInfo eiA = aMap.get(id);
            Keyframe.EntityInfo eiB = bMap.get(id);

            if (eiA != null && eiB != null) {
                Vector2 pa = eiA.pos;
                Vector2 pb = eiB.pos;
                float x = (float)((1.0 - u) * pa.x + u * pb.x);
                float y = (float)((1.0 - u) * pa.y + u * pb.y);

                TransformComponent tc = obj.getComponent(TransformComponent.class);
                if (tc != null) tc.setPosition(new Vector2(x, y));

                // 新增：更新血量显示（如果有血量信息）
                updateHealthDisplay(obj, eiA, eiB, u);
            }
        }
    }

    /**
     * 新增：更新血量显示
     */
    private void updateHealthDisplay(GameObject obj, Keyframe.EntityInfo eiA, Keyframe.EntityInfo eiB, float u) {
        // 这里可以添加血量条的渲染逻辑
        // 目前先简单地在控制台输出
        if (DEBUG_REPLAY && eiA.health != null && eiB.health != null) {
            debugAccumulator += 0.016f; // 约60FPS
            if (debugAccumulator > 1.0f) {
                float health = (float)((1.0 - u) * eiA.health + u * eiB.health);
                System.out.println(obj.getName() + " Health: " + health);
                debugAccumulator = 0f;
            }
        }
    }

    /**
     * 根据实体信息构建游戏对象
     */
    private GameObject buildObjectFromEntity(Keyframe.EntityInfo ei) {
        GameObject obj;

        // 修复：使用name字段而不是id字段来判断对象类型
        if ("Player".equalsIgnoreCase(ei.name)
                || (ei.name != null && ei.name.startsWith("NetworkPlayer"))
                || "LocalPlayer".equalsIgnoreCase(ei.name)) {
            obj = com.gameengine.example.EntityFactory.createPlayerVisual(renderer);
            if (ei.name != null) obj.setName(ei.name);
        } else if ("AIPlayer".equalsIgnoreCase(ei.name)) {
            float w2 = (ei.w > 0 ? ei.w : 20);
            float h2 = (ei.h > 0 ? ei.h : 20);
            obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, w2, h2, ei.r, ei.g, ei.b, ei.a);
        } else if (ei.bullet != null && ei.bullet) {
            // 新增：子弹对象
            obj = new GameObject(ei.name != null ? ei.name : ei.id);
            obj.addComponent(new TransformComponent(new Vector2(0,0)));
            com.gameengine.components.RenderComponent rc = obj.addComponent(
                    new com.gameengine.components.RenderComponent(
                            com.gameengine.components.RenderComponent.RenderType.RECTANGLE,
                            new Vector2(6, 6), // 子弹尺寸
                            new com.gameengine.components.RenderComponent.Color(0.0f, 1.0f, 1.0f, 1.0f) // 青色子弹
                    )
            );
            rc.setRenderer(renderer);
        } else {
            if ("CIRCLE".equals(ei.rt)) {
                GameObject tmp = new GameObject(ei.name != null ? ei.name : ei.id);
                tmp.addComponent(new TransformComponent(new Vector2(0,0)));
                com.gameengine.components.RenderComponent rc = tmp.addComponent(
                        new com.gameengine.components.RenderComponent(
                                com.gameengine.components.RenderComponent.RenderType.CIRCLE,
                                new Vector2(Math.max(1, ei.w), Math.max(1, ei.h)),
                                new com.gameengine.components.RenderComponent.Color(ei.r, ei.g, ei.b, ei.a)
                        )
                );
                rc.setRenderer(renderer);
                obj = tmp;
            } else {
                obj = com.gameengine.example.EntityFactory.createAIVisual(renderer, Math.max(1, ei.w>0?ei.w:10), Math.max(1, ei.h>0?ei.h:10), ei.r, ei.g, ei.b, ei.a);
            }
            obj.setName(ei.name != null ? ei.name : ei.id);
        }
        TransformComponent tc = obj.getComponent(TransformComponent.class);
        if (tc == null) obj.addComponent(new TransformComponent(new Vector2(ei.pos)));
        else tc.setPosition(new Vector2(ei.pos));
        return obj;
    }

    public void renderReplayHud() {
        IRenderer renderer = getRenderer();
        if (renderer == null) return;

        // 查找当前帧的玩家状态
        Keyframe currentFrame = findCurrentKeyframe();
        if (currentFrame == null) return;

        // 渲染所有玩家的状态
        for (Keyframe.EntityInfo entity : currentFrame.entities) {
            if (entity.health != null || entity.mana != null) {
                renderEntityStatus(entity, renderer);
            }
        }
    }

    // 新增：查找当前时间对应的关键帧
    private Keyframe findCurrentKeyframe() {
        if (keyframes.isEmpty()) return null;

        // 找到包含当前时间的关键帧
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.t && time <= k2.t) {
                return k1; // 返回前一帧，或者可以插值
            }
        }
        return keyframes.get(keyframes.size() - 1);
    }

    // 新增：渲染实体状态（血条、魔力条等）
    private void renderEntityStatus(Keyframe.EntityInfo entity, IRenderer renderer) {
        Vector2 screenPos = entity.pos;

        // 渲染血条（如果存在血量信息）
        if (entity.health != null && entity.maxHealth != null && entity.maxHealth > 0) {
            float healthPercent = entity.health / entity.maxHealth;
            renderHealthBar(screenPos, healthPercent, entity.invulnerable, renderer);
        }

        // 渲染魔力条（如果存在魔力信息）
        if (entity.mana != null && entity.maxMana != null && entity.maxMana > 0) {
            float manaPercent = entity.mana / entity.maxMana;
            renderManaBar(screenPos, manaPercent, renderer);
        }

        // 渲染技能状态（如果存在技能信息）
        if (entity.skillStates != null && !entity.skillStates.isEmpty()) {
            renderSkillStatus(screenPos, entity.skillStates, renderer);
        }
    }

    // 新增：渲染血条
    private void renderHealthBar(Vector2 position, float healthPercent, Boolean invulnerable, IRenderer renderer) {
        float barWidth = 40f;
        float barHeight = 4f;
        float barYOffset = -25f; // 血条在实体上方的位置

        // 血条背景
        float barX = position.x - barWidth / 2;
        float barY = position.y + barYOffset;

        renderer.drawRect(barX, barY, barWidth, barHeight, 0.3f, 0.3f, 0.3f, 0.8f);

        // 当前血量
        float healthWidth = barWidth * healthPercent;
        if (healthPercent > 0.6f) {
            renderer.drawRect(barX, barY, healthWidth, barHeight, 0.0f, 1.0f, 0.0f, 0.8f); // 绿色
        } else if (healthPercent > 0.3f) {
            renderer.drawRect(barX, barY, healthWidth, barHeight, 1.0f, 1.0f, 0.0f, 0.8f); // 黄色
        } else {
            renderer.drawRect(barX, barY, healthWidth, barHeight, 1.0f, 0.0f, 0.0f, 0.8f); // 红色
        }

        // 无敌状态指示
        if (invulnerable != null && invulnerable) {
            renderer.drawRect(barX, barY, barWidth, barHeight, 0.5f, 0.5f, 1.0f, 0.5f); // 半透明蓝色边框
        }
    }

    // 新增：渲染魔力条
    private void renderManaBar(Vector2 position, float manaPercent, IRenderer renderer) {
        float barWidth = 40f;
        float barHeight = 3f;
        float barYOffset = -30f; // 魔力条在血条下方

        float barX = position.x - barWidth / 2;
        float barY = position.y + barYOffset;

        // 魔力条背景
        renderer.drawRect(barX, barY, barWidth, barHeight, 0.2f, 0.2f, 0.3f, 0.8f);

        // 当前魔力
        float manaWidth = barWidth * manaPercent;
        renderer.drawRect(barX, barY, manaWidth, barHeight, 0.0f, 0.5f, 1.0f, 0.8f); // 蓝色
    }

    // 新增：渲染技能状态
    private void renderSkillStatus(Vector2 position, Map<String, Keyframe.EntityInfo.SkillStateInfo> skillStates, IRenderer renderer) {
        float iconSize = 16f;
        float spacing = 24f;
        float startY = position.y + 105f; // 技能图标在魔力条下方

        SkillComponent.SkillType[] skillTypes = {
                SkillComponent.SkillType.MELEE_ATTACK,
                SkillComponent.SkillType.RANGED_ATTACK,
                SkillComponent.SkillType.DODGE
        };

        String[] skillLabels = {"Z", "X", "C"};
        RenderComponent.Color[] skillColors = {
                new RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f), // 红色 - 近战
                new RenderComponent.Color(0.0f, 1.0f, 1.0f, 1.0f), // 青色 - 远程
                new RenderComponent.Color(0.0f, 1.0f, 0.0f, 1.0f)  // 绿色 - 闪避
        };

        for (int i = 0; i < skillTypes.length; i++) {
            String skillType = skillTypes[i].name();
            Keyframe.EntityInfo.SkillStateInfo skillState = skillStates.get(skillType);
            if (skillState == null) continue;

            float iconX = position.x - (spacing * skillTypes.length / 2) + i * spacing;
            float iconY = startY;

            // 技能图标背景
            boolean isReady = "READY".equals(skillState.state);
            RenderComponent.Color bgColor = isReady ?
                    new RenderComponent.Color(0.3f, 0.3f, 0.5f, 1.0f) :
                    new RenderComponent.Color(0.5f, 0.2f, 0.2f, 1.0f);

            renderer.drawRect(iconX, iconY, iconSize, iconSize,
                    bgColor.r, bgColor.g, bgColor.b, bgColor.a);

            // 技能图标
            renderer.drawRect(iconX + 1, iconY + 1, iconSize - 2, iconSize - 2,
                    skillColors[i].r, skillColors[i].g, skillColors[i].b, skillColors[i].a);

            // 冷却显示
            if (skillState.cooldown > 0 && skillState.totalCooldown > 0) {
                float progress = skillState.cooldown / skillState.totalCooldown;
                renderer.drawRect(iconX, iconY, iconSize, iconSize * progress,
                        0.0f, 0.0f, 0.0f, 0.7f);
            }

            // 技能标签
            renderer.drawText(iconX + 1, iconY + 1, skillLabels[i],
                    1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // 修改 render 方法，调用状态渲染
    @Override
    public void render() {
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.06f, 0.06f, 0.08f, 1.0f);

        if (recordingPath == null) {
            renderFileList();
            return;
        }

        super.render();
        renderMousePosition();
        renderReplayHud(); // 新增：渲染状态UI

        String hint = "REPLAY: ESC to return";
        float w = hint.length() * 12.0f;
        renderer.drawText(renderer.getWidth()/2.0f - w/2.0f, 30, hint, 0.8f, 0.8f, 0.8f, 1.0f);
    }


    // ========== 文件列表模式 ==========
    private List<File> recordingFiles;
    private int selectedIndex = 0;

    private void ensureFilesListed() {
        if (recordingFiles != null) return;
        com.gameengine.recording.RecordingStorage storage = new com.gameengine.recording.FileRecordingStorage();
        recordingFiles = storage.listRecordings();
    }

    private void handleFileSelection() {
        ensureFilesListed();
        if (input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_UP_ARROW)) {
            selectedIndex = (selectedIndex - 1 + Math.max(1, recordingFiles.size())) % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_DOWN_ARROW)) {
            selectedIndex = (selectedIndex + 1) % Math.max(1, recordingFiles.size());
        } else if (input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_ENTER)
                || input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_SPACE)) {
            if (recordingFiles.size() > 0) {
                String path = recordingFiles.get(selectedIndex).getAbsolutePath();
                this.recordingPath = path;
                this.recordingFiles = null;
                clear();
                initialize();            }
        } else if (input.isKeyJustPressed(com.gameengine.config.GameConfig.KEY_ESCAPE)) {
            engine.setScene(new MenuScene(engine, "MainMenu"));
        }
    }

    private void renderFileList() {
        ensureFilesListed();
        int w = renderer.getWidth();
        int h = renderer.getHeight();
        String title = "SELECT RECORDING";
        float tw = title.length() * 16f;
        renderer.drawText(w/2f - tw/2f, 80, title, 1f,1f,1f,1f);

        if (recordingFiles.isEmpty()) {
            String none = "NO RECORDINGS FOUND";
            float nw = none.length() * 14f;
            renderer.drawText(w/2f - nw/2f, h/2f, none, 0.9f,0.8f,0.2f,1f);
            String back = "ESC TO RETURN";
            float bw = back.length() * 12f;
            renderer.drawText(w/2f - bw/2f, h - 60, back, 0.7f,0.7f,0.7f,1f);
            return;
        }

        float startY = 140f;
        float itemH = 28f;
        for (int i = 0; i < recordingFiles.size(); i++) {
            String name = recordingFiles.get(i).getName();
            float x = 100f;
            float y = startY + i * itemH;
            if (i == selectedIndex) {
                renderer.drawRect(x - 10, y - 6, 600, 24, 0.3f,0.3f,0.4f,0.8f);
            }
            renderer.drawText(x, y, name, 0.9f,0.9f,0.9f,1f);
        }

        String hint = "UP/DOWN SELECT, ENTER PLAY, ESC RETURN";
        float hw = hint.length() * 12f;
        renderer.drawText(w/2f - hw/2f, h - 60, hint, 0.7f,0.7f,0.7f,1f);
    }

    // 解析相关逻辑已移至 RecordingJson
}
