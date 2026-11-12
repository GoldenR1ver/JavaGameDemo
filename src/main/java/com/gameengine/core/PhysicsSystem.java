package com.gameengine.core;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 物理系统：处理物理模拟（速度、加速度、重力、边界碰撞）。
 * 使用多线程并行更新物理组件——每个组件只会被一个线程处理，互不共享状态。
 *
 * 注意：物理线程使用守护线程，避免窗口关闭后 JVM 因线程池未销毁而无法退出。
 */
public class PhysicsSystem {
    private final Scene scene;
    private final ExecutorService physicsExecutor;
    private final int screenWidth;
    private final int screenHeight;
    private volatile boolean shutdown;

    public PhysicsSystem(Scene scene) {
        this(scene, 1920, 1080);
    }

    public PhysicsSystem(Scene scene, int screenWidth, int screenHeight) {
        this.scene = scene;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        this.physicsExecutor = Executors.newFixedThreadPool(threadCount, new DaemonThreadFactory("physics"));
    }

    public void update(float deltaTime) {
        if (shutdown) return;
        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        if (physicsComponents.isEmpty()) return;

        // 物体较少时串行执行：避免线程调度开销大于计算本身
        if (physicsComponents.size() < 32) {
            for (PhysicsComponent physics : physicsComponents) {
                if (physics.isEnabled()) {
                    updatePhysics(physics, deltaTime);
                    handleBoundary(physics);
                }
            }
            return;
        }

        int threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        int batchSize = Math.max(1, physicsComponents.size() / threadCount + 1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < physicsComponents.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, physicsComponents.size());

            futures.add(physicsExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    PhysicsComponent physics = physicsComponents.get(j);
                    if (physics.isEnabled()) {
                        updatePhysics(physics, deltaTime);
                        handleBoundary(physics);
                    }
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("[Physics] 物理更新失败: " + e.getMessage());
            }
        }
    }

    private void updatePhysics(PhysicsComponent physics, float deltaTime) {
        GameObject owner = physics.getOwner();
        if (owner == null || !owner.isActive()) return;

        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) return;

        Vector2 acceleration = physics.getAcceleration();
        if (physics.isUseGravity()) {
            acceleration = acceleration.add(physics.getGravity());
            physics.setAcceleration(acceleration);
        }

        Vector2 velocity = physics.getVelocity();
        velocity = velocity.add(acceleration.multiply(deltaTime));
        // 摩擦改为与帧率无关：friction 表示"每 1/60 秒保留的比例"
        float friction = physics.getFriction();
        if (friction < 1f) {
            velocity = velocity.multiply((float) Math.pow(friction, deltaTime * 60f));
        }
        physics.setVelocity(velocity);

        transform.translate(velocity.multiply(deltaTime));
        physics.setAcceleration(new Vector2());
    }

    private void handleBoundary(PhysicsComponent physics) {
        GameObject owner = physics.getOwner();
        if (owner == null || !owner.isActive()) return;

        TransformComponent transform = owner.getComponent(TransformComponent.class);
        if (transform == null) return;

        Vector2 pos = transform.getPosition();
        Vector2 velocity = physics.getVelocity();

        boolean velocityChanged = false;
        float velX = velocity.x;
        float velY = velocity.y;
        float posX = pos.x;
        float posY = pos.y;

        if (posX <= 0 || posX >= screenWidth - 15) {
            velX = -velX;
            velocityChanged = true;
        }
        if (posY <= 0 || posY >= screenHeight - 15) {
            velY = -velY;
            velocityChanged = true;
        }

        if (posX < 0) posX = 0;
        if (posY < 0) posY = 0;
        if (posX > screenWidth - 15) posX = screenWidth - 15;
        if (posY > screenHeight - 15) posY = screenHeight - 15;

        transform.setPosition(new Vector2(posX, posY));

        if (velocityChanged) {
            physics.setVelocity(new Vector2(velX, velY));
        }
    }

    public void cleanup() {
        shutdown = true;
        if (physicsExecutor != null && !physicsExecutor.isShutdown()) {
            physicsExecutor.shutdown();
            try {
                if (!physicsExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    physicsExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                physicsExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /** 守护线程工厂：防止非守护线程阻塞 JVM 退出 */
    static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
