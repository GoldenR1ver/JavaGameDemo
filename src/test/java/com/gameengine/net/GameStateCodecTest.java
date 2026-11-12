package com.gameengine.net;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameObject;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态快照编解码测试：验证服务器生成的实体 ID/位置/血量能被客户端正确还原。
 */
class GameStateCodecTest {

    @Test
    void snapshotShouldRoundTrip() {
        Scene scene = new Scene("test");
        GameObject player = new GameObject("Player");
        player.addComponent(new TransformComponent(new Vector2(100f, 200f)));
        player.addComponent(new PhysicsComponent(1f));
        player.addComponent(new HealthComponent(80f));
        player.getComponent(HealthComponent.class).takeDamage(20f);
        scene.addGameObject(player);
        scene.update(0f); // 处理 objectsToAdd

        String json = GameStateCodec.encode(scene, 123456L);
        assertNotNull(json);

        GameStateCodec.Snapshot snapshot = GameStateCodec.decode(json);
        assertNotNull(snapshot);
        assertEquals(1, snapshot.entities.size());

        GameStateCodec.Entity entity = snapshot.entities.get(player.getId());
        assertNotNull(entity, "实体必须用 GameObject 的稳定 id 关联");
        assertEquals("Player", entity.name);
        assertEquals(100f, entity.x, 0.1f);
        assertEquals(200f, entity.y, 0.1f);
        assertEquals(60f, entity.health, 0.1f);
        assertEquals(80f, entity.maxHealth, 0.1f);
    }

    @Test
    void decodeShouldRejectGarbage() {
        assertNull(GameStateCodec.decode(null));
        assertNull(GameStateCodec.decode("not json"));
        assertNull(GameStateCodec.decode("{\"type\":\"other\"}"));
    }

    @Test
    void encodeShouldIgnoreObjectsOutsideWhitelist() {
        Scene scene = new Scene("test");
        GameObject decoration = new GameObject("Decoration");
        decoration.addComponent(new TransformComponent(new Vector2(1, 1)));
        scene.addGameObject(decoration);
        scene.update(0f);

        GameStateCodec.Snapshot snapshot = GameStateCodec.decode(GameStateCodec.encode(scene, 0));
        assertNotNull(snapshot);
        assertTrue(snapshot.entities.isEmpty());
    }
}
