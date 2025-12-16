package com.originofmiracles.miraclebridge.entity.ysm;

import com.originofmiracles.miraclebridge.entity.IEntityDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 用于 YSM 兼容玩家的实体驱动程序实现
 */
public class YSMEntityDriver implements IEntityDriver {
    
    private final ServerPlayer player;
    
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
        // TODO: 实现寻路逻辑
        // 这不是 YSM 特有功能，需要自定义导航系统
    }
    
    @Override
    public void halt() {
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
        double dx = target.getX() - player.getX();
        double dy = target.getY() - player.getEyeY();
        double dz = target.getZ() - player.getZ();
        
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, dist) * 180.0 / Math.PI);
        
        player.setYRot(yaw);
        player.setXRot(pitch);
    }
}
