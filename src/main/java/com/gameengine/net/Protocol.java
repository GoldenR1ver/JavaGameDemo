package com.gameengine.net;

import com.gameengine.config.GameConfig;

import java.nio.charset.StandardCharsets;

/**
 * 网络协议常量与编解码。
 *
 * 协议：UTF-8 文本行协议，每行一条消息，以 '\n' 分隔。
 * 客户端 -> 服务器：
 *   JOIN:玩家名
 *   INPUT:序号,vx,vy,近战,远程,闪避,瞄准x,瞄准y
 *   PING
 * 服务器 -> 客户端：
 *   WELCOME:会话ID
 *   JOIN_ACK
 *   STATE:{json}
 *   PONG
 *   PLAYER_LEFT:会话ID
 *   ERROR:原因
 *
 * 集中在一个类里，避免"服务端一套格式、客户端另一套格式"的隐性耦合。
 */
public final class Protocol {
    private Protocol() {}

    public static final String CMD_WELCOME = "WELCOME";
    public static final String CMD_JOIN = "JOIN";
    public static final String CMD_JOIN_ACK = "JOIN_ACK";
    public static final String CMD_INPUT = "INPUT";
    public static final String CMD_STATE = "STATE";
    public static final String CMD_PING = "PING";
    public static final String CMD_PONG = "PONG";
    public static final String CMD_PLAYER_LEFT = "PLAYER_LEFT";
    public static final String CMD_ERROR = "ERROR";

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = GameConfig.DEFAULT_NETWORK_PORT;

    public static String welcome(String sessionId) {
        return CMD_WELCOME + ":" + sessionId;
    }

    public static String join(String playerName) {
        return CMD_JOIN + ":" + sanitizeName(playerName);
    }

    public static String joinAck() {
        return CMD_JOIN_ACK;
    }

    public static String ping() {
        return CMD_PING;
    }

    public static String pong() {
        return CMD_PONG;
    }

    public static String playerLeft(String sessionId) {
        return CMD_PLAYER_LEFT + ":" + sessionId;
    }

    public static String error(String reason) {
        return CMD_ERROR + ":" + reason;
    }

    public static String state(String json) {
        return CMD_STATE + ":" + json;
    }

    public static String input(PlayerCommand command) {
        return CMD_INPUT + ":" + command.sequence
                + "," + fmt(command.moveX)
                + "," + fmt(command.moveY)
                + "," + command.melee
                + "," + command.ranged
                + "," + command.dodge
                + "," + fmt(command.aimX)
                + "," + fmt(command.aimY);
    }

    /**
     * 解析输入行。
     * @param line 形如 "seq,vx,vy,melee,ranged,dodge,ax,ay"（不含 INPUT: 前缀）
     * @return 解析结果；格式非法时返回 null
     */
    public static PlayerCommand parseInput(String line) {
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split(",");
        if (parts.length != 8) return null;
        try {
            PlayerCommand c = new PlayerCommand();
            c.sequence = Long.parseLong(parts[0].trim());
            c.moveX = clamp(Float.parseFloat(parts[1].trim()), -1f, 1f);
            c.moveY = clamp(Float.parseFloat(parts[2].trim()), -1f, 1f);
            c.melee = Boolean.parseBoolean(parts[3].trim());
            c.ranged = Boolean.parseBoolean(parts[4].trim());
            c.dodge = Boolean.parseBoolean(parts[5].trim());
            c.aimX = finite(Float.parseFloat(parts[6].trim()));
            c.aimY = finite(Float.parseFloat(parts[7].trim()));
            return c;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 玩家名清洗：去掉控制字符/分隔符，限制长度，防止协议注入与超长行攻击。
     */
    public static String sanitizeName(String name) {
        if (name == null) return "Player";
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < name.length() && sb.length() < 24; i++) {
            char c = name.charAt(i);
            if (c == ':' || c == ',' || c == '\n' || c == '\r' || Character.isISOControl(c)) continue;
            sb.append(c);
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? "Player" : result;
    }

    /** 按 UTF-8 编码，避免平台默认字符集导致中文乱码 */
    public static byte[] encodeLine(String message) {
        return (message + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return 0f;
        return Math.max(min, Math.min(max, v));
    }

    private static float finite(float v) {
        return (Float.isNaN(v) || Float.isInfinite(v)) ? 0f : v;
    }

    private static String fmt(float v) {
        // 控制小数位，减少带宽
        return String.valueOf(Math.round(v * 100f) / 100f);
    }
}
