package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.net.Protocol;
import com.gameengine.scene.Scene;

/**
 * 连接设置场景（可选功能）。
 * 目前固定使用 Protocol 中的默认地址/端口；如果需要手动输入，可在此基础上扩展。
 */
public class ServerConnectScene extends Scene {

    private final IRenderer renderer;
    private final InputManager inputManager;
    private final GameEngine engine;

    private final String serverAddress = Protocol.DEFAULT_HOST;
    private final int serverPort = Protocol.DEFAULT_PORT;
    private boolean connecting;

    public ServerConnectScene(GameEngine engine) {
        super("ServerConnect");
        this.engine = engine;
        this.renderer = engine.getRenderer();
        this.inputManager = InputManager.getInstance();
    }

    @Override
    public boolean usesPhysics() {
        return false;
    }

    @Override
    public void initialize() {
        super.initialize();
        connecting = false;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        if (connecting) return;

        if (inputManager.isKeyJustPressed(GameConfig.KEY_ENTER)) {
            connecting = true;
            engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.CLIENT,
                    serverAddress, serverPort));
        } else if (inputManager.isKeyJustPressed(GameConfig.KEY_ESCAPE)) {
            engine.setScene(new NetworkMenuScene(engine));
        }
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

        String title = "CONNECT TO SERVER";
        float titleWidth = title.length() * 20.0f;
        renderer.drawRect(centerX - titleWidth / 2.0f - 20, 80, titleWidth + 40, 80, 0.4f, 0.4f, 0.5f, 1.0f);
        renderer.drawText(centerX - titleWidth / 2.0f, 120, title, 1.0f, 1.0f, 1.0f, 1.0f);

        String serverInfo = "SERVER: " + serverAddress;
        renderer.drawText(centerX - serverInfo.length() * 8.0f, centerY - 50, serverInfo, 1.0f, 1.0f, 1.0f, 1.0f);

        String portInfo = "PORT: " + serverPort;
        renderer.drawText(centerX - portInfo.length() * 8.0f, centerY, portInfo, 1.0f, 1.0f, 1.0f, 1.0f);

        String connectText = connecting ? "CONNECTING..." : "PRESS ENTER TO CONNECT";
        renderer.drawText(centerX - connectText.length() * 8.0f, centerY + 120, connectText, 1.0f, 1.0f, 1.0f, 1.0f);

        String hint = "ESC TO RETURN";
        renderer.drawText(centerX - hint.length() * 8.0f, height - 70, hint, 0.6f, 0.6f, 0.6f, 1.0f);
    }
}
