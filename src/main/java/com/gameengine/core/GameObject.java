package com.gameengine.core;

import com.gameengine.scene.Scene;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 游戏对象：组件容器 + 场景归属 + 跨线程用户数据。
 *
 * 设计说明：
 * - 每个对象拥有全局唯一、单调递增的 id，网络同步使用该 id 而不是 identityHashCode，
 *   这样对象 id 可预测、可测试，也不会因为 JVM 内存布局变化而改变。
 * - components 使用 CopyOnWriteArrayList：读多写少，允许渲染/序列化线程安全地遍历，
 *   避免"迭代时 addComponent"导致的 ConcurrentModificationException。
 */
public class GameObject {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    protected boolean active;
    protected String name;
    protected final List<Component<?>> components;

    /** 全局唯一 id，用于录像与网络同步 */
    private final long id;

    // 场景归属：对象可在任意时刻查询自己属于哪个场景
    private Scene scene; // 所属场景
    public Scene getScene() { return scene; }
    public void setScene(Scene scene) { this.scene = scene; }

    // 跨线程/跨模块的键值数据（网络同步、归属标记等）
    private final Map<String, Object> userData;

    public synchronized void setUserData(String key, Object value) { userData.put(key, value); }
    public synchronized Object getUserData(String key) { return userData.get(key); }
    public synchronized void removeUserData(String key) { userData.remove(key); }
    public synchronized boolean hasUserData(String key) { return userData.containsKey(key); }

    public GameObject() {
        this("GameObject");
    }

    public GameObject(String name) {
        this.id = NEXT_ID.getAndIncrement();
        this.active = true;
        this.name = name;
        this.components = new CopyOnWriteArrayList<>();
        this.userData = new ConcurrentHashMap<>();
    }

    public void update(float deltaTime) {
        updateComponents(deltaTime);
    }

    public void render() {
        renderComponents();
    }

    public void initialize() {
    }

    /**
     * 销毁对象：幂等。重复调用不会再遍历/清空组件，也不会重复触发死亡逻辑。
     */
    public void destroy() {
        if (!active && components.isEmpty()) {
            return;
        }
        this.active = false;
        for (Component<?> component : components) {
            component.destroy();
        }
        components.clear();
    }

    public <T extends Component<T>> T addComponent(T component) {
        component.setOwner(this);
        components.add(component);
        component.initialize();
        return component;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component<?>> T getComponent(Class<T> componentType) {
        for (Component<?> component : components) {
            if (componentType.isInstance(component)) {
                return (T) component;
            }
        }
        return null;
    }

    public <T extends Component<?>> boolean hasComponent(Class<T> componentType) {
        for (Component<?> component : components) {
            if (componentType.isInstance(component)) {
                return true;
            }
        }
        return false;
    }

    public void updateComponents(float deltaTime) {
        for (Component<?> component : components) {
            if (component.isEnabled()) {
                component.update(deltaTime);
            }
        }
    }

    public void renderComponents() {
        for (Component<?> component : components) {
            if (component.isEnabled()) {
                component.render();
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 全局唯一 id（网络/录像使用） */
    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return name + "#" + id;
    }
}
