package com.gameengine.example;

import com.gameengine.config.GameConfig;
import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;

import java.io.File;
import java.util.Arrays;

/**
 * 回放启动器：独立的回放模式入口点
 * 可以直接从命令行启动，指定录像文件路径
 */
public class ReplayLauncher {
    public static void main(String[] args) {
        String path = null;
        // 处理命令行参数
        if (args != null && args.length > 0) {
            path = args[0];
        } else {
            // 如果没有指定文件，自动选择最新的录像文件
            File dir = new File("recordings");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".jsonl"));
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
                    path = files[0].getAbsolutePath();
                    
                }
            }
        }

        if (path == null) {
            System.err.println("未找到录像文件，退出");
            return;
        }

        // 启动回放引擎
        GameEngine engine = new GameEngine(1024, 768, GameConfig.GAME_TITLE + " - 回放", RenderBackend.GPU);
        ReplayScene replay = new ReplayScene(engine, path);
        engine.setScene(replay);
        engine.run();
        engine.cleanup();
    }
}


