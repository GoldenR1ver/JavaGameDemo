package com.gameengine.core;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.config.GameConfig;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 游戏逻辑系统：处理玩家输入、AI 行为、碰撞检测。
 *
 * 说明：这里只处理"移动与碰撞"这类通用逻辑；技能输入由 PlayerController 负责，
 * 因此 GameLogic 不再依赖 GameEngine，也不再自行创建线程池（避免线程泄漏）。
 */
public class GameLogic {
    private final Scene scene;
    private final InputManager inputManager;
    private final Random random;
    private boolean gameOver;

    public GameLogic(Scene scene) {
        this.scene = scene;
        this.inputManager = InputManager.getInstance();
        this.random = new Random();
        this.gameOver = false;
    }

    /** 兼容旧调用：本类已不持有线程资源，保留空实现 */
    public void cleanup() {
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public GameObject getUserPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.getName().equals("Player") && obj.hasComponent(PhysicsComponent.class)) {
                return obj;
            }
        }
        return null;
    }

    /**
     * 所有"玩家类"实体：本机玩家 + 服务器上的远端玩家。
     * 客户端场景中的远端替身没有 PhysicsComponent，会被自动过滤掉。
     */
    public List<GameObject> getAllPlayers() {
        List<GameObject> players = new ArrayList<>();
        for (GameObject obj : scene.getGameObjects()) {
            if (!obj.isActive() || !obj.hasComponent(PhysicsComponent.class)) continue;
            String name = obj.getName();
            if ("Player".equals(name) || "LocalPlayer".equals(name) || name.startsWith("NetworkPlayer")) {
                players.add(obj);
            }
        }
        return players;
    }

    public List<GameObject> getAIPlayers() {
        return scene.getGameObjects().stream()
                .filter(obj -> obj.getName().equals("AIPlayer"))
                .filter(GameObject::isActive)
                .collect(Collectors.toList());
    }

    // 处理玩家移动
    public void handlePlayerInput(float deltaTime) {
        if (gameOver) return;

        GameObject player = getUserPlayer();
        if (player == null) return;

        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);

        if (transform == null || physics == null) return;

        Vector2 movement = new Vector2();

        if (inputManager.isKeyPressed(GameConfig.KEY_UP) || inputManager.isKeyPressed(GameConfig.KEY_UP_ARROW)) {
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(GameConfig.KEY_DOWN) || inputManager.isKeyPressed(GameConfig.KEY_DOWN_ARROW)) {
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(GameConfig.KEY_LEFT) || inputManager.isKeyPressed(GameConfig.KEY_LEFT_ARROW)) {
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(GameConfig.KEY_RIGHT) || inputManager.isKeyPressed(GameConfig.KEY_RIGHT_ARROW)) {
            movement.x += 1;
        }

        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(GameConfig.PLAYER_MOVE_SPEED);
            physics.setVelocity(movement);
        } else {
            // 松开方向键后立即停下，避免靠摩擦滑行导致操作发飘
            physics.setVelocity(new Vector2());
        }

        clampToScreen(transform);
    }

    private void clampToScreen(TransformComponent transform) {
        Vector2 pos = transform.getPosition();
        int screenW = getScreenWidth();
        int screenH = getScreenHeight();
        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.x > screenW - 20) pos.x = screenW - 20;
        if (pos.y > screenH - 20) pos.y = screenH - 20;
        transform.setPosition(pos);
    }

    private int getScreenWidth() {
        return scene.getRenderer() != null ? scene.getRenderer().getWidth() : GameConfig.WINDOW_WIDTH;
    }

    private int getScreenHeight() {
        return scene.getRenderer() != null ? scene.getRenderer().getHeight() : GameConfig.WINDOW_HEIGHT;
    }

    // 处理AI移动：朝向最近的玩家移动 + 避障逻辑
    public void handleAIPlayerMovement(float deltaTime) {
        if (gameOver) return;

        List<GameObject> aiPlayers = getAIPlayers();
        List<GameObject> targets = getAllPlayers();
        if (targets.isEmpty()) return;

        for (GameObject aiPlayer : aiPlayers) {
            PhysicsComponent physics = aiPlayer.getComponent(PhysicsComponent.class);
            TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);

            if (physics == null || aiTransform == null) continue;

            Vector2 aiPos = aiTransform.getPosition();

            // 选择最近的玩家作为目标（多人联机时 AI 不会只追主机）
            Vector2 playerPos = null;
            float bestDistance = Float.MAX_VALUE;
            for (GameObject target : targets) {
                TransformComponent targetTransform = target.getComponent(TransformComponent.class);
                if (targetTransform == null) continue;
                float distance = aiPos.distance(targetTransform.getPosition());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    playerPos = targetTransform.getPosition();
                }
            }
            if (playerPos == null) continue;

            Vector2 directionToPlayer = playerPos.subtract(aiPos);

            if (directionToPlayer.magnitude() < 10f) {
                physics.setVelocity(new Vector2(0, 0));
                continue;
            }

            Vector2 targetVelocity = directionToPlayer.normalize().multiply(GameConfig.AI_MOVE_SPEED);

            Vector2 currentVelocity = physics.getVelocity();
            float lerpFactor = 0.1f;
            Vector2 newVelocity = new Vector2(
                    currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                    currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
            );

            if (newVelocity.magnitude() > GameConfig.AI_MOVE_SPEED) {
                newVelocity = newVelocity.normalize().multiply(GameConfig.AI_MOVE_SPEED);
            }

            physics.setVelocity(newVelocity);
        }
    }

    /**
     * 处理 AI 避障。
     *
     * 原实现用线程池并发的读写同一批 TransformComponent，与物理线程构成数据竞争；
     * 这里的 O(n²) 计算量对 40 个 AI 完全可以忽略，改为单线程以保证确定性。
     */
    public void handleAIPlayerAvoidance(float deltaTime) {
        if (gameOver) return;

        List<GameObject> aiPlayers = getAIPlayers();
        if (aiPlayers.isEmpty()) return;

        float radius = GameConfig.AI_AVOID_RADIUS;
        for (int i = 0; i < aiPlayers.size(); i++) {
            GameObject aiPlayer1 = aiPlayers.get(i);
            TransformComponent transform1 = aiPlayer1.getComponent(TransformComponent.class);
            PhysicsComponent physics1 = aiPlayer1.getComponent(PhysicsComponent.class);
            if (transform1 == null || physics1 == null) continue;

            Vector2 pos1 = transform1.getPosition();
            Vector2 avoidance = new Vector2();

            for (int j = i + 1; j < aiPlayers.size(); j++) {
                TransformComponent transform2 = aiPlayers.get(j).getComponent(TransformComponent.class);
                if (transform2 == null) continue;

                Vector2 pos2 = transform2.getPosition();
                float distance = pos1.distance(pos2);

                if (distance < radius && distance > 0) {
                    Vector2 direction = pos1.subtract(pos2).normalize();
                    float strength = (radius - distance) / radius;
                    avoidance = avoidance.add(direction.multiply(strength * 50));
                }
            }

            if (avoidance.magnitude() > 0) {
                Vector2 currentVelocity = physics1.getVelocity();
                float lerpFactor = 0.15f;
                Vector2 avoidanceDirection = avoidance.normalize();
                float avoidanceStrength = Math.min(avoidance.magnitude(), 50f);

                Vector2 targetVelocity = currentVelocity.add(
                        avoidanceDirection.multiply(avoidanceStrength * deltaTime * 10)
                );

                Vector2 newVelocity = new Vector2(
                        currentVelocity.x + (targetVelocity.x - currentVelocity.x) * lerpFactor,
                        currentVelocity.y + (targetVelocity.y - currentVelocity.y) * lerpFactor
                );

                if (newVelocity.magnitude() > GameConfig.AI_MOVE_SPEED) {
                    newVelocity = newVelocity.normalize().multiply(GameConfig.AI_MOVE_SPEED);
                }

                physics1.setVelocity(newVelocity);
            }
        }
    }

    // 检测玩家碰撞：AI 会伤害所有玩家（本机 + 远端），每人独立计算无敌时间
    public void checkCollisions() {
        if (gameOver) return;

        List<GameObject> aiPlayers = getAIPlayers();
        if (aiPlayers.isEmpty()) return;

        for (GameObject player : getAllPlayers()) {
            TransformComponent playerTransform = player.getComponent(TransformComponent.class);
            HealthComponent playerHealth = player.getComponent(HealthComponent.class);
            if (playerTransform == null || playerHealth == null || !playerHealth.isAlive()) continue;

            Vector2 playerPos = playerTransform.getPosition();
            for (GameObject aiPlayer : aiPlayers) {
                TransformComponent aiTransform = aiPlayer.getComponent(TransformComponent.class);
                if (aiTransform == null) continue;

                float distance = playerPos.distance(aiTransform.getPosition());
                if (distance < GameConfig.PLAYER_COLLISION_RADIUS) {
                    playerHealth.takeDamage(GameConfig.PLAYER_COLLISION_DAMAGE);
                    if (!playerHealth.isInvulnerable()) {
                        playerHealth.setInvulnerable(GameConfig.PLAYER_INVULNERABLE_TIME);
                    }
                    // 只有本机玩家的死亡会结束单机游戏；远端玩家死亡由服务器销毁并在下次输入时重生
                    if ("Player".equals(player.getName()) && !playerHealth.isAlive()) {
                        gameOver = true;
                    }
                    break;
                }
            }
        }
    }

    public List<GameObject> getGameObjectsSnapshot() {
        return new ArrayList<>(scene.getGameObjects());
    }

    public Random getRandom() {
        return random;
    }
}
