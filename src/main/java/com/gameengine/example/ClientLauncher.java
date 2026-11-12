package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.net.Protocol;

/**
 * 客户端启动入口。
 * 用法：ClientLauncher [host] [port]
 */
public class ClientLauncher {

    public static void main(String[] args) {
        System.out.println("启动游戏客户端...");

        String host = Protocol.DEFAULT_HOST;
        int port = Protocol.DEFAULT_PORT;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("无效的端口号: " + args[1] + "，使用默认端口 " + Protocol.DEFAULT_PORT);
            }
        }

        System.out.println("连接到服务器: " + host + ":" + port);

        GameEngine engine = null;
        try {
            engine = new GameEngine(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT,
                    GameConfig.GAME_TITLE + " - 客户端", RenderBackend.GPU);

            // 关键修复：把 host/port 真正传给场景（原实现解析后丢弃，场景里硬编码 127.0.0.1:7776）
            engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.CLIENT, host, port));
            engine.run();
        } catch (Exception e) {
            System.err.println("客户端运行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (engine != null) {
                engine.cleanup();
            }
        }

        System.out.println("客户端结束");
    }
}
