package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.core.GameObject;
import com.gameengine.scene.Scene;
import java.util.*;

/**
 * 武器组件：管理葫芦籽武器系统
 * 支持四种葫芦籽，每种有不同词条效果
 */
public class WeaponComponent extends Component<WeaponComponent> {

    // 葫芦籽类型枚举
    public enum WeaponType {
        SIWA_HULUZI("四娃葫芦籽", "近战攻击"),
        WUWA_HULUZI("五娃葫芦籽", "远程攻击"),
        LIUWA_HULUZI("六娃葫芦籽", "魔力条"),
        QIWA_HULUZI("七娃葫芦籽", "闪避");

        private final String displayName;
        private final String effect;

        WeaponType(String displayName, String effect) {
            this.displayName = displayName;
            this.effect = effect;
        }

        public String getDisplayName() { return displayName; }
        public String getEffect() { return effect; }
    }

    // 词条类型枚举
    public enum EnhancementType {
        HUGE("巨大的", "增加伤害"),
        SHINY("闪亮的", "降低冷却"),
        SMOOTH("圆润的", "增加子弹速度/射速"),
        BRIGHT("鲜艳的", "增加射程");

        private final String displayName;
        private final String effect;

        EnhancementType(String displayName, String effect) {
            this.displayName = displayName;
            this.effect = effect;
        }

        public String getDisplayName() { return displayName; }
        public String getEffect() { return effect; }
    }

    // 葫芦籽类
    public static class HuluSeed {
        public WeaponType type;
        public EnhancementType enhancement;
        public float value; // 增强数值

        public HuluSeed(WeaponType type, EnhancementType enhancement, float value) {
            this.type = type;
            this.enhancement = enhancement;
            this.value = value;
        }

        public String getDisplayName() {
            if(enhancement == null)return "六娃葫芦籽";
            return enhancement.getDisplayName() + "的" + type.getDisplayName();
        }

        public String getEffectDescription() {
            if(enhancement == null)return "魔力";
            return enhancement.getEffect() + "提升了";
        }
    }

    // 玩家当前装备的葫芦籽
    private Map<WeaponType, HuluSeed> equippedWeapons;
    private Scene scene;
    private Random random;

    public WeaponComponent() {
        this.equippedWeapons = new HashMap<>();
        this.random = new Random();
    }

    @Override
    public void initialize() {
        if (owner != null) {
            this.scene = owner.getScene();
        }
    }

    @Override
    public void update(float deltaTime) {
        // 武器组件主要处理拾取逻辑，不需要每帧更新
    }

    @Override
    public void render() {
        // 武器组件不直接渲染
    }

    /**
     * 拾取葫芦籽
     */
    public void pickUpHuluSeed(HuluSeed seed) {
        // 应用葫芦籽效果
        applyWeaponEffect(seed);

        // 显示拾取提示
        showPickupMessage(seed);

        // 更新装备的武器
        equippedWeapons.put(seed.type, seed);

        // System.out.println("拾取了" + seed.getDisplayName() + "，" + seed.getEffectDescription());
    }

    /**
     * 应用葫芦籽效果到对应的技能组件
     */
    private void applyWeaponEffect(HuluSeed seed) {
        SkillComponent skillComponent = owner.getComponent(SkillComponent.class);
        if (skillComponent == null) return;

        switch (seed.type) {
            case SIWA_HULUZI:
                applyMeleeEnhancement(skillComponent, seed);
                break;
            case WUWA_HULUZI:
                applyRangedEnhancement(skillComponent, seed);
                break;
            case LIUWA_HULUZI:
                applyManaEnhancement(skillComponent, seed);
                break;
            case QIWA_HULUZI:
                applyDodgeEnhancement(skillComponent, seed);
                break;
        }
    }

    /**
     * 应用近战攻击增强
     */
    private void applyMeleeEnhancement(SkillComponent skillComponent, HuluSeed seed) {
        switch (seed.enhancement) {
            case HUGE:
                skillComponent.enhanceSkill(SkillComponent.SkillType.MELEE_ATTACK,
                        1.0f + seed.value, 1.0f, 1.0f, 1.0f);
                break;
            case SHINY:
                skillComponent.enhanceSkill(SkillComponent.SkillType.MELEE_ATTACK,
                        1.0f, 1.0f, 1.0f, 1.0f - seed.value);
                break;
            case SMOOTH:
                skillComponent.enhanceSkill(SkillComponent.SkillType.MELEE_ATTACK,
                        1.0f, 1.0f, 1.0f + seed.value, 1.0f);
                break;
            case BRIGHT:
                skillComponent.enhanceSkill(SkillComponent.SkillType.MELEE_ATTACK,
                        1.0f, 1.0f + seed.value, 1.0f, 1.0f);
                break;
        }
    }

    /**
     * 应用远程攻击增强
     */
    private void applyRangedEnhancement(SkillComponent skillComponent, HuluSeed seed) {
        switch (seed.enhancement) {
            case HUGE:
                skillComponent.enhanceSkill(SkillComponent.SkillType.RANGED_ATTACK,
                        1.0f + seed.value, 1.0f, 1.0f, 1.0f);
                break;
            case SHINY:
                skillComponent.enhanceSkill(SkillComponent.SkillType.RANGED_ATTACK,
                        1.0f, 1.0f, 1.0f, 1.0f - seed.value);
                break;
            case SMOOTH:
                skillComponent.enhanceSkill(SkillComponent.SkillType.RANGED_ATTACK,
                        1.0f, 1.0f, 1.0f + seed.value, 1.0f);
                break;
            case BRIGHT:
                skillComponent.enhanceSkill(SkillComponent.SkillType.RANGED_ATTACK,
                        1.0f, 1.0f + seed.value, 1.0f, 1.0f);
                break;
        }
    }

    /**
     * 应用魔力条增强
     */
    private void applyManaEnhancement(SkillComponent skillComponent, HuluSeed seed) {
        // 六娃葫芦籽直接增加魔力上限和恢复魔力
        skillComponent.setMaxMana(skillComponent.getMaxMana() + seed.value * 50);
        skillComponent.restoreMana(seed.value * 30);
    }

    /**
     * 应用闪避增强
     */
    private void applyDodgeEnhancement(SkillComponent skillComponent, HuluSeed seed) {
        // 七娃葫芦籽主要影响闪避冷却时间
        switch (seed.enhancement) {
            case SHINY:
                skillComponent.enhanceSkill(SkillComponent.SkillType.DODGE,
                        1.0f, 1.0f, 1.0f, 1.0f - seed.value);
                break;
            default:
                // 其他词条对闪避影响较小，主要降低冷却
                skillComponent.enhanceSkill(SkillComponent.SkillType.DODGE,
                        1.0f, 1.0f, 1.0f, 0.9f);
                break;
        }
    }

    /**
     * 显示拾取消息
     */
    private void showPickupMessage(HuluSeed seed) {
        if (scene != null && scene.getRenderer() != null) {
            // 在实际游戏中，这里可以调用UI系统显示消息
            System.out.println("拾取了" + seed.getDisplayName() + "，" + seed.getEffectDescription());

            // 也可以在这里创建临时的文本显示对象
            createPickupTextEffect(seed);
        }
    }

    /**
     * 创建拾取文字效果
     */
    private void createPickupTextEffect(HuluSeed seed) {
        // 这里可以创建一个临时的GameObject来显示拾取文字
        // 由于时间关系，这里只输出到控制台
        System.out.println("UI: 拾取了" + seed.getDisplayName() + "，" + seed.getEffectDescription());
    }

    /**
     * 生成随机葫芦籽（用于敌人掉落）
     */
    public HuluSeed generateRandomHuluSeed() {
        // 随机选择葫芦籽类型
        WeaponType[] weaponTypes = WeaponType.values();
        WeaponType randomType = weaponTypes[random.nextInt(weaponTypes.length)];

        // 随机选择词条（六娃葫芦籽没有词条）
        EnhancementType randomEnhancement = null;
        float value = 0f;

        if (randomType != WeaponType.LIUWA_HULUZI) {
            EnhancementType[] enhancements = EnhancementType.values();
            randomEnhancement = enhancements[random.nextInt(enhancements.length)];

            // 根据词条类型设置不同的增强值范围
            switch (randomEnhancement) {
                case HUGE:
                    value = 0.1f + random.nextFloat() * 0.3f; // 10%-40%伤害提升
                    break;
                case SHINY:
                    value = 0.05f + random.nextFloat() * 0.15f; // 5%-20%冷却降低
                    break;
                case SMOOTH:
                    value = 0.1f + random.nextFloat() * 0.2f; // 10%-30%速度提升
                    break;
                case BRIGHT:
                    value = 0.1f + random.nextFloat() * 0.2f; // 10%-30%射程提升
                    break;
            }
        } else {
            // 六娃葫芦籽的值（魔力恢复）
            value = 0.5f + random.nextFloat() * 1.0f; // 0.5-1.5倍基础值
        }

        return new HuluSeed(randomType, randomEnhancement, value);
    }

    /**
     * 处理敌人死亡掉落
     */
    public void onEnemyDeath(GameObject enemy) {
        // 30%概率掉落葫芦籽
        if (random.nextFloat() < 0.3f) {
            HuluSeed droppedSeed = generateRandomHuluSeed();

            // 在实际游戏中，这里应该创建一个可拾取的道具对象
            // 现在直接让玩家拾取
            pickUpHuluSeed(droppedSeed);
        }
    }

    // Getters

    /**
     * 获取当前装备的武器
     */
    public Map<WeaponType, HuluSeed> getEquippedWeapons() {
        return new HashMap<>(equippedWeapons);
    }

    /**
     * 检查是否装备了特定类型的葫芦籽
     */
    public boolean hasWeapon(WeaponType type) {
        return equippedWeapons.containsKey(type);
    }

    /**
     * 获取特定葫芦籽的增强值
     */
    public float getWeaponEnhancementValue(WeaponType type) {
        HuluSeed seed = equippedWeapons.get(type);
        return seed != null ? seed.value : 0f;
    }

    /**
     * 获取装备的葫芦籽数量
     */
    public int getEquippedWeaponCount() {
        return equippedWeapons.size();
    }
}
