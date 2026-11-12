package com.gameengine.example;

import com.gameengine.components.HealthComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.components.WeaponComponent;
import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.PlayerSkillController;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 游戏主场景：玩家控制、AI 行为、碰撞检测和粒子效果。
 *
 * 该类是"玩法层"，网络场景通过继承复用它；引擎层（Scene/GameEngine）不依赖它。
 */
public class GameScene extends Scene {
    private IRenderer renderer;
    private Random random;
    private float time;
    protected GameLogic gameLogic;
    private ParticleSystem playerParticles;
    private final List<ParticleSystem> collisionParticles = new ArrayList<>();
    private final Map<GameObject, ParticleSystem> aiPlayerParticles = new HashMap<>();
    private PlayerSkillController skillController;

    private boolean waitingReturn;
    private float waitInputTimer;
    private float freezeTimer;
    private final float inputCooldown = 0.25f;
    private final float freezeDelay = 0.20f;

    public GameScene(GameEngine engine) {
        super("GameScene");
    }

    /** 子类（网络客户端）可覆盖为 false，自行创建场景内容 */
    protected boolean shouldCreateDefaultWorld() {
        return true;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = getRenderer();
        if (renderer == null) {
            System.err.println("[GameScene] 渲染器未初始化");
            return;
        }
        this.random = new Random();
        this.time = 0;
        this.gameLogic = new GameLogic(this);
        this.waitingReturn = false;
        this.waitInputTimer = 0f;
        this.freezeTimer = 0f;

        collisionParticles.clear();
        aiPlayerParticles.clear();

        if (shouldCreateDefaultWorld()) {
            createWorld();
        }
    }

    /** 创建默认单机世界：玩家 + 初始 AI + 装饰 */
    protected void createWorld() {
        GameObject player = createPlayerEntity("Player", renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f);
        if (player != null) {
            addGameObject(player);
            skillController = new PlayerSkillController(player);
        }
        createAIPlayers();
        createDecorations();
        createPlayerParticles();
    }

    private void createPlayerParticles() {
        playerParticles = new ParticleSystem(renderer,
                new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f));
        playerParticles.setActive(true);
    }

    /**
     * 创建一个可玩的玩家实体（不含加入场景，由调用方决定 addGameObject）。
     */
    protected GameObject createPlayerEntity(String name, float x, float y) {
        if (renderer == null) return null;

        GameObject player = EntityFactory.createPlayer(name);
        player.addComponent(new TransformComponent(new Vector2(x, y)));

        PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);
        physics.setVelocity(new Vector2());

        player.addComponent(new HealthComponent(true));
        player.addComponent(new SkillComponent());
        player.addComponent(new WeaponComponent());
        // Scene#addGameObject 会自动 setScene
        return player;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        time += deltaTime;

        // ESC 返回主菜单（停止录制），而不是直接退出游戏
        if (handleEscapeToMenu()) {
            return;
        }

        // 只更新仍在本场景中的逻辑（场景切换已由引擎延迟处理）
        if (gameLogic != null) {
            gameLogic.handlePlayerInput(deltaTime);
            gameLogic.handleAIPlayerMovement(deltaTime);
            gameLogic.handleAIPlayerAvoidance(deltaTime);

            boolean wasGameOver = gameLogic.isGameOver();
            gameLogic.checkCollisions();

            if (gameLogic.isGameOver() && !wasGameOver) {
                spawnPlayerExplosion();
            }
        }

        if (skillController != null) {
            skillController.update();
        }

        updateParticles(deltaTime);
        handleGameOverInput(deltaTime);

        // 定期补充 AI（有上限，避免无限增长）
        if (time >= GameConfig.AI_SPAWN_INTERVAL) {
            time = 0;
            if (countAI() < GameConfig.MAX_AI_PLAYERS) {
                createAIPlayer();
            }
        }
    }

    private void handleGameOverInput(float deltaTime) {
        if (!waitingReturn) return;

        waitInputTimer += deltaTime;
        freezeTimer += deltaTime;

        if (waitInputTimer >= inputCooldown
                && (getInputManager().isAnyKeyJustPressed() || getInputManager().isMouseButtonJustPressed(0))) {
            getEngine().setScene(new MenuScene(getEngine(), "MainMenu"));
        }
    }

    private void spawnPlayerExplosion() {
        if (gameLogic == null || renderer == null) return;
        GameObject player = gameLogic.getUserPlayer();
        if (player == null) return;

        TransformComponent transform = player.getComponent(TransformComponent.class);
        if (transform == null) return;

        ParticleSystem.Config cfg = new ParticleSystem.Config();
        cfg.initialCount = 0;
        cfg.spawnRate = 9999f;
        cfg.opacityMultiplier = 1.0f;
        cfg.minRenderSize = 3.0f;
        cfg.burstSpeedMin = 250f;
        cfg.burstSpeedMax = 520f;
        cfg.burstLifeMin = 0.5f;
        cfg.burstLifeMax = 1.2f;
        cfg.burstSizeMin = 18f;
        cfg.burstSizeMax = 42f;
        cfg.burstR = 1.0f;
        cfg.burstGMin = 0.0f;
        cfg.burstGMax = 0.05f;
        cfg.burstB = 0.0f;

        ParticleSystem explosion = new ParticleSystem(renderer, transform.getPosition(), cfg);
        explosion.burst(180);
        collisionParticles.add(explosion);

        waitingReturn = true;
        waitInputTimer = 0f;
        freezeTimer = 0f;
    }

    private void updateParticles(float deltaTime) {
        boolean freeze = waitingReturn && freezeTimer >= freezeDelay;

        if (playerParticles != null && !freeze) {
            if (gameLogic != null) {
                GameObject player = gameLogic.getUserPlayer();
                if (player != null) {
                    TransformComponent transform = player.getComponent(TransformComponent.class);
                    if (transform != null) {
                        playerParticles.setPosition(transform.getPosition());
                    }
                }
            }
            playerParticles.update(deltaTime);
        }

        List<GameObject> aiPlayers = gameLogic != null ? gameLogic.getAIPlayers() : new ArrayList<>();
        if (!freeze) {
            for (GameObject aiPlayer : aiPlayers) {
                if (aiPlayer == null || !aiPlayer.isActive()) continue;

                ParticleSystem particles = aiPlayerParticles.get(aiPlayer);
                if (particles == null) {
                    TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                    if (transform != null) {
                        particles = new ParticleSystem(renderer, transform.getPosition(), ParticleSystem.Config.light());
                        particles.setActive(true);
                        aiPlayerParticles.put(aiPlayer, particles);
                    }
                }
                if (particles != null) {
                    TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                    if (transform != null) {
                        particles.setPosition(transform.getPosition());
                    }
                    particles.update(deltaTime);
                }
            }
        }

        aiPlayerParticles.entrySet().removeIf(entry ->
                !entry.getKey().isActive() || !aiPlayers.contains(entry.getKey()));

        for (ParticleSystem ps : collisionParticles) {
            if (ps != null && !freeze) {
                ps.update(deltaTime);
            }
        }
    }

    @Override
    public void render() {
        if (renderer == null) return;

        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.1f, 0.1f, 0.2f, 1.0f);
        super.render();
        renderParticles();
        renderHud();

        if (gameLogic != null && gameLogic.isGameOver()) {
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.0f, 0.0f, 0.0f, 0.35f);
            renderer.drawRect(cx - 200, cy - 60, 400, 120, 0.0f, 0.0f, 0.0f, 0.7f);
            renderer.drawText(cx - 100, cy - 10, "GAME OVER", 1.0f, 1.0f, 1.0f, 1.0f);
            renderer.drawText(cx - 180, cy + 30, "PRESS ANY KEY TO RETURN", 0.8f, 0.8f, 0.8f, 1.0f);
        }
    }

    private void renderParticles() {
        if (playerParticles != null && playerParticles.getParticleCount() > 0) {
            playerParticles.render();
        }
        for (ParticleSystem ps : aiPlayerParticles.values()) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }
        for (ParticleSystem ps : collisionParticles) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }
    }

    // ==================== HUD（从 Scene 搬回玩法层） ====================

    /** 子类可覆盖：客户端 HUD 应显示本地玩家而不是服务器玩家 */
    protected GameObject getHudPlayer() {
        return gameLogic != null ? gameLogic.getUserPlayer() : null;
    }

    protected void renderHud() {
        if (renderer == null) return;
        GameObject player = getHudPlayer();
        if (player == null) return;

        renderHealthBar(player);
        renderSkillBar(player);
    }

    private void renderHealthBar(GameObject player) {
        HealthComponent health = player.getComponent(HealthComponent.class);
        if (health == null) return;

        float healthPercent = health.getHealthPercentage();
        int barWidth = 200;
        int barHeight = 20;
        int barX = renderer.getWidth() - barWidth - 20;
        int barY = 20;

        renderer.drawRect(barX, barY, barWidth, barHeight, 0.3f, 0.3f, 0.3f, 0.8f);
        if (healthPercent > 0.6f) {
            renderer.drawRect(barX, barY, barWidth * healthPercent, barHeight, 0.0f, 1.0f, 0.0f, 0.8f);
        } else if (healthPercent > 0.3f) {
            renderer.drawRect(barX, barY, barWidth * healthPercent, barHeight, 1.0f, 1.0f, 0.0f, 0.8f);
        } else {
            renderer.drawRect(barX, barY, barWidth * healthPercent, barHeight, 1.0f, 0.0f, 0.0f, 0.8f);
        }
        renderer.drawRect(barX, barY, barWidth, barHeight, 1.0f, 1.0f, 1.0f, 0.3f);
        renderer.drawText(barX + 10, barY + 15,
                String.format("HP: %.0f/%.0f", health.getCurrentHealth(), health.getMaxHealth()),
                1.0f, 1.0f, 1.0f, 1.0f);

        if (health.isInvulnerable()) {
            renderer.drawText(barX, barY + 25,
                    String.format("无敌: %.1fs", health.getInvulnerableTimeRemaining()),
                    0.5f, 0.5f, 1.0f, 1.0f);
        }
    }

    private void renderSkillBar(GameObject player) {
        SkillComponent skill = player.getComponent(SkillComponent.class);
        if (skill == null) return;

        int skillBarWidth = 3 * GameConfig.SKILL_ICON_SPACING;
        int skillBarX = GameConfig.SKILL_BAR_CENTER_X - skillBarWidth / 2;

        renderer.drawRect(skillBarX - 10, GameConfig.SKILL_BAR_Y - 10,
                skillBarWidth + 20, GameConfig.SKILL_ICON_SIZE + 20,
                0.2f, 0.2f, 0.3f, 0.8f);

        SkillComponent.SkillType[] skillTypes = {
                SkillComponent.SkillType.MELEE_ATTACK,
                SkillComponent.SkillType.RANGED_ATTACK,
                SkillComponent.SkillType.DODGE
        };
        String[] skillKeys = {"Z", "X", "C"};
        RenderComponent.Color[] skillColors = {
                new RenderComponent.Color(1.0f, 0.0f, 0.0f, 1.0f),
                new RenderComponent.Color(0.0f, 1.0f, 1.0f, 1.0f),
                new RenderComponent.Color(0.0f, 1.0f, 0.0f, 1.0f)
        };

        for (int i = 0; i < skillTypes.length; i++) {
            int iconX = skillBarX + i * GameConfig.SKILL_ICON_SPACING;

            RenderComponent.Color bgColor = skill.isSkillReady(skillTypes[i])
                    ? new RenderComponent.Color(0.3f, 0.3f, 0.5f, 1.0f)
                    : new RenderComponent.Color(0.5f, 0.2f, 0.2f, 1.0f);

            renderer.drawRect(iconX, GameConfig.SKILL_BAR_Y,
                    GameConfig.SKILL_ICON_SIZE, GameConfig.SKILL_ICON_SIZE,
                    bgColor.r, bgColor.g, bgColor.b, bgColor.a);

            renderer.drawRect(iconX + 10, GameConfig.SKILL_BAR_Y + 10,
                    GameConfig.SKILL_ICON_SIZE - 20, GameConfig.SKILL_ICON_SIZE - 20,
                    skillColors[i].r, skillColors[i].g, skillColors[i].b, skillColors[i].a);

            float cooldown = skill.getSkillCooldown(skillTypes[i]);
            float totalCooldown = skill.getSkillTotalCooldown(skillTypes[i]);
            if (cooldown > 0 && totalCooldown > 0) {
                renderer.drawRect(iconX, GameConfig.SKILL_BAR_Y,
                        GameConfig.SKILL_ICON_SIZE, GameConfig.SKILL_ICON_SIZE * (cooldown / totalCooldown),
                        0.0f, 0.0f, 0.0f, 0.7f);
                renderer.drawText(iconX + GameConfig.SKILL_ICON_SIZE / 2 - 15,
                        GameConfig.SKILL_BAR_Y + GameConfig.SKILL_ICON_SIZE / 2 - 8,
                        String.format("%.1f", cooldown), 1.0f, 1.0f, 1.0f, 1.0f);
            }

            renderer.drawText(iconX + 5, GameConfig.SKILL_BAR_Y + 5,
                    skillKeys[i], 1.0f, 1.0f, 1.0f, 1.0f);
        }

        float manaPercent = skill.getManaPercentage();
        int barWidth = 200;
        int barHeight = 20;
        int barX = 20;
        int barY = 20;

        renderer.drawRect(barX, barY, barWidth, barHeight, 0.3f, 0.3f, 0.3f, 0.8f);
        renderer.drawRect(barX, barY, barWidth * manaPercent, barHeight, 0.0f, 0.5f, 1.0f, 0.8f);
        renderer.drawRect(barX, barY, barWidth, barHeight, 1.0f, 1.0f, 1.0f, 0.3f);
        renderer.drawText(barX + 10, barY + 15,
                String.format("MP: %.0f/%.0f", skill.getCurrentMana(), skill.getMaxMana()),
                1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ==================== 世界创建 ====================

    protected void createAIPlayers() {
        for (int i = 0; i < GameConfig.INITIAL_AI_PLAYERS; i++) {
            createAIPlayer();
        }
    }

    protected GameObject createAIPlayer() {
        if (renderer == null) return null;

        GameObject aiPlayer = EntityFactory.createRenderable("AIPlayer", renderer,
                RenderComponent.RenderType.RECTANGLE, 20, 20, 0.0f, 0.8f, 1.0f, 1.0f);

        Vector2 center = new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f);
        Vector2 position;
        int guard = 0;
        do {
            position = new Vector2(random.nextFloat() * renderer.getWidth(),
                    random.nextFloat() * renderer.getHeight());
        } while (position.distance(center) < 100 && ++guard < 20);

        aiPlayer.getComponent(TransformComponent.class).setPosition(position);

        PhysicsComponent physics = aiPlayer.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2(
                (random.nextFloat() - 0.5f) * GameConfig.AI_MOVE_SPEED,
                (random.nextFloat() - 0.5f) * GameConfig.AI_MOVE_SPEED));
        physics.setFriction(0.98f);

        aiPlayer.addComponent(new HealthComponent(false));

        addGameObject(aiPlayer);
        return aiPlayer;
    }

    private int countAI() {
        int count = 0;
        for (GameObject obj : getGameObjects()) {
            if ("AIPlayer".equals(obj.getName()) && obj.isActive()) count++;
        }
        return count;
    }

    private void createDecorations() {
        for (int i = 0; i < 5; i++) {
            GameObject decoration = EntityFactory.createRenderable("Decoration", renderer,
                    RenderComponent.RenderType.CIRCLE, 5, 5, 0.5f, 0.5f, 1.0f, 0.8f);
            decoration.getComponent(TransformComponent.class).setPosition(
                    new Vector2(random.nextFloat() * renderer.getWidth(),
                            random.nextFloat() * renderer.getHeight()));
            addGameObject(decoration);
        }
    }

    protected com.gameengine.input.InputManager getInputManager() {
        return getEngine() != null ? getEngine().getInputManager() : com.gameengine.input.InputManager.getInstance();
    }

    /**
     * 统一的 ESC 语义：返回主菜单并停止录制。
     * @return 是否已请求切换场景
     */
    protected boolean handleEscapeToMenu() {
        if (!getInputManager().isKeyJustPressed(GameConfig.KEY_ESCAPE)) {
            return false;
        }
        if (getEngine() == null) {
            return false;
        }
        getEngine().disableRecording();
        getEngine().setScene(new MenuScene(getEngine(), "MainMenu"));
        return true;
    }

    @Override
    public void clear() {
        if (playerParticles != null) {
            playerParticles.clear();
        }
        for (ParticleSystem ps : collisionParticles) {
            if (ps != null) ps.clear();
        }
        collisionParticles.clear();
        aiPlayerParticles.clear();
        skillController = null;
        waitingReturn = false;
        super.clear();
    }
}
