package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;
import java.util.Random;

/**
 * 武器掉落组件：管理葫芦籽武器的掉落和拾取
 */
public class WeaponDrop extends Component<WeaponDrop> {

    private WeaponComponent.HuluSeed seed;
    private Scene scene;
    private ParticleSystem particleSystem;
    private TransformComponent transform;
    private boolean pickedUp;
    private float pickupRadius;
    private float floatAmplitude;
    private float floatSpeed;
    private float floatTimer;
    private Vector2 originalPosition;

    public WeaponDrop(WeaponComponent.HuluSeed seed) {
        this.seed = seed;
        this.pickedUp = false;
        this.pickupRadius = 50f;
        this.floatAmplitude = 5f;
        this.floatSpeed = 2f;
        this.floatTimer = 0f;
    }

    @Override
    public void initialize() {
        ensureInitialized();
    }

    /**
     * 惰性初始化：组件可能在对象加入 Scene 之前就被 addComponent，
     * 此时 owner.getScene() 还是 null。这里在第一次 update 时补一次初始化，
     * 避免粒子系统/浮动基准位置永久缺失。
     */
    private void ensureInitialized() {
        if (owner == null) return;
        if (scene == null) {
            this.scene = owner.getScene();
        }
        if (transform == null) {
            this.transform = owner.getComponent(TransformComponent.class);
        }
        if (transform != null && originalPosition == null) {
            this.originalPosition = new Vector2(transform.getPosition());
        }
        if (scene != null && particleSystem == null) {
            createParticleSystem();
        }
    }

    @Override
    public void update(float deltaTime) {
        ensureInitialized();
        if (pickedUp || scene == null || transform == null) return;

        // 浮动动画
        updateFloatAnimation(deltaTime);

        // 检查玩家是否在拾取范围内
        checkPlayerPickup();

        // 更新粒子系统
        if (particleSystem != null) {
            particleSystem.update(deltaTime);
        }
    }

    @Override
    public void render() {
        if (pickedUp || scene == null || transform == null) return;

        Vector2 position = transform.getPosition();

        // 渲染掉落物（彩色矩形）
        renderWeaponDrop(position);

        // 渲染粒子系统
        if (particleSystem != null) {
            particleSystem.render();
        }

        // 渲染拾取范围提示（调试用）
        // renderPickupRadius(position);
    }

    /**
     * 创建粒子系统
     */
    private void createParticleSystem() {
        if (scene == null || scene.getRenderer() == null) return;

        ParticleSystem.Config config = new ParticleSystem.Config();
        config.initialCount = 0;
        config.spawnRate = 2f; // 每秒生成2个粒子
        config.speedMin = 10f;
        config.speedMax = 30f;
        config.lifeMin = 1.0f;
        config.lifeMax = 2.0f;
        config.sizeMin = 3f;
        config.sizeMax = 8f;
        config.minRenderSize = 2f;

        // 根据葫芦籽类型设置颜色
        switch (seed.type) {
            case SIWA_HULUZI:
                config.r = 1.0f; config.g = 0.0f; config.b = 0.0f; // 红色 - 近战
                break;
            case WUWA_HULUZI:
                config.r = 0.0f; config.g = 1.0f; config.b = 1.0f; // 青色 - 远程
                break;
            case LIUWA_HULUZI:
                config.r = 1.0f; config.g = 1.0f; config.b = 0.0f; // 黄色 - 魔力
                break;
            case QIWA_HULUZI:
                config.r = 0.0f; config.g = 1.0f; config.b = 0.0f; // 绿色 - 闪避
                break;
        }

        config.opacityMultiplier = 0.8f;

        Vector2 position = transform != null ? transform.getPosition() : new Vector2(0, 0);
        particleSystem = new ParticleSystem(scene.getRenderer(), position, config);
        particleSystem.setActive(true);
    }

    /**
     * 更新浮动动画
     */
    private void updateFloatAnimation(float deltaTime) {
        floatTimer += deltaTime * floatSpeed;
        float floatOffset = (float) Math.sin(floatTimer) * floatAmplitude;

        Vector2 newPosition = new Vector2(
                originalPosition.x,
                originalPosition.y + floatOffset
        );

        transform.setPosition(newPosition);

        // 更新粒子系统位置
        if (particleSystem != null) {
            particleSystem.setPosition(newPosition);
        }
    }

    /**
     * 检查玩家拾取
     */
    private void checkPlayerPickup() {
        GameObject player = findPlayer();
        if (player == null) return;

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null) return;

        float distance = transform.getPosition().distance(playerTransform.getPosition());

        if (distance <= pickupRadius) {
            pickup(player);
        }
    }

    /**
     * 查找玩家对象
     */
    private GameObject findPlayer() {
        if (scene == null) return null;

        for (GameObject obj : scene.getGameObjects()) {
            String name = obj.getName();
            if ("Player".equals(name) || "LocalPlayer".equals(name) || name.startsWith("NetworkPlayer")) {
                return obj;
            }
        }
        return null;
    }

    /**
     * 拾取武器
     */
    private void pickup(GameObject player) {
        WeaponComponent weaponComponent = player.getComponent(WeaponComponent.class);
        if (weaponComponent != null) {
            weaponComponent.pickUpHuluSeed(seed);
            pickedUp = true;

            // 创建拾取特效
            createPickupEffect();

            // 销毁掉落物
            if (owner != null) {
                Scene scene = owner.getScene();
                scene.removeGameObject(owner);
            }
        }
    }

    /**
     * 创建拾取特效
     */
    private void createPickupEffect() {
        if (scene == null || scene.getRenderer() == null) return;

        Vector2 position = transform.getPosition();

        // 创建爆炸粒子效果
        ParticleSystem.Config explosionConfig = new ParticleSystem.Config();
        explosionConfig.initialCount = 0;
        explosionConfig.spawnRate = 9999f;
        explosionConfig.burstSpeedMin = 100f;
        explosionConfig.burstSpeedMax = 200f;
        explosionConfig.burstLifeMin = 0.5f;
        explosionConfig.burstLifeMax = 1.0f;
        explosionConfig.burstSizeMin = 5f;
        explosionConfig.burstSizeMax = 15f;
        explosionConfig.minRenderSize = 3f;

        // 根据葫芦籽类型设置爆炸颜色
        switch (seed.type) {
            case SIWA_HULUZI:
                explosionConfig.burstR = 1.0f; explosionConfig.burstGMin = 0.3f; explosionConfig.burstGMax = 0.6f; explosionConfig.burstB = 0.0f;
                break;
            case WUWA_HULUZI:
                explosionConfig.burstR = 0.0f; explosionConfig.burstGMin = 0.7f; explosionConfig.burstGMax = 1.0f; explosionConfig.burstB = 1.0f;
                break;
            case LIUWA_HULUZI:
                explosionConfig.burstR = 1.0f; explosionConfig.burstGMin = 0.8f; explosionConfig.burstGMax = 1.0f; explosionConfig.burstB = 0.0f;
                break;
            case QIWA_HULUZI:
                explosionConfig.burstR = 0.0f; explosionConfig.burstGMin = 0.7f; explosionConfig.burstGMax = 1.0f; explosionConfig.burstB = 0.3f;
                break;
        }

        ParticleSystem explosion = new ParticleSystem(scene.getRenderer(), position, explosionConfig);
        explosion.burst(20);

        // 创建临时对象管理爆炸粒子
        GameObject explosionEffect = new GameObject("PickupExplosion") {
            private ParticleSystem particles = explosion;
            private float lifetime = 1.0f;

            @Override
            public void update(float deltaTime) {
                particles.update(deltaTime);
                lifetime -= deltaTime;
                if (lifetime <= 0) {
                    destroy();
                }
            }

            @Override
            public void render() {
                particles.render();
            }

            @Override
            public void destroy() {
                particles.clear();
                super.destroy();
            }
        };

        scene.addGameObject(explosionEffect);
    }

    /**
     * 渲染武器掉落物
     */
    private void renderWeaponDrop(Vector2 position) {
        if (scene == null || scene.getRenderer() == null) return;

        float size = 20f;

        // 根据葫芦籽类型设置颜色
        float r, g, b;
        switch (seed.type) {
            case SIWA_HULUZI:
                r = 1.0f; g = 0.0f; b = 0.0f; // 红色
                break;
            case WUWA_HULUZI:
                r = 0.0f; g = 1.0f; b = 1.0f; // 青色
                break;
            case LIUWA_HULUZI:
                r = 1.0f; g = 1.0f; b = 0.0f; // 黄色
                break;
            case QIWA_HULUZI:
                r = 0.0f; g = 1.0f; b = 0.0f; // 绿色
                break;
            default:
                r = 1.0f; g = 1.0f; b = 1.0f; // 白色
        }

        // 绘制彩色矩形
        scene.getRenderer().drawRect(
                position.x - size/2, position.y - size/2,
                size, size,
                r, g, b, 1.0f
        );

        // 绘制边框
        scene.getRenderer().drawRect(
                position.x - size/2, position.y - size/2,
                size, size,
                1.0f, 1.0f, 1.0f, 0.5f
        );

        // 绘制中心点
        scene.getRenderer().drawRect(
                position.x - 2, position.y - 2,
                4, 4,
                1.0f, 1.0f, 1.0f, 1.0f
        );
    }

    /**
     * 渲染拾取范围（调试用）
     */
    private void renderPickupRadius(Vector2 position) {
        if (scene == null || scene.getRenderer() == null) return;

        // 绘制拾取范围圆环
        scene.getRenderer().drawCircle(
                position.x, position.y,
                pickupRadius, 32,
                0.0f, 1.0f, 0.0f, 0.3f
        );
    }

    /**
     * 清理资源
     */
    @Override
    public void destroy() {
        if (particleSystem != null) {
            particleSystem.clear();
        }
        super.destroy();
    }

    // Getters
    public WeaponComponent.HuluSeed getSeed() {
        return seed;
    }

    public boolean isPickedUp() {
        return pickedUp;
    }

    public float getPickupRadius() {
        return pickupRadius;
    }

    public void setPickupRadius(float pickupRadius) {
        this.pickupRadius = pickupRadius;
    }
}
