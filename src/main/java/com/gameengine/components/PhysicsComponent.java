package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

/**
 * 物理组件：管理游戏对象的物理属性（速度、加速度、质量、摩擦等）。
 * 与PhysicsSystem配合实现物理模拟。
 */
public class PhysicsComponent extends Component<PhysicsComponent> {
    private Vector2 velocity;   // 速度
    private Vector2 acceleration;   // 加速度
    private float mass; // 质量
    private float friction; // 摩擦力
    private boolean useGravity; // 是否使用重力
    private Vector2 gravity;    // 重力方向和大小

    /**
     * 默认构造函数：零速度、零加速度，质量1，摩擦0.9，禁用重力
     */
    public PhysicsComponent() {
        this.velocity = new Vector2();
        this.acceleration = new Vector2();
        this.mass = 1.0f;
        this.friction = 0.9f;
        this.useGravity = false;
        this.gravity = new Vector2(0, 9.8f);  // 默认向下重力
    }

    // 指定重量的构造函数
    public PhysicsComponent(float mass) {
        this();
        this.mass = mass;
    }
    
    @Override
    public void initialize() {
    }
    
    @Override
    public void render() {
    }

    /**
     * 施加力：根据 F=ma 计算加速度变化
     */
    public void applyForce(Vector2 force) {
        if (mass > 0) {
            acceleration = acceleration.add(force.multiply(1.0f / mass));
        }
    }
    
    public void applyImpulse(Vector2 impulse) {
        if (mass > 0) {
            velocity = velocity.add(impulse.multiply(1.0f / mass));
        }
    }
    public void setAbsVelocity(float Velocity){this.velocity = this.velocity.normalize().multiply(Velocity);}

    public void setVelocity(Vector2 velocity) {
        this.velocity = new Vector2(velocity);
    }
    
    public void setVelocity(float x, float y) {
        this.velocity = new Vector2(x, y);
    }
    
    public void setAcceleration(Vector2 acceleration) {
        this.acceleration = new Vector2(acceleration);
    }
    
    public void addVelocity(Vector2 delta) {
        this.velocity = velocity.add(delta);
    }
    
    public void setGravity(Vector2 gravity) {
        this.gravity = new Vector2(gravity);
    }
    
    public void setUseGravity(boolean useGravity) {
        this.useGravity = useGravity;
    }
    
    public void setFriction(float friction) {
        this.friction = Math.max(0, Math.min(1, friction));
    }
    
    public void setMass(float mass) {
        this.mass = Math.max(0.1f, mass);
    }
    
    public Vector2 getVelocity() {
        return new Vector2(velocity);
    }
    
    public Vector2 getAcceleration() {
        return new Vector2(acceleration);
    }

    public float getAbsVelocity() {return new Vector2(velocity).magnitude();}

    public float getMass() {
        return mass;
    }
    
    public float getFriction() {
        return friction;
    }
    
    public boolean isUseGravity() {
        return useGravity;
    }
    
    public Vector2 getGravity() {
        return new Vector2(gravity);
    }
}
