package com.gameengine.input;

import com.gameengine.math.Vector2;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 输入管理器：单例模式，管理键盘和鼠标输入状态。
 *
 * 线程模型：GLFW 的回调在渲染线程（也是主线程）触发，但网络线程/录制线程
 * 也可能读取输入状态，因此这里使用并发容器，避免 HashSet 并发读写导致的
 * ConcurrentModificationException 或数据竞争。
 */
public class InputManager {
    private static volatile InputManager instance;

    private final Set<Integer> pressedKeys;
    private final Set<Integer> justPressedKeys;

    /** 鼠标位置由回调线程写、游戏线程读，使用 volatile 保证可见性 */
    private volatile float mouseX;
    private volatile float mouseY;

    private final boolean[] mouseButtons;
    private final boolean[] mouseButtonsJustPressed;

    private InputManager() {
        pressedKeys = ConcurrentHashMap.newKeySet();
        justPressedKeys = ConcurrentHashMap.newKeySet();
        mouseButtons = new boolean[3];
        mouseButtonsJustPressed = new boolean[3];
    }

    public static InputManager getInstance() {
        InputManager local = instance;
        if (local == null) {
            synchronized (InputManager.class) {
                if (instance == null) {
                    instance = new InputManager();
                }
                local = instance;
            }
        }
        return local;
    }

    /**
     * 每帧结束调用：清除"刚按下"的瞬时状态。
     */
    public void update() {
        justPressedKeys.clear();
        for (int i = 0; i < mouseButtonsJustPressed.length; i++) {
            mouseButtonsJustPressed[i] = false;
        }
    }

    public void onKeyPressed(int keyCode) {
        if (!pressedKeys.contains(keyCode)) {
            justPressedKeys.add(keyCode);
        }
        pressedKeys.add(keyCode);
    }

    public void onKeyReleased(int keyCode) {
        pressedKeys.remove(keyCode);
    }

    public void onMouseMoved(float x, float y) {
        mouseX = x;
        mouseY = y;
    }

    public void onMousePressed(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            if (!mouseButtons[button]) {
                mouseButtonsJustPressed[button] = true;
            }
            mouseButtons[button] = true;
        }
    }

    public void onMouseReleased(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            mouseButtons[button] = false;
        }
    }

    public boolean isKeyPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public boolean isKeyJustPressed(int keyCode) {
        return justPressedKeys.contains(keyCode);
    }

    public boolean isMouseButtonPressed(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            return mouseButtons[button];
        }
        return false;
    }

    public boolean isMouseButtonJustPressed(int button) {
        if (button >= 0 && button < mouseButtons.length) {
            return mouseButtonsJustPressed[button];
        }
        return false;
    }

    public boolean isAnyKeyJustPressed() {
        return !justPressedKeys.isEmpty();
    }

    public boolean isAnyKeyPressed() {
        return !pressedKeys.isEmpty();
    }

    public Set<Integer> getJustPressedKeysSnapshot() {
        return new java.util.HashSet<>(justPressedKeys);
    }

    public Vector2 getMousePosition() {
        return new Vector2(mouseX, mouseY);
    }

    public float getMouseX() {
        return mouseX;
    }

    public float getMouseY() {
        return mouseY;
    }

    /** 窗口失去焦点时清空按键，防止"卡键" */
    public void clearAll() {
        pressedKeys.clear();
        justPressedKeys.clear();
        for (int i = 0; i < mouseButtons.length; i++) {
            mouseButtons[i] = false;
            mouseButtonsJustPressed[i] = false;
        }
    }
}
