package com.gameengine.core;

import com.gameengine.config.GameConfig;
import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.graphics.RendererFactory;
import com.gameengine.input.InputManager;
import com.gameengine.recording.RecordingService;
import com.gameengine.scene.Scene;

/**
 * 游戏引擎核心类：管理游戏主循环、场景切换、系统更新和渲染。
 * 协调所有子系统（物理、输入、渲染）的运行。
 *
 * 线程模型：引擎只在创建它的线程（主线程/渲染线程）上运行。
 * 网络/录制等后台线程不得直接修改场景，只通过线程安全队列交换数据。
 */
public class GameEngine {
    private final IRenderer renderer;
    private final InputManager inputManager;
    private Scene currentScene;
    private PhysicsSystem physicsSystem;
    private boolean running;
    private float targetFPS;
    private float deltaTime;
    private long lastTime;
    private final String title;

    private RecordingService recordingService;
    /** 场景切换期间请求开始录制：等新场景初始化完成后再真正 start */
    private boolean pendingRecordingStart;
    /** 延迟到本帧末尾再切换场景，避免在旧场景 update 的调用栈中途销毁旧场景 */
    private Scene pendingScene;
    private boolean cleanedUp;

    public GameEngine(int width, int height, String title) {
        this(width, height, title, RenderBackend.GPU);
    }

    public GameEngine(int width, int height, String title, RenderBackend backend) {
        this.title = title;
        this.renderer = RendererFactory.createRenderer(backend, width, height, title);
        this.inputManager = InputManager.getInstance();
        this.running = false;
        this.targetFPS = GameConfig.TARGET_FPS;
        this.deltaTime = 0.0f;
        this.lastTime = System.nanoTime();
    }

    public boolean initialize() {
        return true;
    }

    // 启动游戏主循环
    public void run() {
        if (!initialize()) {
            System.err.println("游戏引擎初始化失败");
            return;
        }

        running = true;
        lastTime = System.nanoTime();

        long lastFrameTime = System.nanoTime();
        long frameTimeNanos = (long) (1_000_000_000.0 / targetFPS);

        while (running) {
            long currentTime = System.nanoTime();

            // 基于目标FPS进行状态更新
            if (currentTime - lastFrameTime >= frameTimeNanos) {
                pollEvents();
                applyPendingScene();
                update();
                if (running) {
                    render();
                }
                lastFrameTime = currentTime;
            }

            // 检查窗口关闭请求
            if (renderer.shouldClose()) {
                running = false;
            }

            // 让出CPU，避免过度占用（渲染线程已开启 vsync，这里只是兜底）
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 更新游戏状态：处理输入、更新场景、物理系统和录制服务
     */
    private void update() {
        // 计算帧间隔时间：单帧最大 50ms，避免卡顿后物理"瞬移"/穿模
        long currentTime = System.nanoTime();
        deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
        lastTime = currentTime;
        if (deltaTime < 0f) deltaTime = 0f;
        if (deltaTime > GameConfig.MAX_DELTA_TIME) deltaTime = GameConfig.MAX_DELTA_TIME;

        // 更新场景
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }

        // 更新物理系统
        if (physicsSystem != null) {
            physicsSystem.update(deltaTime);
        }

        // 更新录制服务
        if (recordingService != null && recordingService.isRecording()) {
            recordingService.update(deltaTime, currentScene, inputManager);
        }

        inputManager.update();

        // 注意：ESC 不再由引擎全局处理，否则会抢在场景之前把游戏直接退出，
        // 导致"ESC 返回菜单"永远不生效。各场景自行决定 ESC 的语义。
    }

    /**
     * 渲染当前帧
     */
    private void render() {
        if (renderer == null) return;

        renderer.beginFrame();

        if (currentScene != null) {
            currentScene.render();
        }

        renderer.endFrame();
    }

    private void pollEvents() {
        renderer.pollEvents();
    }

    private void applyPendingScene() {
        if (pendingScene == null) return;
        Scene next = pendingScene;
        pendingScene = null;
        applyScene(next);
    }

    /**
     * 切换场景。
     * 运行中调用会被延迟到本帧末尾执行，避免"在旧场景 update 里销毁旧场景"。
     */
    public void setScene(Scene scene) {
        if (running) {
            this.pendingScene = scene;
        } else {
            applyScene(scene);
        }
    }

    private void applyScene(Scene scene) {
        // 切换场景时停止录制，避免把菜单帧/空帧写进同一个文件
        stopRecordingQuietly();

        if (physicsSystem != null) {
            physicsSystem.cleanup();
            physicsSystem = null;
        }
        if (currentScene != null && currentScene != scene) {
            currentScene.clear();
        }

        this.currentScene = scene;
        if (scene == null) return;

        // 场景切换后回填引擎引用
        scene.setEngine(this);
        scene.initialize();

        if (scene.usesPhysics()) {
            physicsSystem = new PhysicsSystem(scene, renderer.getWidth(), renderer.getHeight());
        }

        // 新场景就绪后再开始录制，避免录到上个场景的残余帧
        if (pendingRecordingStart && recordingService != null) {
            pendingRecordingStart = false;
            try {
                recordingService.start(scene, renderer.getWidth(), renderer.getHeight());
            } catch (Exception e) {
                System.err.println("录制启动失败: " + e.getMessage());
            }
        }
    }

    public Scene getCurrentScene() {
        return currentScene;
    }

    public void stop() {
        running = false;
    }

    /**
     * 清理资源：停止录制、清理物理系统、场景和渲染器。
     * 幂等，允许被 ESC 分支和 main 的 finally 重复调用。
     */
    public void cleanup() {
        if (cleanedUp) return;
        cleanedUp = true;
        running = false;
        stopRecordingQuietly();
        if (physicsSystem != null) {
            physicsSystem.cleanup();
            physicsSystem = null;
        }
        if (currentScene != null) {
            currentScene.clear();
            currentScene = null;
        }
        if (renderer != null) {
            renderer.cleanup();
        }
    }

    /**
     * 启用录制服务
     */
    public void enableRecording(RecordingService service) {
        this.recordingService = service;
        if (service == null) return;

        // 场景切换尚未落地（例如菜单同一帧内 setScene + enableRecording）：
        // 等 applyScene 初始化完新场景后再开始，否则会立刻被下一次场景切换停掉。
        if (pendingScene != null || currentScene == null) {
            pendingRecordingStart = true;
            return;
        }

        try {
            service.start(currentScene, renderer.getWidth(), renderer.getHeight());
        } catch (Exception e) {
            System.err.println("录制启动失败: " + e.getMessage());
        }
    }

    /**
     * 禁用录制服务
     */
    public void disableRecording() {
        stopRecordingQuietly();
        recordingService = null;
        pendingRecordingStart = false;
    }

    private void stopRecordingQuietly() {
        if (recordingService != null && recordingService.isRecording()) {
            try {
                recordingService.stop();
            } catch (Exception e) {
                System.err.println("录制停止失败: " + e.getMessage());
            }
        }
    }

    public IRenderer getRenderer() {
        return renderer;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public float getDeltaTime() {
        return deltaTime;
    }

    public void setTargetFPS(float fps) {
        this.targetFPS = fps > 1f ? fps : 1f;
    }

    public float getTargetFPS() {
        return targetFPS;
    }

    public boolean isRunning() {
        return running;
    }

    public String getTitle() {
        return title;
    }
}
