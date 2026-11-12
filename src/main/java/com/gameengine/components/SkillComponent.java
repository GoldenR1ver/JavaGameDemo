package com.gameengine.components;

import com.gameengine.config.GameConfig;
import com.gameengine.core.Component;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 技能组件：管理魔力、蓄力、近战/远程/闪避。
 *
 * 重要变化（解耦）：
 * - 组件不再直接读取 InputManager。输入由 PlayerController / 网络命令负责，
 *   通过 press()/release()/useSkill() 驱动。这样服务端的远端玩家实体不会
 *   因为"本机键盘按下"而施放技能。
 * - 所有方向由调用方传入（aim），组件不依赖鼠标等具体输入设备。
 */
public class SkillComponent extends Component<SkillComponent> {

    public enum SkillType {
        MELEE_ATTACK,   // 近战攻击 (Z键)
        RANGED_ATTACK,  // 远程攻击 (X键)
        DODGE           // 闪避 (C键)
    }

    public enum SkillState {
        READY,          // 就绪
        CHARGING,       // 蓄力中
        ACTIVE,         // 激活中
        COOLDOWN        // 冷却中
    }

    public static class SkillConfig {
        public final SkillType type;
        public float damage;
        public float range;
        public float bulletSpeed;
        public float cooldown;
        public float manaCost;
        public final boolean chargeable;
        public final float maxChargeTime;
        public final float chargeDamageMultiplier;
        public final float chargeSpeedMultiplier;

        public SkillConfig(SkillType type, float damage, float range, float bulletSpeed,
                           float cooldown, float manaCost, boolean chargeable,
                           float maxChargeTime, float chargeDamageMultiplier, float chargeSpeedMultiplier) {
            this.type = type;
            this.damage = damage;
            this.range = range;
            this.bulletSpeed = bulletSpeed;
            this.cooldown = cooldown;
            this.manaCost = manaCost;
            this.chargeable = chargeable;
            this.maxChargeTime = maxChargeTime;
            this.chargeDamageMultiplier = chargeDamageMultiplier;
            this.chargeSpeedMultiplier = chargeSpeedMultiplier;
        }
    }

    public static class SkillInstance {
        public final SkillConfig config;
        public SkillState state;
        public float currentCooldown;
        public float chargeTime;
        public float activeTime;

        public SkillInstance(SkillConfig config) {
            this.config = config;
            this.state = SkillState.READY;
        }
    }

    // 魔力系统
    private float maxMana;
    private float currentMana;
    private final float manaRegenRate;
    private float timeSinceLastSkill;
    private final float manaRegenDelay;

    // 技能管理
    private final Map<SkillType, SkillInstance> skills;
    private Scene scene;
    private IRenderer renderer;

    // 闪避状态（用游戏时间而不是墙钟，暂停/掉帧时行为一致）
    private boolean isDodging;
    private float dodgeTimer;
    private Vector2 originalVelocity;

    // 近战攻击状态
    private float meleeAttackTimer;
    private final List<GameObject> meleeHitEnemies;
    private ParticleSystem meleeParticleSystem;

    // 子弹管理
    private final List<GameObject> activeBullets;

    /** 最近一次瞄准方向（单位向量） */
    private Vector2 aimDirection = new Vector2(1, 0);

    private static final Map<SkillType, SkillConfig> BASE_SKILL_CONFIGS = new HashMap<>();

    static {
        BASE_SKILL_CONFIGS.put(SkillType.MELEE_ATTACK,
                new SkillConfig(SkillType.MELEE_ATTACK,
                        GameConfig.MELEE_DAMAGE,
                        GameConfig.MELEE_RANGE,
                        GameConfig.MELEE_BULLET_SPEED,
                        GameConfig.MELEE_COOLDOWN,
                        GameConfig.MELEE_MANA_COST,
                        GameConfig.MELEE_CHARGEABLE,
                        GameConfig.MELEE_MAX_CHARGE_TIME,
                        GameConfig.MELEE_CHARGE_DAMAGE_MULTIPLIER,
                        GameConfig.MELEE_CHARGE_SPEED_MULTIPLIER));

        BASE_SKILL_CONFIGS.put(SkillType.RANGED_ATTACK,
                new SkillConfig(SkillType.RANGED_ATTACK,
                        GameConfig.RANGED_DAMAGE,
                        GameConfig.RANGED_RANGE,
                        GameConfig.RANGED_BULLET_SPEED,
                        GameConfig.RANGED_COOLDOWN,
                        GameConfig.RANGED_MANA_COST,
                        GameConfig.RANGED_CHARGEABLE,
                        GameConfig.RANGED_MAX_CHARGE_TIME,
                        GameConfig.RANGED_CHARGE_DAMAGE_MULTIPLIER,
                        GameConfig.RANGED_CHARGE_SPEED_MULTIPLIER));

        BASE_SKILL_CONFIGS.put(SkillType.DODGE,
                new SkillConfig(SkillType.DODGE, 0, 0, 0,
                        GameConfig.DODGE_COOLDOWN,
                        GameConfig.DODGE_MANA_COST,
                        false, 0, 1.0f, 1.0f));
    }

    public SkillComponent() {
        this.skills = new HashMap<>();
        this.maxMana = GameConfig.PLAYER_MAX_MANA;
        this.currentMana = maxMana;
        this.manaRegenRate = GameConfig.MANA_REGEN_RATE;
        this.manaRegenDelay = GameConfig.MANA_REGEN_DELAY;
        this.meleeHitEnemies = new ArrayList<>();
        this.activeBullets = new ArrayList<>();
        this.originalVelocity = new Vector2(0, 0);
    }

    @Override
    public void initialize() {
        for (SkillType type : SkillType.values()) {
            SkillConfig base = BASE_SKILL_CONFIGS.get(type);
            if (base != null) {
                skills.put(type, new SkillInstance(new SkillConfig(
                        base.type, base.damage, base.range, base.bulletSpeed,
                        base.cooldown, base.manaCost, base.chargeable,
                        base.maxChargeTime, base.chargeDamageMultiplier, base.chargeSpeedMultiplier)));
            }
        }
        // 注意：此时 owner 可能还没加入场景，真正的资源在 ensureScene() 里惰性创建
        attachScene();
    }

    /** 惰性获取场景/渲染器（对象加入场景后才会真正可用） */
    private void attachScene() {
        if (owner == null) return;
        if (scene == null) {
            scene = owner.getScene();
        }
        if (scene != null && renderer == null) {
            renderer = scene.getRenderer();
        }
        if (scene != null && renderer != null && meleeParticleSystem == null && owner.getComponent(TransformComponent.class) != null) {
            meleeParticleSystem = createMeleeParticleSystem(owner.getComponent(TransformComponent.class).getPosition());
        }
    }

    @Override
    public void update(float deltaTime) {
        attachScene();
        updateMana(deltaTime);

        for (SkillInstance skill : skills.values()) {
            updateSkillState(skill, deltaTime);
        }

        if (isDodging) {
            dodgeTimer -= deltaTime;
            if (dodgeTimer <= 0) {
                endDodge();
            }
        }

        updateMeleeAttack(deltaTime);

        if (meleeParticleSystem != null) {
            TransformComponent transform = owner != null ? owner.getComponent(TransformComponent.class) : null;
            if (transform != null) {
                meleeParticleSystem.setPosition(transform.getPosition());
            }
            meleeParticleSystem.update(deltaTime);
        }

        updateBullets();
    }

    @Override
    public void render() {
        if (meleeParticleSystem != null) {
            meleeParticleSystem.render();
        }
    }

    // ==================== 输入接口（由控制器调用） ====================

    /**
     * 按住技能键时每帧调用。
     * @param aim 瞄准点（世界坐标），可为 null（沿用上次方向）
     */
    public void press(SkillType type, Vector2 aim) {
        updateAim(aim);
        switch (type) {
            case MELEE_ATTACK:
                if (getSkillState(type) == SkillState.READY) {
                    useSkill(type, aim);
                }
                break;
            case RANGED_ATTACK:
                if (getSkillState(type) == SkillState.READY) {
                    startSkill(type, aim);
                }
                break;
            case DODGE:
                // 闪避是一次性动作，由控制器在 JustPressed 时调用
                useSkill(type, aim);
                break;
        }
    }

    /** 松开技能键时调用 */
    public void release(SkillType type, Vector2 aim) {
        updateAim(aim);
        if (type == SkillType.MELEE_ATTACK) {
            endSkill(SkillType.MELEE_ATTACK);
        } else if (type == SkillType.RANGED_ATTACK) {
            releaseSkill(SkillType.RANGED_ATTACK, aim);
        }
    }

    private void updateAim(Vector2 aim) {
        if (aim == null) return;
        TransformComponent transform = owner != null ? owner.getComponent(TransformComponent.class) : null;
        if (transform == null) return;
        Vector2 direction = aim.subtract(transform.getPosition());
        if (direction.magnitude() > 1e-4f) {
            aimDirection = direction.normalize();
        }
    }

    public boolean startSkill(SkillType type, Vector2 aim) {
        updateAim(aim);
        SkillInstance skill = skills.get(type);
        if (skill == null || skill.state != SkillState.READY || currentMana < skill.config.manaCost) {
            return false;
        }
        if (skill.config.chargeable) {
            skill.state = SkillState.CHARGING;
            skill.chargeTime = 0;
            return true;
        }
        return useSkill(type, aim);
    }

    public boolean releaseSkill(SkillType type, Vector2 aim) {
        updateAim(aim);
        SkillInstance skill = skills.get(type);
        if (skill == null || skill.state != SkillState.CHARGING) {
            return false;
        }
        if (!consumeMana(skill.config.manaCost)) {
            skill.state = SkillState.READY;
            return false;
        }

        float chargeRatio = skill.config.maxChargeTime > 0
                ? skill.chargeTime / skill.config.maxChargeTime : 1f;
        float damageMultiplier = 1.0f + (skill.config.chargeDamageMultiplier - 1.0f) * chargeRatio;
        float speedMultiplier = 1.0f + (skill.config.chargeSpeedMultiplier - 1.0f) * chargeRatio;

        skill.currentCooldown = skill.config.cooldown;
        skill.state = SkillState.ACTIVE;
        skill.activeTime = 0;

        if (type == SkillType.RANGED_ATTACK) {
            createRangedAttack(skill, damageMultiplier, speedMultiplier);
        }
        skill.chargeTime = 0;
        return true;
    }

    public boolean useSkill(SkillType type, Vector2 aim) {
        updateAim(aim);
        SkillInstance skill = skills.get(type);
        if (skill == null || skill.state != SkillState.READY || currentMana < skill.config.manaCost) {
            return false;
        }
        if (!consumeMana(skill.config.manaCost)) {
            return false;
        }

        skill.currentCooldown = skill.config.cooldown;
        skill.state = SkillState.ACTIVE;
        skill.activeTime = 0;

        switch (type) {
            case MELEE_ATTACK:
                startMeleeAttack();
                break;
            case DODGE:
                activateDodge(skill);
                break;
            default:
                break;
        }
        return true;
    }

    public void endSkill(SkillType type) {
        SkillInstance skill = skills.get(type);
        if (skill != null && skill.state == SkillState.ACTIVE) {
            skill.state = SkillState.COOLDOWN;
            skill.activeTime = 0;

            if (type == SkillType.MELEE_ATTACK) {
                meleeHitEnemies.clear();
                meleeAttackTimer = 0;
                if (meleeParticleSystem != null) {
                    meleeParticleSystem.setSpawnRate(0);
                }
            }
        }
    }

    private boolean consumeMana(float amount) {
        if (currentMana >= amount) {
            currentMana -= amount;
            timeSinceLastSkill = 0;
            return true;
        }
        return false;
    }

    // ==================== 内部逻辑 ====================

    private ParticleSystem createMeleeParticleSystem(Vector2 position) {
        if (renderer == null) return null;
        ParticleSystem.Config config = new ParticleSystem.Config();
        config.spawnRate = GameConfig.MELEE_PARTICLE_SPAWN_RATE;
        config.initialCount = 0;
        config.speedMin = GameConfig.MELEE_PARTICLE_SPEED_MIN;
        config.speedMax = GameConfig.MELEE_PARTICLE_SPEED_MAX;
        config.lifeMin = GameConfig.MELEE_PARTICLE_LIFE_MIN;
        config.lifeMax = GameConfig.MELEE_PARTICLE_LIFE_MAX;
        config.sizeMin = GameConfig.MELEE_PARTICLE_SIZE_MIN;
        config.sizeMax = GameConfig.MELEE_PARTICLE_SIZE_MAX;
        config.r = GameConfig.MELEE_PARTICLE_R;
        config.g = GameConfig.MELEE_PARTICLE_G;
        config.b = GameConfig.MELEE_PARTICLE_B;
        config.opacityMultiplier = GameConfig.MELEE_PARTICLE_OPACITY;
        config.minRenderSize = 1.0f;
        return new ParticleSystem(renderer, position, config);
    }

    private void updateMana(float deltaTime) {
        timeSinceLastSkill += deltaTime;
        if (timeSinceLastSkill >= manaRegenDelay && currentMana < maxMana) {
            currentMana = Math.min(maxMana, currentMana + manaRegenRate * deltaTime);
        }
    }

    private void updateSkillState(SkillInstance skill, float deltaTime) {
        switch (skill.state) {
            case COOLDOWN:
                skill.currentCooldown -= deltaTime;
                if (skill.currentCooldown <= 0) {
                    skill.state = SkillState.READY;
                    skill.currentCooldown = 0;
                }
                break;
            case CHARGING:
                if (skill.config.chargeable) {
                    skill.chargeTime += deltaTime;
                    if (skill.config.maxChargeTime <= 0 || skill.chargeTime >= skill.config.maxChargeTime) {
                        skill.chargeTime = skill.config.maxChargeTime;
                        releaseSkill(skill.config.type, null);
                    }
                }
                break;
            case ACTIVE:
                skill.activeTime += deltaTime;
                handleActiveSkill(skill);
                break;
            default:
                break;
        }
    }

    private void handleActiveSkill(SkillInstance skill) {
        if (skill.config.type == SkillType.RANGED_ATTACK && skill.activeTime > 0.1f) {
            endSkill(skill.config.type);
        }
    }

    private void startMeleeAttack() {
        meleeHitEnemies.clear();
        meleeAttackTimer = 0;
        if (meleeParticleSystem != null) {
            meleeParticleSystem.setSpawnRate(0.05f);
        }
    }

    private void updateMeleeAttack(float deltaTime) {
        SkillInstance meleeSkill = skills.get(SkillType.MELEE_ATTACK);
        if (meleeSkill == null || meleeSkill.state != SkillState.ACTIVE) {
            return;
        }

        meleeAttackTimer += deltaTime;
        float attackInterval = 1.0f / Math.max(0.1f, meleeSkill.config.bulletSpeed);
        if (meleeAttackTimer >= attackInterval) {
            performMeleeAttack(meleeSkill);
            meleeAttackTimer = 0;
        }

        if (meleeParticleSystem != null) {
            generateMeleeParticles();
        }
    }

    private void generateMeleeParticles() {
        if (meleeParticleSystem == null) return;

        // 粒子系统内部按随机方向爆发；这里通过"集中爆发 + 低间隔"近似扇形火焰。
        // 不再每帧 new Random()/构造无用向量，避免 GC 抖动。
        meleeParticleSystem.burst(GameConfig.MELEE_PARTICLES_PER_FRAME);
    }

    private void performMeleeAttack(SkillInstance skill) {
        if (owner == null || scene == null) return;
        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) return;

        Vector2 playerPos = transform.getPosition();
        float angleRange = GameConfig.MELEE_ANGLE_RANGE;
        float distance = skill.config.range;

        for (GameObject enemy : scene.getGameObjects()) {
            if (!"AIPlayer".equals(enemy.getName()) || meleeHitEnemies.contains(enemy)) continue;

            TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
            if (enemyTransform == null) continue;

            Vector2 toEnemy = enemyTransform.getPosition().subtract(playerPos);
            float enemyDistance = toEnemy.magnitude();
            if (enemyDistance <= distance && enemyDistance > 1e-4f) {
                float dot = aimDirection.dot(toEnemy.normalize());
                float angle = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, dot))));
                if (angle <= angleRange / 2) {
                    applyDamage(enemy, skill.config.damage);
                    meleeHitEnemies.add(enemy);
                    createHitEffect(enemyTransform.getPosition(), 1.0f, 0.5f, 0.0f);
                }
            }
        }
    }

    private void createRangedAttack(SkillInstance skill, float damageMultiplier, float speedMultiplier) {
        if (owner == null || scene == null) return;
        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) return;

        final Vector2 playerPos = transform.getPosition();
        final Vector2 direction = new Vector2(aimDirection);
        final float actualDamage = skill.config.damage * damageMultiplier;
        final float actualSpeed = skill.config.bulletSpeed * speedMultiplier;
        final float range = skill.config.range;
        final IRenderer bulletRenderer = renderer != null ? renderer : scene.getRenderer();

        GameObject bullet = new GameObject("Bullet") {
            private Vector2 position = new Vector2(playerPos);
            private Vector2 velocity = direction.multiply(actualSpeed);
            private float travelDistance;
            private ParticleSystem trailParticleSystem;
            private final Vector2 startPos = new Vector2(playerPos);

            @Override
            public void initialize() {
                super.initialize();
                this.position = new Vector2(playerPos);
                this.velocity = direction.multiply(actualSpeed);
                this.startPos.set(playerPos);
                addComponent(new TransformComponent(new Vector2(position)));

                if (bulletRenderer != null) {
                    ParticleSystem.Config trailConfig = new ParticleSystem.Config();
                    trailConfig.spawnRate = 0.01f;
                    trailConfig.initialCount = 0;
                    trailConfig.speedMin = 0;
                    trailConfig.speedMax = 10;
                    trailConfig.lifeMin = 0.2f;
                    trailConfig.lifeMax = 0.5f;
                    trailConfig.sizeMin = 1f;
                    trailConfig.sizeMax = 3f;
                    trailConfig.r = 0.0f;
                    trailConfig.g = 1.0f;
                    trailConfig.b = 1.0f;
                    trailConfig.opacityMultiplier = 0.6f;
                    trailParticleSystem = new ParticleSystem(bulletRenderer, position, trailConfig);
                    trailParticleSystem.setSpawnRate(0.02f);
                }
            }

            @Override
            public void update(float deltaTime) {
                Vector2 delta = velocity.multiply(deltaTime);
                position = position.add(delta);
                travelDistance += delta.magnitude();

                TransformComponent bulletTransform = getComponent(TransformComponent.class);
                if (bulletTransform != null) {
                    bulletTransform.setPosition(position);
                }
                if (trailParticleSystem != null) {
                    trailParticleSystem.setPosition(position);
                    trailParticleSystem.update(deltaTime);
                }

                if (travelDistance >= range) {
                    destroy();
                    return;
                }
                checkCollisions();
            }

            @Override
            public void render() {
                if (bulletRenderer == null) return;
                bulletRenderer.drawRect(position.x - 3, position.y - 3, 6, 6,
                        0.0f, 1.0f, 1.0f, 1.0f);
                bulletRenderer.drawLine(startPos.x, startPos.y, position.x, position.y,
                        0.0f, 1.0f, 1.0f, 0.3f);
                if (trailParticleSystem != null) {
                    trailParticleSystem.render();
                }
            }

            @Override
            public void destroy() {
                if (trailParticleSystem != null) {
                    trailParticleSystem.clear();
                }
                activeBullets.remove(this);
                super.destroy();
            }

            private void checkCollisions() {
                Scene currentScene = getScene();
                if (currentScene == null) return;
                for (GameObject enemy : currentScene.getGameObjects()) {
                    if (!"AIPlayer".equals(enemy.getName())) continue;
                    TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
                    if (enemyTransform == null) continue;

                    if (position.distance(enemyTransform.getPosition()) <= GameConfig.BULLET_COLLISION_RADIUS) {
                        applyDamage(enemy, actualDamage);
                        createHitEffect(enemyTransform.getPosition(), 0.0f, 0.0f, 1.0f);
                        destroy();
                        return;
                    }
                }
            }
        };

        activeBullets.add(bullet);
        scene.addGameObject(bullet);
    }

    private void updateBullets() {
        Iterator<GameObject> iterator = activeBullets.iterator();
        while (iterator.hasNext()) {
            GameObject bullet = iterator.next();
            if (!bullet.isActive()) {
                iterator.remove();
            }
        }
    }

    private void activateDodge(SkillInstance skill) {
        if (owner == null) return;
        isDodging = true;
        dodgeTimer = GameConfig.DODGE_DURATION;
        skill.currentCooldown = skill.config.cooldown;

        PhysicsComponent physics = owner.getComponent(PhysicsComponent.class);
        if (physics != null) {
            originalVelocity = physics.getVelocity();
            physics.setVelocity(originalVelocity.multiply(GameConfig.DODGE_SPEED_BOOST));
        }

        HealthComponent health = owner.getComponent(HealthComponent.class);
        if (health != null) {
            health.setInvulnerable(GameConfig.DODGE_DURATION);
        }

        createDodgeEffect();
    }

    private void endDodge() {
        isDodging = false;
        if (owner != null) {
            PhysicsComponent physics = owner.getComponent(PhysicsComponent.class);
            if (physics != null) {
                physics.setVelocity(originalVelocity);
            }
        }

        SkillInstance dodgeSkill = skills.get(SkillType.DODGE);
        if (dodgeSkill != null && dodgeSkill.state == SkillState.ACTIVE) {
            dodgeSkill.state = SkillState.COOLDOWN;
            dodgeSkill.activeTime = 0;
        }
    }

    private void createDodgeEffect() {
        if (scene == null || renderer == null || owner == null) return;
        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) return;

        final Vector2 playerPos = transform.getPosition();
        final float duration = GameConfig.DODGE_DURATION;
        final IRenderer effectRenderer = renderer;

        GameObject dodgeEffect = new GameObject("DodgeEffect") {
            private float lifetime = duration;

            @Override
            public void update(float deltaTime) {
                lifetime -= deltaTime;
                if (lifetime <= 0) {
                    destroy();
                }
            }

            @Override
            public void render() {
                float alpha = 0.7f * (lifetime / duration);
                effectRenderer.drawCircle(playerPos.x, playerPos.y, 50, 32,
                        0.5f, 0.5f, 1.0f, Math.max(0f, alpha));
            }
        };

        scene.addGameObject(dodgeEffect);
    }

    private void createHitEffect(Vector2 position, float r, float g, float b) {
        if (scene == null || renderer == null) return;

        ParticleSystem.Config config = new ParticleSystem.Config();
        config.initialCount = GameConfig.HIT_EFFECT_PARTICLES;
        config.speedMin = GameConfig.HIT_EFFECT_SPEED_MIN;
        config.speedMax = GameConfig.HIT_EFFECT_SPEED_MAX;
        config.lifeMin = GameConfig.HIT_EFFECT_LIFE_MIN;
        config.lifeMax = GameConfig.HIT_EFFECT_LIFE_MAX;
        config.sizeMin = GameConfig.HIT_EFFECT_SIZE_MIN;
        config.sizeMax = GameConfig.HIT_EFFECT_SIZE_MAX;
        config.r = r;
        config.g = g;
        config.b = b;

        final ParticleSystem hitParticles = new ParticleSystem(renderer, position, config);
        hitParticles.burst(GameConfig.HIT_EFFECT_PARTICLES);

        GameObject hitEffect = new GameObject("HitEffect") {
            private float lifetime = GameConfig.HIT_EFFECT_LIFE_MAX;

            @Override
            public void update(float deltaTime) {
                hitParticles.update(deltaTime);
                lifetime -= deltaTime;
                if (lifetime <= 0) {
                    destroy();
                }
            }

            @Override
            public void render() {
                hitParticles.render();
            }

            @Override
            public void destroy() {
                hitParticles.clear();
                super.destroy();
            }
        };

        scene.addGameObject(hitEffect);
    }

    private void applyDamage(GameObject enemy, float damage) {
        if (enemy == null) return;
        HealthComponent enemyHealth = enemy.getComponent(HealthComponent.class);
        if (enemyHealth != null) {
            enemyHealth.takeDamage(damage);
        }
    }

    // ==================== Getters / Setters ====================

    public float getCurrentMana() {
        return currentMana;
    }

    public float getMaxMana() {
        return maxMana;
    }

    public float getManaPercentage() {
        return maxMana > 0 ? currentMana / maxMana : 0;
    }

    public SkillState getSkillState(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.state : SkillState.READY;
    }

    public float getSkillCooldown(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.currentCooldown : 0;
    }

    public float getSkillTotalCooldown(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.config.cooldown : 0;
    }

    public float getSkillChargeTime(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.chargeTime : 0;
    }

    public float getSkillMaxChargeTime(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.config.maxChargeTime : 0;
    }

    public boolean isSkillReady(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null && skill.state == SkillState.READY && currentMana >= skill.config.manaCost;
    }

    public boolean isDodging() {
        return isDodging;
    }

    public SkillConfig getSkillConfig(SkillType type) {
        SkillInstance skill = skills.get(type);
        return skill != null ? skill.config : null;
    }

    /** 强化技能（武器系统调用） */
    public void enhanceSkill(SkillType type, float damageMultiplier, float rangeMultiplier,
                             float speedMultiplier, float cooldownMultiplier) {
        SkillInstance skill = skills.get(type);
        if (skill != null) {
            skill.config.damage *= damageMultiplier;
            skill.config.range *= rangeMultiplier;
            skill.config.bulletSpeed *= speedMultiplier;
            skill.config.cooldown *= cooldownMultiplier;
            skill.config.range = Math.min(skill.config.range, GameConfig.WEAPON_MAX_RANGE);
        }
    }

    public void restoreMana(float amount) {
        currentMana = Math.min(maxMana, currentMana + amount);
    }

    public void setCurrentMana(float amount) {
        currentMana = Math.max(0, Math.min(maxMana, amount));
    }

    public void setMaxMana(float maxMana) {
        this.maxMana = maxMana;
        if (currentMana > maxMana) {
            currentMana = maxMana;
        }
    }
}
