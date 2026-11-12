package com.gameengine.net;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏状态编解码器（服务器权威）。
 *
 * 设计要点：
 * 1. 纯函数、无静态场景引用：调用方显式传入 Scene，避免"全静态状态"导致的耦合与竞态。
 * 2. 只在游戏线程调用 encode（快照在游戏线程生成，网络线程只负责转发字符串）。
 * 3. 实体用 GameObject 的全局 id 标识，客户端据此创建/复用/销毁远端实体。
 */
public final class GameStateCodec {
    private GameStateCodec() {}

    private static final int MAX_ENTITIES = 512;

    /**
     * 将场景编码为全量状态 JSON。
     */
    public static String encode(Scene scene, long timeMillis) {
        if (scene == null) return null;
        JSONArray entities = new JSONArray();
        List<GameObject> objects = scene.getGameObjects();

        for (GameObject obj : objects) {
            if (!obj.isActive()) continue;
            if (!isSyncable(obj.getName())) continue;
            TransformComponent transform = obj.getComponent(TransformComponent.class);
            if (transform == null) continue;

            JSONObject e = new JSONObject();
            e.put("id", obj.getId());
            e.put("n", obj.getName());
            Object owner = obj.getUserData("owner");
            if (owner != null) e.put("o", owner.toString());

            Vector2 pos = transform.getPosition();
            e.put("x", round(pos.x));
            e.put("y", round(pos.y));

            PhysicsComponent physics = obj.getComponent(PhysicsComponent.class);
            if (physics != null) {
                Vector2 v = physics.getVelocity();
                e.put("vx", round(v.x));
                e.put("vy", round(v.y));
            }

            HealthComponent health = obj.getComponent(HealthComponent.class);
            if (health != null) {
                e.put("hp", round(health.getCurrentHealth()));
                e.put("mhp", round(health.getMaxHealth()));
                if (health.isInvulnerable()) e.put("inv", true);
            }

            SkillComponent skill = obj.getComponent(SkillComponent.class);
            if (skill != null) {
                e.put("mp", round(skill.getCurrentMana()));
                e.put("mmp", round(skill.getMaxMana()));
            }

            RenderComponent render = obj.getComponent(RenderComponent.class);
            if (render != null) {
                e.put("rt", render.getRenderType().name());
                Vector2 size = render.getSize();
                e.put("w", round(size.x));
                e.put("h", round(size.y));
                RenderComponent.Color color = render.getColor();
                JSONArray c = new JSONArray();
                c.put(round(color.r)).put(round(color.g)).put(round(color.b)).put(round(color.a));
                e.put("c", c);
            } else if ("WeaponDrop".equals(obj.getName())) {
                e.put("rt", "RECTANGLE");
                e.put("w", 20);
                e.put("h", 20);
                e.put("c", colorOf(1.0f, 1.0f, 0.0f, 1.0f));
            } else if (obj.getName().startsWith("Bullet")) {
                e.put("rt", "RECTANGLE");
                e.put("w", 6);
                e.put("h", 6);
                e.put("c", colorOf(0.0f, 1.0f, 1.0f, 1.0f));
            } else {
                // 玩家/AI 使用自定义人形渲染
                e.put("rt", "AVATAR");
            }

            entities.put(e);
        }

        JSONObject root = new JSONObject();
        root.put("type", "state");
        root.put("t", timeMillis);
        root.put("entities", entities);
        return root.toString();
    }

    /**
     * 解析状态 JSON。任何非法/缺失字段都被忽略，不会抛异常打断客户端主循环。
     */
    public static Snapshot decode(String json) {
        if (json == null || json.isEmpty()) return null;
        Snapshot snapshot = new Snapshot();
        try {
            JSONObject root = new JSONObject(json);
            if (!"state".equals(root.optString("type"))) return null;
            snapshot.time = root.optLong("t", System.currentTimeMillis());

            JSONArray array = root.optJSONArray("entities");
            if (array == null) return snapshot;

            int count = Math.min(array.length(), MAX_ENTITIES);
            for (int i = 0; i < count; i++) {
                JSONObject e = array.optJSONObject(i);
                if (e == null) continue;
                long id = e.optLong("id", -1L);
                if (id < 0) continue;

                Entity entity = new Entity();
                entity.id = id;
                entity.name = e.optString("n", "Entity");
                entity.owner = e.optString("o", null);
                entity.x = (float) e.optDouble("x", 0);
                entity.y = (float) e.optDouble("y", 0);
                entity.vx = (float) e.optDouble("vx", 0);
                entity.vy = (float) e.optDouble("vy", 0);
                entity.health = (float) e.optDouble("hp", -1);
                entity.maxHealth = (float) e.optDouble("mhp", -1);
                entity.invulnerable = e.optBoolean("inv", false);
                entity.mana = (float) e.optDouble("mp", -1);
                entity.maxMana = (float) e.optDouble("mmp", -1);
                entity.renderType = e.optString("rt", "AVATAR");
                entity.width = (float) e.optDouble("w", 0);
                entity.height = (float) e.optDouble("h", 0);
                entity.color = new float[]{0.9f, 0.9f, 0.9f, 1f};
                JSONArray color = e.optJSONArray("c");
                if (color != null && color.length() >= 4) {
                    entity.color[0] = (float) color.optDouble(0, 0.9);
                    entity.color[1] = (float) color.optDouble(1, 0.9);
                    entity.color[2] = (float) color.optDouble(2, 0.9);
                    entity.color[3] = (float) color.optDouble(3, 1.0);
                }
                snapshot.entities.put(id, entity);
            }
        } catch (Exception ex) {
            return null;
        }
        return snapshot;
    }

    /** 是否需要同步（白名单，避免把装饰物/粒子对象写进网络包） */
    private static boolean isSyncable(String name) {
        if (name == null) return false;
        return "Player".equals(name)
                || "AIPlayer".equals(name)
                || "WeaponDrop".equals(name)
                || name.startsWith("NetworkPlayer")
                || name.startsWith("Bullet")
                || name.startsWith("SkillEffect");
    }

    private static double round(float v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static JSONArray colorOf(float r, float g, float b, float a) {
        JSONArray c = new JSONArray();
        c.put(round(r)).put(round(g)).put(round(b)).put(round(a));
        return c;
    }

    /** 一帧服务器状态 */
    public static final class Snapshot {
        public long time;
        public final Map<Long, Entity> entities = new HashMap<>();
    }

    /** 单个实体状态 */
    public static final class Entity {
        public long id;
        public String name;
        public String owner;
        public float x, y;
        public float vx, vy;
        public float health = -1;
        public float maxHealth = -1;
        public boolean invulnerable;
        public float mana = -1;
        public float maxMana = -1;
        public String renderType = "AVATAR";
        public float width, height;
        public float[] color;

        public boolean isAvatar() {
            return "AVATAR".equals(renderType);
        }
    }
}
