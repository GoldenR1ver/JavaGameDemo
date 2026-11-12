package com.gameengine.components;

import com.gameengine.config.GameConfig;
import com.gameengine.core.Component;
import com.gameengine.core.GameObject;
import com.gameengine.math.Vector2;

import java.util.Random;

/**
 * 血量组件，管理游戏对象的生命值
 */
public class HealthComponent extends Component<HealthComponent> {
    /** 敌人死亡掉落葫芦籽的概率 */
    private static final float DROP_CHANCE = 0.3f;

    private final Random random = new Random();
    private float maxHealth;
    private float currentHealth;
    private boolean isInvulnerable;
    private float invulnerableTime;
    private float invulnerableTimer;

    public HealthComponent(float maxHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
        this.isInvulnerable = false;
        this.invulnerableTime = 0;
        this.invulnerableTimer = 0;
    }

    // 使用GameConfig常量的便捷构造函数
    public HealthComponent(boolean isPlayer) {
        // 根据是否是玩家使用不同的GameConfig常量
        this(isPlayer ? GameConfig.PLAYER_MAX_HEALTH : GameConfig.ENEMY_BASE_HEALTH);
    }

    @Override
    public void initialize() {
        // 初始化血量组件
    }

    @Override
    public void update(float deltaTime) {
        // 更新无敌时间
        if (isInvulnerable) {
            invulnerableTimer -= deltaTime;
            if (invulnerableTimer <= 0) {
                isInvulnerable = false;
            }
        }
    }

    @Override
    public void render() {
        // 血量组件不直接渲染，由UI系统渲染
    }

    /**
     * 造成伤害
     */
    public void takeDamage(float damage) {
        if (isInvulnerable || currentHealth <= 0) {
            return;
        }

        currentHealth -= damage;

        if (currentHealth <= 0) {
            currentHealth = 0;
            onDeath();
        } else {
            onDamage(damage);
        }
    }

    /**
     * 治疗
     */
    public void heal(float amount) {
        currentHealth = Math.min(currentHealth + amount, maxHealth);
    }

    /**
     * 设置无敌状态 - 使用GameConfig常量
     */
    public void setInvulnerable(float duration) {
        this.isInvulnerable = true;
        this.invulnerableTime = duration;
        this.invulnerableTimer = duration;
    }

    /**
     * 设置玩家无敌状态（使用默认无敌时间）
     */
    public void setPlayerInvulnerable() {
        setInvulnerable(GameConfig.PLAYER_INVULNERABLE_TIME);
    }

    /**
     * 重置血量
     */
    public void reset() {
        this.currentHealth = maxHealth;
        this.isInvulnerable = false;
        this.invulnerableTimer = 0;
    }

    /**
     * 设置最大血量
     */
    public void setMaxHealth(float maxHealth) {
        this.maxHealth = maxHealth;
        if (currentHealth > maxHealth) {
            currentHealth = maxHealth;
        }
    }

    /**
     * 受到伤害时的回调
     */
    protected void onDamage(float damage) {
        if (owner != null) {
            System.out.println(owner.getName() + " 受到 " + damage + " 点伤害，剩余血量: " + currentHealth);
        }
    }

    /**
     * 死亡时的回调。
     *
     * 修复：原实现连续调用了两次 owner.destroy()，并在非玩家对象上重复生成掉落判定。
     * 现在只销毁一次，且只有未处理过死亡的实体才会掉落。
     */
    protected void onDeath() {
        if (owner == null) return;
        if (owner.getName() == null) return;

        System.out.println(owner.getName() + " 死亡");

        if (!"Player".equals(owner.getName())) {
            generateWeaponDrop();
            owner.destroy();
        }
    }

    private void generateWeaponDrop() {
        if (owner == null || owner.getScene() == null) return;

        // 30%概率掉落武器
        if (random.nextFloat() < DROP_CHANCE) {
            GameObject weaponDrop = new GameObject("WeaponDrop");

            TransformComponent enemyTransform = owner.getComponent(TransformComponent.class);
            Vector2 dropPosition = enemyTransform != null ? enemyTransform.getPosition() : new Vector2(0, 0);
            weaponDrop.addComponent(new TransformComponent(dropPosition));

            WeaponComponent.HuluSeed seed = generateRandomHuluSeed();
            weaponDrop.addComponent(new WeaponDrop(seed));

            owner.getScene().addGameObject(weaponDrop);
        }
    }

    private WeaponComponent.HuluSeed generateRandomHuluSeed() {
        WeaponComponent.WeaponType[] weaponTypes = WeaponComponent.WeaponType.values();
        WeaponComponent.WeaponType randomType = weaponTypes[random.nextInt(weaponTypes.length)];

        WeaponComponent.EnhancementType randomEnhancement = null;
        float value = 0f;

        if (randomType != WeaponComponent.WeaponType.LIUWA_HULUZI) {
            WeaponComponent.EnhancementType[] enhancements = WeaponComponent.EnhancementType.values();
            randomEnhancement = enhancements[random.nextInt(enhancements.length)];

            switch (randomEnhancement) {
                case HUGE:
                    value = 0.1f + random.nextFloat() * 0.3f;
                    break;
                case SHINY:
                    value = 0.05f + random.nextFloat() * 0.15f;
                    break;
                case SMOOTH:
                    value = 0.1f + random.nextFloat() * 0.2f;
                    break;
                case BRIGHT:
                    value = 0.1f + random.nextFloat() * 0.2f;
                    break;
            }
        } else {
            value = 0.5f + random.nextFloat() * 1.0f;
        }

        return new WeaponComponent.HuluSeed(randomType, randomEnhancement, value);
    }

    // Getters
    public float getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(float currentHealth){ this.currentHealth = currentHealth;}

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealthPercentage() {
        return maxHealth > 0 ? currentHealth / maxHealth : 0;
    }

    public boolean isAlive() {
        return currentHealth > 0;
    }

    public boolean isInvulnerable() {
        return isInvulnerable;
    }

    public float getInvulnerableTimeRemaining() {
        return invulnerableTimer;
    }
}
