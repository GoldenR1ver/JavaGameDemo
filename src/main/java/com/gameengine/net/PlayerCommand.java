package com.gameengine.net;

/**
 * 玩家一帧的输入命令（客户端 -> 服务器）。
 * 不可变对象，可安全跨线程传递。
 */
public final class PlayerCommand {
    public long sequence;
    /** 归一化方向（-1..1），服务器负责换算成速度 */
    public float moveX;
    public float moveY;
    public boolean melee;
    public boolean ranged;
    public boolean dodge;
    /** 瞄准点（世界坐标） */
    public float aimX;
    public float aimY;

    public PlayerCommand() {
        this(0, 0, 0, false, false, false, 0, 0);
    }

    public PlayerCommand(long sequence, float moveX, float moveY,
                         boolean melee, boolean ranged, boolean dodge,
                         float aimX, float aimY) {
        this.sequence = sequence;
        this.moveX = moveX;
        this.moveY = moveY;
        this.melee = melee;
        this.ranged = ranged;
        this.dodge = dodge;
        this.aimX = aimX;
        this.aimY = aimY;
    }

    public boolean isMoving() {
        return moveX != 0f || moveY != 0f;
    }

    @Override
    public String toString() {
        return "PlayerCommand{seq=" + sequence + ", move=(" + moveX + "," + moveY
                + "), melee=" + melee + ", ranged=" + ranged + ", dodge=" + dodge + "}";
    }
}
