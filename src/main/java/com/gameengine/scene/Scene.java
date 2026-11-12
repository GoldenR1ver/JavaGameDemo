package com.gameengine.scene;

import com.gameengine.core.Component;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameObject;
import com.gameengine.graphics.IRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 场景管理类：管理所有游戏对象（GameObject）的生命周期。
 *
 * 职责边界（重要）：
 * Scene 只负责"对象容器 + 生命周期 + 渲染遍历"，不关心任何具体玩法
 * （血量、技能、AI、网络）。玩法逻辑请放在 GameScene / 各 System 中。
 */
public class Scene {
    private final String name;
    private final List<GameObject> gameObjects;   // 当前游戏对象
    private final List<GameObject> objectsToAdd;  // 待添加对象
    private final List<GameObject> objectsToRemove; // 待删除对象
    private boolean initialized;    // 场景是否初始化

    // 引擎引用：渲染器与输入系统从这里获取
    private GameEngine engine;

    public Scene(String name) {
        this.name = name;
        this.gameObjects = new ArrayList<>();
        this.objectsToAdd = new ArrayList<>();
        this.objectsToRemove = new ArrayList<>();
        this.initialized = false;
    }

    /** 由 GameEngine#setScene 调用，仅保存引用，不再顺手 new GameLogic（避免线程池泄漏） */
    public void setEngine(GameEngine engine) {
        this.engine = engine;
    }

    public GameEngine getEngine() { return engine; }

    public IRenderer getRenderer() { return engine != null ? engine.getRenderer() : null; }

    /**
     * 该场景是否需要主循环驱动物理系统。
     * 菜单/回放等 UI 场景返回 false，避免用"场景名字符串比较"来决定系统配置。
     */
    public boolean usesPhysics() {
        return true;
    }

    // 初始化所有对象
    public void initialize() {
        for (GameObject obj : gameObjects) {
            obj.initialize();
        }
        initialized = true;
    }

    /**
     * 处理对象生命周期（添加 / 删除）。
     * 子类若自定义 update（如客户端只做表现层），可直接调用此方法。
     */
    public void updateGameObjects(float deltaTime) {
        processObjectLifecycle();
        updateObjectsDirectly(deltaTime);
    }

    private void processObjectLifecycle() {
        // 添加：先设置场景归属，再初始化，组件才能拿到正确的 Scene
        for (GameObject obj : objectsToAdd) {
            if (obj.getScene() == null) {
                obj.setScene(this);
            }
            gameObjects.add(obj);
            if (initialized) {
                obj.initialize();
            }
        }
        objectsToAdd.clear();

        // 删除
        for (GameObject obj : objectsToRemove) {
            gameObjects.remove(obj);
        }
        objectsToRemove.clear();
    }

    private void updateObjectsDirectly(float deltaTime) {
        Iterator<GameObject> iterator = gameObjects.iterator();
        while (iterator.hasNext()) {
            GameObject obj = iterator.next();
            if (obj.isActive()) {
                obj.update(deltaTime);
            } else {
                iterator.remove();
            }
        }
    }

    // 更新场景：处理增删和调用每个对象的update
    public void update(float deltaTime) {
        updateGameObjects(deltaTime);
    }

    // 渲染所有活跃对象
    public void render() {
        for (GameObject obj : gameObjects) {
            if (obj.isActive()) {
                obj.render();
            }
        }
    }

    // 添加对象（下一帧）
    public void addGameObject(GameObject gameObject) {
        if (gameObject == null) return;
        if (gameObject.getScene() == null) {
            gameObject.setScene(this);
        }
        objectsToAdd.add(gameObject);
    }

    // 删除对象
    public void removeGameObject(GameObject gameObject) {
        if (gameObject == null || !gameObjects.contains(gameObject)) return;
        objectsToRemove.add(gameObject);
    }

    // 查找游戏对象
    public <T extends Component<T>> List<GameObject> findGameObjectsByComponent(Class<T> componentType) {
        return gameObjects.stream()
                .filter(obj -> obj.hasComponent(componentType))
                .collect(Collectors.toList());
    }

    // 获取所有指定组件
    public <T extends Component<T>> List<T> getComponents(Class<T> componentType) {
        List<T> result = new ArrayList<>();
        for (GameObject obj : gameObjects) {
            T component = obj.getComponent(componentType);
            if (component != null) {
                result.add(component);
            }
        }
        return result;
    }

    public void clear() {
        gameObjects.clear();
        objectsToAdd.clear();
        objectsToRemove.clear();
    }

    public String getName() {
        return name;
    }

    /** 返回副本，避免调用方在遍历时被生命周期修改影响 */
    public List<GameObject> getGameObjects() {
        return new ArrayList<>(gameObjects);
    }
}
