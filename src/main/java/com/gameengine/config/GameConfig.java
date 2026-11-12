package com.gameengine.config;

/**
 * Ã¦Â¸Â¸Ã¦Ë†ÂÃ©â€¦ÂÃ§Â½Â®Ã¥Â¸Â¸Ã©â€¡ÂÃ§Â±Â»Ã£â‚¬â€š
 *
 * Ã§ÂºÂ¦Ã¥Â®Å¡Ã¯Â¼Å¡Ã¦â€°â‚¬Ã¦Å“â€°"Ã©Â­â€Ã¦Â³â€¢Ã¦â€¢Â°Ã¥Â­â€”"Ã©â€ºâ€ Ã¤Â¸Â­Ã¥Å“Â¨Ã¨Â¿â„¢Ã©â€¡Å’Ã¯Â¼Å’Ã§Â¦ÂÃ¦Â­Â¢Ã¥Å“Â¨Ã¥Ââ€žÃ¤Â¸ÂªÃ¥Å“ÂºÃ¦â„¢Â¯/Ã§Â»â€žÃ¤Â»Â¶Ã©â€¡Å’Ã§Â¡Â¬Ã§Â¼â€“Ã§Â ÂÃ£â‚¬â€š
 * Ã©â€Â®Ã¥â‚¬Â¼Ã¦Â³Â¨Ã¦â€žÂÃ¯Â¼Å¡Ã¦Å“Â¬Ã©Â¡Â¹Ã§â€ºÂ®Ã¤Â½Â¿Ã§â€Â¨ LWJGL/GLFW Ã§Å¡â€žÃ©â€Â®Ã§Â ÂÃ¯Â¼Ë†GLFW_KEY_*Ã¯Â¼â€°Ã¯Â¼Å’
 * Ã¤Â¸ÂÃ¨Â¦ÂÃ¥â€ ÂÃ¦Â·Â·Ã§â€Â¨ AWT Ã§Å¡â€ž KeyEvent.VK_*Ã¯Â¼Å’Ã¥ÂÂ¦Ã¥Ë†â„¢Ã©â€Â®Ã§â€ºËœÃ¥Å“Â¨ GPU Ã¥ÂÅ½Ã§Â«Â¯Ã¤Â¸â€¹Ã¤Â¼Å¡Ã¥Â¤Â±Ã¦â€¢Ë†Ã£â‚¬â€š
 */
public final class GameConfig {
    private GameConfig() {} // Ã©ËœÂ²Ã¦Â­Â¢Ã¥Â®Å¾Ã¤Â¾â€¹Ã¥Å’â€“

    // Ã¦Â¸Â¸Ã¦Ë†ÂÃ§Âªâ€”Ã¥ÂÂ£Ã¨Â®Â¾Ã§Â½Â®
    public static final int WINDOW_WIDTH = 1920;
    public static final int WINDOW_HEIGHT = 1080;
    public static final String GAME_TITLE = "Ã¨â€˜Â«Ã¨Å Â¦Ã¥Â¨Æ’Ã¥Â¤Â§Ã¦Ë†ËœÃ¥Â¦â€“Ã§Â²Â¾";

    // Ã¦Â¸Â¸Ã¦Ë†ÂÃ¨Â¾Â¹Ã§â€¢Å’
    public static final int GAME_BOUNDS_LEFT = 0;
    public static final int GAME_BOUNDS_TOP = 0;
    public static final int GAME_BOUNDS_RIGHT = WINDOW_WIDTH;
    public static final int GAME_BOUNDS_BOTTOM = WINDOW_HEIGHT;

    // UIÃ¨Â®Â¾Ã§Â½Â®
    public static final int SKILL_BAR_Y = WINDOW_HEIGHT - 80;
    public static final int SKILL_ICON_SIZE = 60;
    public static final int SKILL_ICON_SPACING = 80;
    public static final int SKILL_BAR_CENTER_X = WINDOW_WIDTH / 2;

    // ---------------- Ã©â€Â®Ã¤Â½ÂÃ¯Â¼Ë†GLFW Ã©â€Â®Ã§Â ÂÃ¯Â¼â€° ----------------
    public static final int KEY_ESCAPE = 256;      // GLFW_KEY_ESCAPE
    public static final int KEY_ENTER = 257;       // GLFW_KEY_ENTER
    public static final int KEY_SPACE = 32;
    public static final int KEY_BACKSPACE = 259;   // GLFW_KEY_BACKSPACE
    public static final int KEY_UP_ARROW = 265;    // GLFW_KEY_UP
    public static final int KEY_DOWN_ARROW = 264;  // GLFW_KEY_DOWN
    public static final int KEY_LEFT_ARROW = 263;  // GLFW_KEY_LEFT
    public static final int KEY_RIGHT_ARROW = 262; // GLFW_KEY_RIGHT

    // Ã¦Å â‚¬Ã¨Æ’Â½Ã©â€Â®Ã¤Â½Â
    public static final int KEY_MELEE = 90;    // ZÃ©â€Â®
    public static final int KEY_RANGED = 88;   // XÃ©â€Â®
    public static final int KEY_DODGE = 67;    // CÃ©â€Â®
    public static final int KEY_RESTART = 82;  // RÃ©â€Â®
    public static final int KEY_QUIT = 81;     // QÃ©â€Â®

    // Ã§Â§Â»Ã¥Å Â¨Ã©â€Â®Ã¤Â½Â
    public static final int KEY_UP = 87;       // WÃ©â€Â®
    public static final int KEY_DOWN = 83;     // SÃ©â€Â®
    public static final int KEY_LEFT = 65;     // AÃ©â€Â®
    public static final int KEY_RIGHT = 68;    // DÃ©â€Â®

    // ---------------- Ã¤Â¸Â»Ã¥Â¾ÂªÃ§Å½Â¯ ----------------
    /** Ã¥Ââ€¢Ã¥Â¸Â§Ã¦Å“â‚¬Ã¥Â¤Â§Ã¦â€”Â¶Ã©â€”Â´Ã¦Â­Â¥Ã©â€¢Â¿Ã¯Â¼Å’Ã©ËœÂ²Ã¦Â­Â¢Ã¥ÂÂ¡Ã©Â¡Â¿Ã¥ÂÅ½Ã§â€°Â©Ã§Ââ€ "Ã§Å¾Â¬Ã§Â§Â»" */
    public static final float MAX_DELTA_TIME = 0.05f;
    /** Ã§â€ºÂ®Ã¦Â â€¡Ã¥Â¸Â§Ã§Å½â€¡ */
    public static final float TARGET_FPS = 60f;

    // ---------------- Ã¨Â¡â‚¬Ã©â€¡ÂÃ©â€¦ÂÃ§Â½Â® ----------------
    public static final float PLAYER_MAX_HEALTH = 100f;
    public static final float ENEMY_BASE_HEALTH = 50f;
    public static final float ENEMY_HEALTH_GROWTH = 10f; // Ã¦Â¯ÂÃ¦Â³Â¢Ã¨Â¡â‚¬Ã©â€¡ÂÃ¥Â¢Å¾Ã©â€¢Â¿
    public static final int ENEMY_BASE_COUNT = 5; // Ã¥Å¸ÂºÃ§Â¡â‚¬Ã¦â€¢Å’Ã¤ÂºÂºÃ¦â€¢Â°Ã©â€¡Â
    public static final int ENEMY_COUNT_GROWTH = 2; // Ã¦Â¯ÂÃ¦Â³Â¢Ã¦â€¢Å’Ã¤ÂºÂºÃ¦â€¢Â°Ã©â€¡ÂÃ¥Â¢Å¾Ã©â€¢Â¿
    public static final float WAVE_INTERVAL = 30f; // Ã¦Â³Â¢Ã¦Â¬Â¡Ã©â€”Â´Ã©Å¡â€Ã¦â€”Â¶Ã©â€”Â´Ã¯Â¼Ë†Ã§Â§â€™Ã¯Â¼â€°
    /** AI Ã¦â€¢Â°Ã©â€¡ÂÃ¤Â¸Å Ã©â„¢ÂÃ¯Â¼Å’Ã©ÂÂ¿Ã¥â€¦ÂÃ¦â€”Â Ã©â„¢ÂÃ¥Ë†Â·Ã¦â‚¬ÂªÃ¥Â¯Â¼Ã¨â€¡Â´Ã¥â€ â€¦Ã¥Â­Ëœ/Ã§ÂºÂ¿Ã§Â¨â€¹Ã§Ë†â€ Ã§â€šÂ¸ */
    public static final int MAX_AI_PLAYERS = 40;
    /** GameScene Ã¥Ë†ÂÃ¥Â§â€¹ AI Ã¦â€¢Â°Ã©â€¡Â */
    public static final int INITIAL_AI_PLAYERS = 30;
    /** Ã¦Â¯ÂÃ©Å¡â€Ã¥Â¤Å¡Ã¥Â°â€˜Ã§Â§â€™Ã¨Â¡Â¥Ã¥â€¦â€¦Ã¤Â¸â‚¬Ã¤Â¸Âª AI */
    public static final float AI_SPAWN_INTERVAL = 1.0f;

    // Ã¤Â¼Â¤Ã¥Â®Â³Ã©â€¦ÂÃ§Â½Â®
    public static final float PLAYER_COLLISION_DAMAGE = 10f; // Ã§Å½Â©Ã¥Â®Â¶Ã§Â¢Â°Ã¦â€™Å¾Ã¤Â¼Â¤Ã¥Â®Â³
    public static final float MELEE_ATTACK_DAMAGE = 50f; // Ã¨Â¿â€˜Ã¦Ë†ËœÃ¦â€Â»Ã¥â€¡Â»Ã¤Â¼Â¤Ã¥Â®Â³
    public static final float RANGED_ATTACK_DAMAGE = 30f; // Ã¨Â¿Å“Ã§Â¨â€¹Ã¦â€Â»Ã¥â€¡Â»Ã¤Â¼Â¤Ã¥Â®Â³

    // Ã¦â€”Â Ã¦â€¢Å’Ã¦â€”Â¶Ã©â€”Â´Ã©â€¦ÂÃ§Â½Â®
    public static final float PLAYER_INVULNERABLE_TIME = 1.0f; // Ã§Å½Â©Ã¥Â®Â¶Ã¦â€”Â Ã¦â€¢Å’Ã¦â€”Â¶Ã©â€”Â´

    // ---------------- Ã§Â§Â»Ã¥Å Â¨ ----------------
    public static final float PLAYER_MOVE_SPEED = 200f;
    public static final float AI_MOVE_SPEED = 150f;
    public static final float AI_AVOID_RADIUS = 80f;

    // Ã©Â­â€Ã¥Å â€ºÃ§Â³Â»Ã§Â»Å¸Ã©â€¦ÂÃ§Â½Â®
    public static final float PLAYER_MAX_MANA = 100f;
    public static final float MANA_REGEN_RATE = 25f; // Ã¦Â¯ÂÃ§Â§â€™Ã¥â€ºÅ¾Ã¥Â¤Â25Ã§â€šÂ¹Ã©Â­â€Ã¥Å â€º
    public static final float MANA_REGEN_DELAY = 3.0f; // 3Ã§Â§â€™Ã¥ÂÅ½Ã¥Â¼â‚¬Ã¥Â§â€¹Ã¥Â¿Â«Ã©â‚¬Å¸Ã¥â€ºÅ¾Ã¥Â¤Â

    // Ã¨Â¿â€˜Ã¦Ë†ËœÃ¦â€Â»Ã¥â€¡Â»Ã©â€¦ÂÃ§Â½Â®
    public static final float MELEE_DAMAGE = 15f;
    public static final float MELEE_RANGE = 150f;
    public static final float MELEE_BULLET_SPEED = 5.0f;
    public static final float MELEE_COOLDOWN = 0.5f;
    public static final float MELEE_MANA_COST = 10f;
    public static final boolean MELEE_CHARGEABLE = true;
    public static final float MELEE_MAX_CHARGE_TIME = 0f;
    public static final float MELEE_CHARGE_DAMAGE_MULTIPLIER = 1.0f;
    public static final float MELEE_CHARGE_SPEED_MULTIPLIER = 1.0f;
    public static final float MELEE_ANGLE_RANGE = 60.0f; // Ã¨Â¿â€˜Ã¦Ë†ËœÃ¦â€Â»Ã¥â€¡Â»Ã¦â€°â€¡Ã¥Â½Â¢Ã¨Â§â€™Ã¥ÂºÂ¦

    // Ã¨Â¿Å“Ã§Â¨â€¹Ã¦â€Â»Ã¥â€¡Â»Ã©â€¦ÂÃ§Â½Â®
    public static final float RANGED_DAMAGE = 25f;
    public static final float RANGED_RANGE = 800f;
    public static final float RANGED_BULLET_SPEED = 300f;
    public static final float RANGED_COOLDOWN = 2.0f;
    public static final float RANGED_MANA_COST = 20f;
    public static final boolean RANGED_CHARGEABLE = true;
    public static final float RANGED_MAX_CHARGE_TIME = 2.0f;
    public static final float RANGED_CHARGE_DAMAGE_MULTIPLIER = 2.0f;
    public static final float RANGED_CHARGE_SPEED_MULTIPLIER = 2.0f;

    // Ã©â€”ÂªÃ©ÂÂ¿Ã©â€¦ÂÃ§Â½Â®
    public static final float DODGE_COOLDOWN = 8.0f;
    public static final float DODGE_MANA_COST = 30f;
    public static final float DODGE_DURATION = 0.5f;
    public static final float DODGE_SPEED_BOOST = 1.5f;

    // Ã§Â²â€™Ã¥Â­ÂÃ§Â³Â»Ã§Â»Å¸Ã©â€¦ÂÃ§Â½Â®
    public static final float MELEE_PARTICLE_SPAWN_RATE = 0.01f;
    public static final float MELEE_PARTICLE_SPEED_MIN = 100f;
    public static final float MELEE_PARTICLE_SPEED_MAX = 300f;
    public static final float MELEE_PARTICLE_LIFE_MIN = 0.3f;
    public static final float MELEE_PARTICLE_LIFE_MAX = 0.8f;
    public static final float MELEE_PARTICLE_SIZE_MIN = 2f;
    public static final float MELEE_PARTICLE_SIZE_MAX = 6f;
    public static final float MELEE_PARTICLE_R = 1.0f; // Ã¦Â©â„¢Ã¨â€°Â² - Ã§ÂºÂ¢Ã¨â€°Â²Ã¥Ë†â€ Ã©â€¡Â
    public static final float MELEE_PARTICLE_G = 0.5f; // Ã¦Â©â„¢Ã¨â€°Â² - Ã§Â»Â¿Ã¨â€°Â²Ã¥Ë†â€ Ã©â€¡Â
    public static final float MELEE_PARTICLE_B = 0.0f; // Ã¦Â©â„¢Ã¨â€°Â² - Ã¨â€œÂÃ¨â€°Â²Ã¥Ë†â€ Ã©â€¡Â
    public static final float MELEE_PARTICLE_OPACITY = 0.8f;
    public static final int MELEE_PARTICLES_PER_FRAME = 5;

    // Ã¥â€˜Â½Ã¤Â¸Â­Ã¦â€¢Ë†Ã¦Å¾Å“Ã©â€¦ÂÃ§Â½Â®
    public static final int HIT_EFFECT_PARTICLES = 8;
    public static final float HIT_EFFECT_SPEED_MIN = 50f;
    public static final float HIT_EFFECT_SPEED_MAX = 150f;
    public static final float HIT_EFFECT_LIFE_MIN = 0.3f;
    public static final float HIT_EFFECT_LIFE_MAX = 0.8f;
    public static final float HIT_EFFECT_SIZE_MIN = 2f;
    public static final float HIT_EFFECT_SIZE_MAX = 6f;

    // Ã§Â¢Â°Ã¦â€™Å¾Ã¦Â£â‚¬Ã¦Âµâ€¹Ã©â€¦ÂÃ§Â½Â®
    public static final float BULLET_COLLISION_RADIUS = 15f;
    public static final float PLAYER_COLLISION_RADIUS = 30f;

    // Ã¥â€¦Â¶Ã¤Â»â€“Ã©â€¦ÂÃ§Â½Â®
    public static final float WEAPON_PICKUP_RANGE = 30f;
    public static final float WEAPON_MAX_RANGE = (float) Math.hypot(WINDOW_WIDTH, WINDOW_HEIGHT);

    // ---------------- Ã§Â½â€˜Ã§Â»Å“ ----------------
    /** Ã©Â»ËœÃ¨Â®Â¤Ã¦Â¸Â¸Ã¦Ë†ÂÃ§Â«Â¯Ã¥ÂÂ£Ã¯Â¼Å¡Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯/Ã¦Å“ÂÃ¥Å Â¡Ã§Â«Â¯/Ã¨ÂÅ“Ã¥Ââ€¢Ã¥Â¿â€¦Ã©Â¡Â»Ã¥â€¦Â±Ã§â€Â¨Ã¨Â¿â„¢Ã¤Â¸â‚¬Ã¤Â¸ÂªÃ¥Â¸Â¸Ã©â€¡Â */
    public static final int DEFAULT_NETWORK_PORT = 7776;
    /** Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯Ã¨Â¾â€œÃ¥â€¦Â¥Ã¤Â¸Å Ã¨Â¡Å’Ã©Â¢â€˜Ã§Å½â€¡Ã¯Â¼Ë†Ã§Â§â€™/Ã¦Â¬Â¡Ã¯Â¼â€° */
    public static final float NETWORK_INPUT_INTERVAL = 1f / 30f;
    /** Ã¦Å“ÂÃ¥Å Â¡Ã¥â„¢Â¨Ã§Å Â¶Ã¦â‚¬ÂÃ¥Â¹Â¿Ã¦â€™Â­Ã©Â¢â€˜Ã§Å½â€¡Ã¯Â¼Ë†Ã§Â§â€™/Ã¦Â¬Â¡Ã¯Â¼â€° */
    public static final float NETWORK_STATE_INTERVAL = 1f / 20f;
    /** Ã¥Ââ€¢Ã¤Â¸Âª UDP/TCP Ã¨Â¡Å’Ã§Å¡â€žÃ¦Å“â‚¬Ã¥Â¤Â§Ã©â€¢Â¿Ã¥ÂºÂ¦Ã¯Â¼Å’Ã©ËœÂ²Ã¦Â­Â¢Ã¥â€ â€¦Ã¥Â­ËœÃ¥Å¾â€¹ DoS */
    public static final int NETWORK_MAX_LINE_LENGTH = 16 * 1024;
    /** Ã¥ÂÅ’Ã¦â€”Â¶Ã¥Å“Â¨Ã§ÂºÂ¿Ã§Å¡â€žÃ¦Å“â‚¬Ã¥Â¤Â§Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯Ã¦â€¢Â° */
    public static final int NETWORK_MAX_CLIENTS = 16;
    /** Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯Ã¥Ë†Â¤Ã¦Â´Â»Ã¨Â¶â€¦Ã¦â€”Â¶Ã¯Â¼Ë†Ã¦Â¯Â«Ã§Â§â€™Ã¯Â¼â€° */
    public static final long NETWORK_TIMEOUT_MS = 30_000L;
    /** Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯Ã¥Â¿Æ’Ã¨Â·Â³Ã©â€”Â´Ã©Å¡â€Ã¯Â¼Ë†Ã¦Â¯Â«Ã§Â§â€™Ã¯Â¼â€° */
    public static final long NETWORK_PING_INTERVAL_MS = 5_000L;
    /** Ã¥Â®Â¢Ã¦Ë†Â·Ã§Â«Â¯Ã¦Å“Å¸Ã¦Å“â€ºÃ§Å¡â€žÃ¦Ââ€™Ã¥â‚¬Â¼Ã¥Â¹Â³Ã¦Â»â€˜Ã§Â³Â»Ã¦â€¢Â°Ã¯Â¼Ë†0~1Ã¯Â¼Å’Ã¨Â¶Å Ã¥Â¤Â§Ã¨Â¶Å Ã¨Â·Å¸Ã¦â€°â€¹Ã¯Â¼â€° */
    public static final float NETWORK_SMOOTHING = 0.25f;
}
