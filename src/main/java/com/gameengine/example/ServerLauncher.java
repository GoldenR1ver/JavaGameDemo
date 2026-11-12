package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.net.Protocol;

/**
 * 服务器启动入口（带图形界面的主机）。
 * 用法：ServerLauncher [port]
 */
public class ServerLauncher {

    public static void main(String[] args) {
        System.out.println("启动游戏服务器...");

        int port = Protocol.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号: " + args[0] + "，使用默认端口 " + Protocol.DEFAULT_PORT);
            }
        }

        GameEngine engine = null;
        try {
            engine = new GameEngine(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT,
                    GameConfig.GAME_TITLE + " - 主机", RenderBackend.GPU);

            // 只启动一个服务器实例（由场景内部创建），避免两个服务器抢同一个端口
            engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.SERVER,
                    Protocol.DEFAULT_HOST, port));
            engine.run();
        } catch (Exception e) {
            System.err.println("服务器运行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (engine != null) {
                engine.cleanup();
            }
        }

        System.out.println("服务器已关闭");
    }
}
