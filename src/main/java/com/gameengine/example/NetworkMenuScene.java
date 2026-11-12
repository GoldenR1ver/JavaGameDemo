package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.net.Protocol;
import com.gameengine.scene.Scene;

/**
 * 网络菜单：作为主机启动，或作为客户端加入。
 * 服务器地址/端口在此集中配置，避免各处硬编码 7776/7777 不一致。
 */
public class NetworkMenuScene extends Scene {

    public enum NetworkOption {
        HOST_SERVER,
        JOIN_CLIENT,
        BACK
    }

    private final IRenderer renderer;
    private final InputManager inputManager;
    private final GameEngine engine;
    private int selectedIndex;
    private final NetworkOption[] options;
    private boolean transitioning;

    private final String serverAddress = Protocol.DEFAULT_HOST;
    private final int serverPort = Protocol.DEFAULT_PORT;

    public NetworkMenuScene(GameEngine engine) {
        super("NetworkMenu");
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.inputManager = InputManager.getInstance();
        this.selectedIndex = 0;
        this.options = new NetworkOption[]{NetworkOption.HOST_SERVER, NetworkOption.JOIN_CLIENT, NetworkOption.BACK};
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

        if (inputManager.isKeyJustPressed(GameConfig.KEY_UP_ARROW)) {
            selectedIndex = (selectedIndex - 1 + options.length) % options.length;
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_DOWN_ARROW)) {
            selectedIndex = (selectedIndex + 1) % options.length;
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_ENTER) || inputManager.isKeyJustPressed(GameConfig.KEY_SPACE)) {
            select(options[selectedIndex]);
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_ESCAPE)) {
            returnToMainMenu();
        }

        if (inputManager.isMouseButtonJustPressed(0) && renderer != null) {
            Vector2 mousePos = inputManager.getMousePosition();
            float centerY = renderer.getHeight() / 2.0f;
            float[] ys = {centerY - 80, centerY, centerY + 80};
            for (int i = 0; i < ys.length; i++) {
                if (mousePos.y >= ys[i] - 30 && mousePos.y <= ys[i] + 30) {
                    selectedIndex = i;
                    select(options[i]);
                    return;
                }
            }
        }
    }

    private void select(NetworkOption option) {
        transitioning = true;
        switch (option) {
            case HOST_SERVER:
                engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.SERVER,
                        serverAddress, serverPort));
                break;
            case JOIN_CLIENT:
                engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.CLIENT,
                        serverAddress, serverPort));
                break;
            case BACK:
                returnToMainMenu();
                break;
        }
    }

    private void returnToMainMenu() {
        transitioning = true;
        engine.setScene(new MenuScene(engine, "MainMenu"));
    }

    @Override
    public void render() {
        if (renderer == null) return;

        int width = renderer.getWidth();
        int height = renderer.getHeight();
        renderer.drawRect(0, 0, width, height, 0.25f, 0.25f, 0.35f, 1.0f);
        super.render();

        float centerX = width / 2.0f;
        float centerY = height / 2.0f;

        String title = "NETWORK GAME";
        float titleWidth = title.length() * 20.0f;
        renderer.drawRect(centerX - titleWidth / 2.0f - 20, 80, titleWidth + 40, 80, 0.4f, 0.4f, 0.5f, 1.0f);
        renderer.drawText(centerX - titleWidth / 2.0f, 120, title, 1.0f, 1.0f, 1.0f, 1.0f);

        String[] labels = {"HOST SERVER", "JOIN CLIENT", "BACK"};
        for (int i = 0; i < options.length; i++) {
            String text = labels[i];
            float textWidth = text.length() * 20.0f;
            float textX = centerX - textWidth / 2.0f;
            float textY = centerY - 80.0f + i * 80.0f;

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

        String info = "SERVER: " + serverAddress + ":" + serverPort;
        renderer.drawText(centerX - info.length() * 8.0f, height - 150, info, 0.7f, 0.7f, 1.0f, 1.0f);

        String hint1 = "UP/DOWN + ENTER  OR  MOUSE";
        renderer.drawText(centerX - hint1.length() * 8.0f, height - 100, hint1, 0.6f, 0.6f, 0.6f, 1.0f);
        String hint2 = "ESC TO RETURN";
        renderer.drawText(centerX - hint2.length() * 8.0f, height - 70, hint2, 0.6f, 0.6f, 0.6f, 1.0f);
    }
}
