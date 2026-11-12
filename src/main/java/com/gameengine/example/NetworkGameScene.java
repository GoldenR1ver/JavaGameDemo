package com.gameengine.example;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.input.InputManager;
import com.gameengine.input.PlayerSkillController;
import com.gameengine.math.Vector2;
import com.gameengine.net.GameClient;
import com.gameengine.net.GameServer;
import com.gameengine.net.GameStateCodec;
import com.gameengine.net.PlayerCommand;
import com.gameengine.net.Protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 联机场景：服务器权威 + 客户端表现/预测。
 *
 * 重构要点：
 * 1. 网络收发与游戏逻辑分离：GameServer/GameClient 只交换事件与字符串快照，
 *    场景在游戏线程消费事件、生成快照，彻底消除跨线程遍历 Scene 的数据竞争。
 * 2. 服务器与客户端共用 GameScene 的世界创建/渲染，不再各写一份匿名玩家类。
 * 3. 客户端只维护"远端实体 + 本地玩家"两类对象：
 *    - 远端实体由快照创建/更新/销毁，位置做平滑插值；
 *    - 本地玩家做输入预测，并用服务器位置做渐进纠偏。
 */
public class NetworkGameScene extends GameScene {

    public enum NetworkMode { SERVER, CLIENT, STANDALONE }

    private final NetworkMode mode;
    private final String host;
    private final int port;

    // ---------------- 客户端状态 ----------------
    private GameClient client;
    private String sessionId;
    private GameObject localPlayer;
    private PlayerSkillController localSkillController;
    private final Map<Long, GameObject> remoteEntities = new HashMap<>();
    private final Map<Long, Vector2> remoteTargets = new HashMap<>();
    private long inputSequence;
    private float inputSendAccumulator;
    private boolean forceInputSend;
    private boolean loggedFirstSnapshot;

    // ---------------- 服务器状态 ----------------
    private GameServer server;
    private final Map<String, GameObject> sessionPlayers = new HashMap<>();
    private float broadcastAccumulator;

    private String statusText = "INIT";
    private final java.util.Random spawnRandom = new java.util.Random();

    public NetworkGameScene(GameEngine engine, NetworkMode mode) {
        this(engine, mode, Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public NetworkGameScene(GameEngine engine, NetworkMode mode, String host, int port) {
        super(engine);
        this.mode = mode;
        this.host = host;
        this.port = port;
    }

    @Override
    protected boolean shouldCreateDefaultWorld() {
        // 客户端的世界完全由服务器快照驱动，不需要本地 AI
        return mode != NetworkMode.CLIENT;
    }

    @Override
    public void initialize() {
        super.initialize();

        if (mode == NetworkMode.CLIENT) {
            initializeClient();
        } else if (mode == NetworkMode.SERVER) {
            initializeServer();
        } else {
            statusText = "STANDALONE";
        }
    }

    // ==================== 客户端 ====================

    private void initializeClient() {
        if (getRenderer() == null) return;

        localPlayer = createPlayerEntity("LocalPlayer",
                getRenderer().getWidth() / 2.0f, getRenderer().getHeight() / 2.0f);
        if (localPlayer != null) {
            addGameObject(localPlayer);
            localSkillController = new PlayerSkillController(localPlayer);
        }

        client = new GameClient();
        statusText = "CONNECTING";
        boolean ok = client.connectAndJoin(host, port, "Player", 3000);
        if (!ok) {
            System.err.println("[Client] 连接失败，回退到单机模式: " + client.getLastError());
            fallbackToStandalone();
            return;
        }

        sessionId = client.getSessionId();
        statusText = "CONNECTED";
        System.out.println("[Client] 会话: " + sessionId);
    }

    private void fallbackToStandalone() {
        if (client != null) {
            client.close();
            client = null;
        }
        statusText = "OFFLINE (standalone)";
        // 连接失败时至少还能玩：创建完整的单机世界
        localPlayer = null;
        localSkillController = null;
        createWorld();
    }

    private void updateClient(float deltaTime) {
        if (client == null || !client.isConnected()) {
            if (client != null && !"OFFLINE".equals(statusText)) {
                System.err.println("[Client] 与服务器断开连接，切换到单机模式");
                enterOfflineMode();
            }
            super.update(deltaTime);
            return;
        }

        updateClientInput(deltaTime);
        applyLatestSnapshot();
        smoothRemoteEntities(deltaTime);

        // 只更新本地对象/组件的表现层，不跑 AI 与游戏逻辑
        updateGameObjects(deltaTime);
    }

    /** 运行中掉线：清掉网络实体，重建一个可玩的单机世界 */
    private void enterOfflineMode() {
        if (client != null) {
            client.close();
            client = null;
        }
        for (GameObject obj : remoteEntities.values()) {
            obj.setActive(false);
            removeGameObject(obj);
        }
        remoteEntities.clear();
        remoteTargets.clear();

        if (localPlayer != null) {
            localPlayer.setActive(false);
            removeGameObject(localPlayer);
            localPlayer = null;
            localSkillController = null;
        }

        statusText = "OFFLINE";
        createWorld();    }

    private void updateClientInput(float deltaTime) {
        if (localPlayer == null) return;

        InputManager input = getInputManager();
        float dx = 0, dy = 0;
        if (input.isKeyPressed(GameConfig.KEY_LEFT)) dx -= 1;
        if (input.isKeyPressed(GameConfig.KEY_RIGHT)) dx += 1;
        if (input.isKeyPressed(GameConfig.KEY_UP)) dy -= 1;
        if (input.isKeyPressed(GameConfig.KEY_DOWN)) dy += 1;

        Vector2 move = new Vector2(dx, dy);
        PhysicsComponent physics = localPlayer.getComponent(PhysicsComponent.class);
        if (physics != null) {
            if (move.magnitude() > 0) {
                physics.setVelocity(move.normalize().multiply(GameConfig.PLAYER_MOVE_SPEED));
            } else {
                physics.setVelocity(new Vector2());
            }
        }

        // 客户端技能预测：本地立即播放动画/冷却，服务器保持权威
        if (localSkillController != null) {
            localSkillController.update();
        }

        boolean dodge = input.isKeyJustPressed(GameConfig.KEY_DODGE);
        if (dodge) {
            forceInputSend = true;
        }

        inputSendAccumulator += deltaTime;
        if (inputSendAccumulator < GameConfig.NETWORK_INPUT_INTERVAL && !forceInputSend) {
            return;
        }
        inputSendAccumulator = 0f;
        forceInputSend = false;

        Vector2 aim = input.getMousePosition();
        PlayerCommand command = new PlayerCommand(
                inputSequence++,
                dx, dy,
                input.isKeyPressed(GameConfig.KEY_MELEE),
                input.isKeyPressed(GameConfig.KEY_RANGED),
                dodge,
                aim.x, aim.y);
        client.sendInput(command);
    }

    private void applyLatestSnapshot() {
        String json = client.pollState();
        if (json == null) return;

        GameStateCodec.Snapshot snapshot = GameStateCodec.decode(json);
        if (snapshot == null) return;

        if (!loggedFirstSnapshot) {
            loggedFirstSnapshot = true;
            System.out.println("[Client] 收到首个状态快照，实体数: " + snapshot.entities.size());
        }

        Set<Long> seen = new HashSet<>();
        for (GameStateCodec.Entity entity : snapshot.entities.values()) {
            // 自己：不创建远端替身，只用服务器位置做纠偏
            if (entity.owner != null && entity.owner.equals(sessionId)) {
                correctLocalPlayer(entity);
                continue;
            }

            seen.add(entity.id);
            GameObject obj = remoteEntities.get(entity.id);
            if (obj == null) {
                obj = createRemoteEntity(entity);
                if (obj != null) {
                    remoteEntities.put(entity.id, obj);
                    addGameObject(obj);
                    // 新实体直接落到目标位置，避免从 (0,0) 飞过来
                    TransformComponent transform = obj.getComponent(TransformComponent.class);
                    if (transform != null) {
                        transform.setPosition(new Vector2(entity.x, entity.y));
                    }
                }
            }
            if (obj != null) {
                remoteTargets.put(entity.id, new Vector2(entity.x, entity.y));
            }
        }

        // 移除本帧已不存在的远端实体
        Iterator<Map.Entry<Long, GameObject>> iterator = remoteEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, GameObject> entry = iterator.next();
            if (!seen.contains(entry.getKey())) {
                GameObject obj = entry.getValue();
                obj.setActive(false);
                removeGameObject(obj);
                remoteTargets.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    /** 用服务器位置平滑纠偏本地预测 */
    private void correctLocalPlayer(GameStateCodec.Entity entity) {
        if (localPlayer == null) return;

        TransformComponent transform = localPlayer.getComponent(TransformComponent.class);
        if (transform != null) {
            Vector2 current = transform.getPosition();
            Vector2 serverPos = new Vector2(entity.x, entity.y);
            float distance = current.distance(serverPos);

            if (distance > 300f) {
                // 偏差过大（如网络卡顿/传送）：直接瞬移，避免长时间拉扯
                transform.setPosition(serverPos);
            } else if (distance > 30f) {
                transform.setPosition(current.add(serverPos.subtract(current).multiply(0.2f)));
            }
        }

        HealthComponent health = localPlayer.getComponent(HealthComponent.class);
        if (health != null && entity.health >= 0 && entity.maxHealth > 0) {
            health.setCurrentHealth(entity.health);
            health.setMaxHealth(entity.maxHealth);
        }

        SkillComponent skill = localPlayer.getComponent(SkillComponent.class);
        if (skill != null && entity.mana >= 0 && entity.maxMana > 0) {
            skill.setMaxMana(entity.maxMana);
            skill.setCurrentMana(entity.mana);
        }
    }

    private GameObject createRemoteEntity(GameStateCodec.Entity entity) {
        if (getRenderer() == null) return null;

        if (entity.isAvatar()) {
            return EntityFactory.createRemotePlayer(entity.name, 0.0f, 0.9f, 1.0f);
        }

        float w = entity.width > 0 ? entity.width : 14f;
        float h = entity.height > 0 ? entity.height : 14f;
        float r = entity.color != null ? entity.color[0] : 0.9f;
        float g = entity.color != null ? entity.color[1] : 0.9f;
        float b = entity.color != null ? entity.color[2] : 0.2f;
        float a = entity.color != null ? entity.color[3] : 1.0f;

        RenderComponent.RenderType type = "CIRCLE".equals(entity.renderType)
                ? RenderComponent.RenderType.CIRCLE
                : RenderComponent.RenderType.RECTANGLE;

        return EntityFactory.createRenderable(entity.name, getRenderer(), type, w, h, r, g, b, a);
    }

    private void smoothRemoteEntities(float deltaTime) {
        // 与帧率无关的插值系数
        float factor = 1f - (float) Math.pow(1f - GameConfig.NETWORK_SMOOTHING, deltaTime * 60f);
        for (Map.Entry<Long, GameObject> entry : remoteEntities.entrySet()) {
            Vector2 target = remoteTargets.get(entry.getKey());
            if (target == null) continue;
            TransformComponent transform = entry.getValue().getComponent(TransformComponent.class);
            if (transform == null) continue;
            Vector2 current = transform.getPosition();
            transform.setPosition(current.add(target.subtract(current).multiply(factor)));
        }
    }

    // ==================== 服务器 ====================

    private void initializeServer() {
        server = new GameServer(port);
        if (!server.start()) {
            statusText = "SERVER FAILED";
            return;
        }
        statusText = "SERVER :" + port;

        // 服务器本地玩家（键盘控制）标记为 local，客户端不会误认为是自己
        GameObject serverPlayer = getHudPlayer();
        if (serverPlayer != null) {
            serverPlayer.setUserData("owner", "local");
        }
        System.out.println("[Server] 场景初始化完成，等待客户端加入");
    }

    private void updateServer(float deltaTime) {
        // 服务器权威：跑完整的玩法逻辑
        super.update(deltaTime);

        if (server != null) {
            processServerEvents();
            broadcastAccumulator += deltaTime;
            if (broadcastAccumulator >= GameConfig.NETWORK_STATE_INTERVAL) {
                broadcastAccumulator = 0f;
                String json = GameStateCodec.encode(this, System.currentTimeMillis());
                server.broadcastState(json);
            }
        }
    }

    private void processServerEvents() {
        List<GameServer.Event> events = server.pollEvents();
        if (events.isEmpty()) return;

        for (GameServer.Event event : events) {
            switch (event.type) {
                case JOIN:
                    handleJoin(event.sessionId);
                    break;
                case LEFT:
                    handleLeft(event.sessionId);
                    break;
                case INPUT:
                    handleInput(event.sessionId, event.command);
                    break;
            }
        }
    }

    private void handleJoin(String sid) {
        if (sessionPlayers.containsKey(sid)) return;

        float width = getRenderer() != null ? getRenderer().getWidth() : GameConfig.WINDOW_WIDTH;
        float height = getRenderer() != null ? getRenderer().getHeight() : GameConfig.WINDOW_HEIGHT;
        // 在屏幕中部区域随机出生，避免多个客户端叠在同一个点
        float spawnX = width * (0.25f + spawnRandom.nextFloat() * 0.5f);
        float spawnY = height * (0.25f + spawnRandom.nextFloat() * 0.5f);

        GameObject player = createPlayerEntity("NetworkPlayer_" + sid, spawnX, spawnY);
        if (player == null) return;

        player.setUserData("owner", sid);
        addGameObject(player);
        sessionPlayers.put(sid, player);
        System.out.println("[Server] 创建远端玩家: " + sid);
    }

    private void handleLeft(String sid) {
        GameObject player = sessionPlayers.remove(sid);
        if (player != null) {
            player.setActive(false);
            removeGameObject(player);
            System.out.println("[Server] 移除远端玩家: " + sid);
        }
    }

    private void handleInput(String sid, PlayerCommand command) {
        GameObject player = sessionPlayers.get(sid);
        // 玩家死亡（HealthComponent 会 destroy 对象）后自动重生
        if (player == null || !player.isActive()) {
            sessionPlayers.remove(sid);
            handleJoin(sid);
            player = sessionPlayers.get(sid);
            if (player == null) return;
        }

        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);
        if (physics != null) {
            if (command.isMoving()) {
                Vector2 direction = new Vector2(command.moveX, command.moveY);
                physics.setVelocity(direction.normalize().multiply(GameConfig.PLAYER_MOVE_SPEED));
            } else {
                physics.setVelocity(new Vector2());
            }
        }

        SkillComponent skill = player.getComponent(SkillComponent.class);
        if (skill == null) return;

        TransformComponent transform = player.getComponent(TransformComponent.class);
        Vector2 aim = new Vector2(command.aimX, command.aimY);
        if (transform != null && aim.distance(transform.getPosition()) < 1e-3f) {
            aim = transform.getPosition().add(new Vector2(1, 0));
        }

        if (command.melee) {
            skill.press(SkillComponent.SkillType.MELEE_ATTACK, aim);
        } else {
            skill.release(SkillComponent.SkillType.MELEE_ATTACK, aim);
        }

        if (command.ranged) {
            skill.press(SkillComponent.SkillType.RANGED_ATTACK, aim);
        } else {
            skill.release(SkillComponent.SkillType.RANGED_ATTACK, aim);
        }

        if (command.dodge) {
            skill.press(SkillComponent.SkillType.DODGE, aim);
        }
    }

    // ==================== 更新 / 渲染 ====================

    @Override
    public void update(float deltaTime) {
        if (mode == NetworkMode.CLIENT) {
            if (handleEscapeToMenu()) return;
            updateClient(deltaTime);
        } else if (mode == NetworkMode.SERVER) {
            updateServer(deltaTime);
        } else {
            super.update(deltaTime);
        }
    }

    @Override
    protected GameObject getHudPlayer() {
        if (mode == NetworkMode.CLIENT && localPlayer != null) {
            return localPlayer;
        }
        return super.getHudPlayer();
    }

    @Override
    public void render() {
        super.render();
        renderNetworkInfo();
    }

    private void renderNetworkInfo() {
        if (getRenderer() == null) return;
        String line1 = "MODE: " + mode.name() + "   " + statusText;
        getRenderer().drawText(20, 60, line1, 0.8f, 0.9f, 1.0f, 1.0f);

        String line2;
        if (mode == NetworkMode.CLIENT) {
            line2 = "REMOTE ENTITIES: " + remoteEntities.size()
                    + (client != null && client.isConnected() ? "   ONLINE" : "   OFFLINE");
        } else if (mode == NetworkMode.SERVER) {
            line2 = "CLIENTS: " + (server != null ? server.getClientCount() : 0);
        } else {
            line2 = "";
        }
        if (!line2.isEmpty()) {
            getRenderer().drawText(20, 80, line2, 0.8f, 0.9f, 1.0f, 1.0f);
        }
    }

    @Override
    public void clear() {
        if (client != null) {
            client.close();
            client = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        remoteEntities.clear();
        remoteTargets.clear();
        sessionPlayers.clear();
        localPlayer = null;
        localSkillController = null;
        super.clear();
    }
}
