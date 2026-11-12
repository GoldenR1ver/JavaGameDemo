package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.recording.RecordingConfig;
import com.gameengine.recording.RecordingService;
import com.gameengine.scene.Scene;

import java.io.File;

/**
 * 主菜单场景：开始游戏、回放、联机、退出。
 * 支持键盘（GLFW 键码）和鼠标操作。
 */
public class MenuScene extends Scene {
    public enum MenuOption {
        START_GAME,
        REPLAY,
        NETWORK,
        EXIT
    }

    private final IRenderer renderer;
    private final InputManager inputManager;
    private final GameEngine engine;
    private int selectedIndex;
    private final MenuOption[] options;
    private boolean transitioning;

    public MenuScene(GameEngine engine, String name) {
        super(name);
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.inputManager = InputManager.getInstance();
        this.selectedIndex = 0;
        this.options = new MenuOption[]{MenuOption.START_GAME, MenuOption.REPLAY, MenuOption.NETWORK, MenuOption.EXIT};
        this.transitioning = false;
    }

    @Override
    public boolean usesPhysics() {
        return false;
    }

    @Override
    public void initialize() {
        super.initialize();
        selectedIndex = 0;
        transitioning = false;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        if (transitioning) return;
        handleKeyboard();
        handleMouse();
    }

    private void handleKeyboard() {
        if (inputManager.isKeyJustPressed(GameConfig.KEY_ESCAPE)) {
            select(MenuOption.EXIT);
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_UP_ARROW)) {
            selectedIndex = (selectedIndex - 1 + options.length) % options.length;
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_DOWN_ARROW)) {
            selectedIndex = (selectedIndex + 1) % options.length;
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_ENTER) || inputManager.isKeyJustPressed(GameConfig.KEY_SPACE)) {
            select(options[selectedIndex]);
        }
    }

    private void handleMouse() {
        if (!inputManager.isMouseButtonJustPressed(0) || renderer == null) return;

        Vector2 mousePos = inputManager.getMousePosition();
        float centerY = renderer.getHeight() / 2.0f;
        float[] ys = {centerY - 120, centerY - 40, centerY + 40, centerY + 120};

        for (int i = 0; i < ys.length; i++) {
            if (mousePos.y >= ys[i] - 30 && mousePos.y <= ys[i] + 30) {
                selectedIndex = i;
                select(options[i]);
                return;
            }
        }
    }

    private void select(MenuOption option) {
        transitioning = true;
        switch (option) {
            case START_GAME:
                engine.disableRecording();
                engine.setScene(new GameScene(engine));
                startRecording();
                break;
            case REPLAY:
                engine.disableRecording();
                engine.setScene(new ReplayScene(engine, null));
                break;
            case NETWORK:
                engine.disableRecording();
                engine.setScene(new NetworkMenuScene(engine));
                break;
            case EXIT:
                engine.stop();
                engine.cleanup();
                System.exit(0);
                break;
        }
    }

    private void startRecording() {
        try {
            File dir = new File("recordings");
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("无法创建 recordings 目录，跳过录制");
                return;
            }
            String path = "recordings/session_" + System.currentTimeMillis() + ".jsonl";
            RecordingService svc = new RecordingService(new RecordingConfig(path));
            engine.enableRecording(svc);
        } catch (Exception e) {
            System.err.println("录制启动失败: " + e.getMessage());
        }
    }

    @Override
    public void render() {
        if (renderer == null) return;

        int width = renderer.getWidth();
        int height = renderer.getHeight();
        renderer.drawRect(0, 0, width, height, 0.25f, 0.25f, 0.35f, 1.0f);
        super.render();
        renderMainMenu();
    }

    private void renderMainMenu() {
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;

        String title = "HULU BATTLE";
        float titleWidth = title.length() * 20.0f;
        renderer.drawRect(centerX - titleWidth / 2.0f - 20, 80, titleWidth + 40, 80, 0.4f, 0.4f, 0.5f, 1.0f);
        renderer.drawText(centerX - titleWidth / 2.0f, 120, title, 1.0f, 1.0f, 1.0f, 1.0f);

        String[] labels = {"START GAME", "REPLAY", "NETWORK", "EXIT"};
        for (int i = 0; i < options.length; i++) {
            String text = labels[i];
            float textWidth = text.length() * 20.0f;
            float textX = centerX - textWidth / 2.0f;
            float textY = centerY - 120.0f + i * 80.0f;

            float r = 0.95f, g = 0.95f, b = 0.95f;
            if (i == selectedIndex) {
                r = 1.0f;
                g = 1.0f;
                b = 0.5f;
                renderer.drawRect(textX - 20, textY - 20, textWidth + 40, 50, 0.6f, 0.5f, 0.2f, 0.9f);
            } else {
                renderer.drawRect(textX - 20, textY - 20, textWidth + 40, 50, 0.2f, 0.2f, 0.3f, 0.5f);
            }
            renderer.drawText(textX, textY, text, r, g, b, 1.0f);
        }

        String hint1 = "UP/DOWN + ENTER  OR  MOUSE";
        renderer.drawText(centerX - hint1.length() * 10.0f, height - 100, hint1, 0.6f, 0.6f, 0.6f, 1.0f);
        String hint2 = "ESC TO QUIT";
        renderer.drawText(centerX - hint2.length() * 10.0f, height - 70, hint2, 0.6f, 0.6f, 0.6f, 1.0f);
    }
}
