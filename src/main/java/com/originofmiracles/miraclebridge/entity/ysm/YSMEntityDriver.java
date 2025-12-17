package com.originofmiracles.miraclebridge.entity.ysm;

import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.entity.IEntityDriver;
import com.originofmiracles.miraclebridge.util.ThreadScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 用于 YSM 兼容玩家的实体驱动程序实现
 */
public class YSMEntityDriver implements IEntityDriver {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private final ServerPlayer player;
    
    /**
     * 导航状态
     */
    private boolean isNavigating = false;
    private BlockPos navigationTarget = null;
    
    /**
     * 导航配置
     */
    private static final double NAVIGATION_SPEED = 0.2; // 每 tick 移动距离
    private static final double ARRIVAL_THRESHOLD = 1.5; // 到达阈值
    private static final int MAX_NAVIGATION_TICKS = 600; // 最大导航时间（30秒）
    
    /**
     * 导航回调
     */
    @Nullable
    private Consumer<Boolean> navigationCallback;
    
    public YSMEntityDriver(ServerPlayer player) {
        this.player = player;
    }
    
    @Override
    public void playAnimation(String animationId) {
        YSMCompat.playAnimation(player, animationId);
    }
    
    @Override
    public void setExpression(String expressionId) {
        // 通过 Molang 变量设置表情
        YSMCompat.executeMolang(player, "v.expression='" + expressionId + "'");
    }
    
    @Override
    public void navigateTo(BlockPos target) {
        navigateTo(target, null);
    }
    
    /**
     * 带回调的导航方法
     * 
     * @param target 目标位置
     * @param callback 到达或失败时的回调，参数为是否成功
     */
    public void navigateTo(BlockPos target, @Nullable Consumer<Boolean> callback) {
        if (isNavigating) {
            LOGGER.debug("取消当前导航，开始新导航");
            halt();
        }
        
        this.navigationTarget = target;
        this.navigationCallback = callback;
        this.isNavigating = true;
        
        LOGGER.debug("开始导航: {} -> {}", player.blockPosition(), target);
        
        // 在服务端主线程执行导航
        startNavigationLoop();
    }
    
    /**
     * 异步导航方法
     * 
     * @param target 目标位置
     * @return 导航完成的 Future
     */
    public CompletableFuture<Boolean> navigateToAsync(BlockPos target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        navigateTo(target, future::complete);
        return future;
    }
    
    /**
     * 开始导航循环
     */
    private void startNavigationLoop() {
        final int[] tickCount = {0};
        
        ThreadScheduler.runRepeating(() -> {
            if (!isNavigating || navigationTarget == null || !player.isAlive()) {
                completeNavigation(false);
                return false; // 停止循环
            }
            
            tickCount[0]++;
            if (tickCount[0] > MAX_NAVIGATION_TICKS) {
                LOGGER.warn("导航超时: {}", navigationTarget);
                completeNavigation(false);
                return false;
            }
            
            // 计算到目标的向量
            Vec3 playerPos = player.position();
            Vec3 targetPos = Vec3.atCenterOf(navigationTarget);
            Vec3 direction = targetPos.subtract(playerPos);
            double distance = direction.horizontalDistance();
            
            // 检查是否到达
            if (distance < ARRIVAL_THRESHOLD) {
                LOGGER.debug("导航完成: {}", navigationTarget);
                completeNavigation(true);
                return false;
            }
            
            // 计算移动方向
            Vec3 moveDir = direction.normalize().scale(NAVIGATION_SPEED);
            
            // 先看向目标
            lookAt(navigationTarget);
            
            // 移动玩家（使用 teleport 模拟移动）
            double newX = playerPos.x + moveDir.x;
            double newY = playerPos.y; // Y 保持不变，或进行跳跃检测
            double newZ = playerPos.z + moveDir.z;
            
            // 检测是否需要跳跃（前方有障碍）
            BlockPos frontPos = new BlockPos((int) newX, (int) newY, (int) newZ);
            if (player.level().getBlockState(frontPos).isSolid()) {
                // 尝试跳跃
                BlockPos aboveFront = frontPos.above();
                if (!player.level().getBlockState(aboveFront).isSolid()) {
                    newY += 1.0;
                }
            }
            
            // 检测脚下是否有地面
            BlockPos belowPos = new BlockPos((int) newX, (int) (newY - 1), (int) newZ);
            if (!player.level().getBlockState(belowPos).isSolid()) {
                // 下落
                newY -= 0.5;
            }
            
            player.teleportTo(newX, newY, newZ);
            
            return true; // 继续循环
        }, 1); // 每 tick 执行
    }
    
    /**
     * 完成导航
     */
    private void completeNavigation(boolean success) {
        isNavigating = false;
        navigationTarget = null;
        
        if (navigationCallback != null) {
            Consumer<Boolean> callback = navigationCallback;
            navigationCallback = null;
            callback.accept(success);
        }
    }
    
    @Override
    public void halt() {
        if (isNavigating) {
            LOGGER.debug("停止导航");
            completeNavigation(false);
        }
        YSMCompat.stopAnimation(player);
    }
    
    @Override
    public boolean isAvailable() {
        return YSMCompat.isYSMLoaded() && player != null && player.isAlive();
    }
    
    @Override
    public BlockPos getPosition() {
        return player.blockPosition();
    }
    
    @Override
    public void lookAt(BlockPos target) {
        // 计算视角
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
    @Nullable
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
