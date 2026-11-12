package com.gameengine.input;

import com.gameengine.components.SkillComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.config.GameConfig;
import com.gameengine.core.GameObject;
import com.gameengine.math.Vector2;

/**
 * 本地玩家的技能输入控制器。
 *
 * 把"读键盘/鼠标"和"技能逻辑"分开：
 * - SkillComponent 只暴露 press/release/useSkill 这样的动作 API；
 * - 控制器负责把设备输入翻译成动作。
 *
 * 这样服务端的远端玩家实体可以被网络命令驱动，而不会误读本机键盘。
 */
public final class PlayerSkillController {
    private final InputManager input;
    private final GameObject player;

    public PlayerSkillController(GameObject player) {
        this.input = InputManager.getInstance();
        this.player = player;
    }

    public void update() {
        if (player == null || !player.isActive()) return;

        SkillComponent skill = player.getComponent(SkillComponent.class);
        TransformComponent transform = player.getComponent(TransformComponent.class);
        if (skill == null || transform == null) return;

        Vector2 aim = input.getMousePosition();
        if (aim.distance(transform.getPosition()) < 1e-3f) {
            aim = transform.getPosition().add(new Vector2(1, 0));
        }

        // 近战：按住持续攻击
        if (input.isKeyPressed(GameConfig.KEY_MELEE)) {
            skill.press(SkillComponent.SkillType.MELEE_ATTACK, aim);
        } else {
            skill.release(SkillComponent.SkillType.MELEE_ATTACK, aim);
        }

        // 远程：按住蓄力，松开释放
        if (input.isKeyPressed(GameConfig.KEY_RANGED)) {
            skill.press(SkillComponent.SkillType.RANGED_ATTACK, aim);
        } else {
            skill.release(SkillComponent.SkillType.RANGED_ATTACK, aim);
        }

        // 闪避：一次性动作
        if (input.isKeyJustPressed(GameConfig.KEY_DODGE)) {
            skill.press(SkillComponent.SkillType.DODGE, aim);
        }
    }
}
