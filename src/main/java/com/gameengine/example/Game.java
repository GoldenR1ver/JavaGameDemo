package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.net.Protocol;

/**
 * 游戏入口点：单机 / 服务器 / 客户端。
 *
 * 用法：
 *   java ... Game              单机
 *   java ... Game server       服务器（监听默认端口）
 *   java ... Game client [host] [port]
 *
 * 注意：服务器只创建一个 GameServer（在 NetworkGameScene 内），
 * 不再额外启动一个"空服务器"占端口。
 */
public class Game {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");

        String mode = "standalone";
        String host = Protocol.DEFAULT_HOST;
        int port = Protocol.DEFAULT_PORT;

        if (args.length > 0) {
            mode = args[0].toLowerCase();
        }
        if (args.length > 1) {
            host = args[1];
        }
        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("无效端口: " + args[2] + "，使用默认 " + Protocol.DEFAULT_PORT);
            }
        }

        System.out.println("启动模式: " + mode + "，渲染后端: GPU");

        GameEngine engine = null;
        try {
            String title = GameConfig.GAME_TITLE;
            if ("server".equals(mode)) {
                title = title + " - 主机";
            } else if ("client".equals(mode)) {
                title = title + " - 客户端";
            }

            engine = new GameEngine(GameConfig.WINDOW_WIDTH, GameConfig.WINDOW_HEIGHT,
                    title, RenderBackend.GPU);

            switch (mode) {
                case "server":
                    engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.SERVER, host, port));
                    break;
                case "client":
                    engine.setScene(new NetworkGameScene(engine, NetworkGameScene.NetworkMode.CLIENT, host, port));
                    break;
                default:
                    engine.setScene(new MenuScene(engine, "MainMenu"));
                    break;
            }

            engine.run();
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (engine != null) {
                engine.cleanup();
            }
        }

        System.out.println("游戏结束");
    }
}
