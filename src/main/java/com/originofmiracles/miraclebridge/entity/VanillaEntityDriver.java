package com.originofmiracles.miraclebridge.entity;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.util.ThreadScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * 原版实体驱动实现
 * 
 * 不依赖 YSM 的回退方案，使用 Minecraft 原生 API。
 * 主要用于：
 * - 不支持 YSM 的平台（如 macOS）
 * - 未安装 YSM 的环境
 * - 非玩家实体的控制
 */
public class VanillaEntityDriver implements IEntityDriver {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final ServerPlayer player;
    
    /**
     * 导航状态
     */
    private boolean isNavigating = false;
    private BlockPos navigationTarget = null;
    
    @Nullable
    private ScheduledFuture<?> navigationTask;
    
    @Nullable
    private Consumer<Boolean> navigationCallback;
    
    /**
     * 导航配置
     */
    private static final double MOVE_SPEED = 0.15;
    private static final double ARRIVAL_THRESHOLD = 1.5;
    private static final int MAX_TICKS = 600;
    
    public VanillaEntityDriver(ServerPlayer player) {
        this.player = player;
    }
    
    @Override
    public void playAnimation(String animationId) {
        // 原版不支持自定义动画
        // 可以尝试触发一些原版效果
        LOGGER.debug("原版驱动不支持自定义动画: {}", animationId);
        
        // 根据动画 ID 触发一些原版行为
        switch (animationId.toLowerCase()) {
            case "wave" -> {
                // 原版没有挥手动画，可以发送一个消息
                LOGGER.debug("触发挥手动作（原版模拟）");
            }
            case "sit" -> {
                // 可以让玩家蹲下
                player.setShiftKeyDown(true);
            }
            case "stand" -> {
                player.setShiftKeyDown(false);
            }
            case "jump" -> {
                // 触发跳跃
                player.jumpFromGround();
            }
            default -> LOGGER.debug("未知动画: {}", animationId);
        }
    }
    
    @Override
    public void setExpression(String expressionId) {
        // 原版不支持表情
        LOGGER.debug("原版驱动不支持表情: {}", expressionId);
    }
    
    @Override
    public void navigateTo(BlockPos target) {
        navigateTo(target, null);
    }
    
    /**
     * 带回调的导航
     */
    public void navigateTo(BlockPos target, @Nullable Consumer<Boolean> callback) {
        if (isNavigating) {
            halt();
        }
        
        this.navigationTarget = target;
        this.navigationCallback = callback;
        this.isNavigating = true;
        
        LOGGER.debug("原版导航: {} -> {}", player.blockPosition(), target);
        
        startNavigation();
    }
    
    /**
     * 异步导航
     */
    public CompletableFuture<Boolean> navigateToAsync(BlockPos target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        navigateTo(target, future::complete);
        return future;
    }
    
    /**
     * 开始导航
     */
    private void startNavigation() {
        final int[] tickCount = {0};
        
        navigationTask = ThreadScheduler.runRepeating(() -> {
            if (!isNavigating || navigationTarget == null || !player.isAlive()) {
                completeNavigation(false);
                return false;
            }
            
            tickCount[0]++;
            if (tickCount[0] > MAX_TICKS) {
                LOGGER.warn("原版导航超时");
                completeNavigation(false);
                return false;
            }
            
            Vec3 playerPos = player.position();
            Vec3 targetPos = Vec3.atCenterOf(navigationTarget);
            Vec3 direction = targetPos.subtract(playerPos);
            double distance = direction.horizontalDistance();
            
            // 到达检测
            if (distance < ARRIVAL_THRESHOLD) {
                LOGGER.debug("原版导航完成");
                completeNavigation(true);
                return false;
            }
            
            // 移动
            Vec3 moveDir = direction.normalize().scale(MOVE_SPEED);
            lookAt(navigationTarget);
            
            double newX = playerPos.x + moveDir.x;
            double newY = playerPos.y;
            double newZ = playerPos.z + moveDir.z;
            
            // 简单的障碍检测
            BlockPos frontPos = new BlockPos((int) newX, (int) newY, (int) newZ);
            if (player.level().getBlockState(frontPos).canOcclude()) {
                BlockPos above = frontPos.above();
                if (!player.level().getBlockState(above).canOcclude()) {
                    newY += 1.0;
                }
            }
            
            // 下落检测
            BlockPos below = new BlockPos((int) newX, (int) (newY - 1), (int) newZ);
            if (!player.level().getBlockState(below).canOcclude()) {
                newY -= 0.5;
            }
            
            player.teleportTo(newX, newY, newZ);
            
            return true;
        }, 1);
    }
    
    /**
     * 完成导航
     */
    private void completeNavigation(boolean success) {
        isNavigating = false;
        navigationTarget = null;
        
        if (navigationTask != null) {
            navigationTask.cancel(false);
            navigationTask = null;
        }
        
        if (navigationCallback != null) {
            Consumer<Boolean> callback = navigationCallback;
            navigationCallback = null;
            callback.accept(success);
        }
    }
    
    @Override
    public void halt() {
        LOGGER.debug("原版驱动停止");
        completeNavigation(false);
        player.setShiftKeyDown(false);
    }
    
    @Override
    public boolean isAvailable() {
        return player != null && player.isAlive();
    }
    
    @Override
    public BlockPos getPosition() {
        return player.blockPosition();
    }
    
    @Override
    public void lookAt(BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dy = target.getY() + 0.5 - player.getEyeY();
        double dz = target.getZ() + 0.5 - player.getZ();
        
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);
        
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }
    
    /**
     * 检查是否正在导航
     */
    @Override
    public boolean isNavigating() {
        return isNavigating;
    }
    
    /**
     * 获取当前导航目标
     */
    @Override
    public BlockPos getNavigationTarget() {
        return navigationTarget;
    }
    
    /**
     * 获取关联的玩家
     */
    public ServerPlayer getPlayer() {
        return player;
    }
}
