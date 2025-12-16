package com.originofmiracles.miraclebridge.entity;

import net.minecraft.core.BlockPos;

/**
 * 实体行为和动画控制接口
 * 
 * 为以下功能提供标准化 API：
 * - 播放动画（通过 YSM 或其他动画系统）
 * - 设置情感表情
 * - 控制导航和移动
 * - 执行高层行为
 * 
 * 实现类：
 * - YSMEntityDriver：用于 YSM 兼容实体（使用 YSM 模型的玩家）
 * - VanillaEntityDriver：原版实体的回退方案
 */
public interface IEntityDriver {
    
    /**
     * 播放命名动画
     * @param animationId 动画标识符（例如 "wave", "sit", "dance"）
     */
    void playAnimation(String animationId);
    
    /**
     * 设置情感表情
     * @param expressionId 表情标识符（例如 "happy", "sad", "angry"）
     */
    void setExpression(String expressionId);
    
    /**
     * 命令实体导航到目标位置
     * @param target 目标方块位置
     */
    void navigateTo(BlockPos target);
    
    /**
     * 停止所有当前行为和动画
     */
    void halt();
    
    /**
     * 检查此驱动程序是否可用/与实体兼容
     * @return 如果驱动程序可以控制实体，返回 true
     */
    boolean isAvailable();
    
    /**
     * 获取实体当前位置
     */
    BlockPos getPosition();
    
    /**
     * 让实体看向目标位置
     */
    void lookAt(BlockPos target);
}
