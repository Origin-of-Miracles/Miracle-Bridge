package com.originofmiracles.miraclebridge.entity.ysm;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * YSM（Yes Steve Model）兼容层
 * 
 * 通过基于命令的 API 提供对 YSM 模型的控制。
 * 
 * ⚠️ 平台限制：
 * - Windows 10/11: ✅ 支持
 * - Linux glibc 2.31+: ✅ 支持
 * - macOS: ❌ 不支持（YSM 2.x 使用 C++ 原生库）
 * 
 * @see <a href="https://ysm.cfpa.team/">YSM 文档</a>
 */
public class YSMCompat {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String YSM_MOD_ID = "ysm";
    
    private static Boolean ysmLoaded = null;
    
    /**
     * 检查 YSM 是否已加载
     */
    public static boolean isYSMLoaded() {
        if (ysmLoaded == null) {
            ysmLoaded = ModList.get().isLoaded(YSM_MOD_ID);
            if (ysmLoaded) {
                LOGGER.info("检测到 YSM - 已启用高级模型控制");
            } else {
                LOGGER.info("未找到 YSM - 使用回退实体控制");
            }
        }
        return ysmLoaded;
    }
    
    /**
     * 在玩家上播放动画
     * @param player 目标玩家
     * @param animationName 动画名称（例如 "wave", "sit"）
     */
    public static void playAnimation(ServerPlayer player, String animationName) {
        if (!isYSMLoaded()) return;
        executeCommand(player, "ysm play " + player.getName().getString() + " " + animationName);
    }
    
    /**
     * 停止强制动画
     */
    public static void stopAnimation(ServerPlayer player) {
        if (!isYSMLoaded()) return;
        executeCommand(player, "ysm play " + player.getName().getString() + " stop");
    }
    
    /**
     * 设置玩家模型
     * @param modelId 模型资源 ID
     * @param textureId 纹理资源 ID
     */
    public static void setModel(ServerPlayer player, String modelId, String textureId) {
        if (!isYSMLoaded()) return;
        executeCommand(player, String.format(
            "ysm model set %s %s %s true",
            player.getName().getString(), modelId, textureId
        ));
    }
    
    /**
     * 执行 Molang 表达式（用于变量操作）
     * 示例："v.expression='happy'" 设置表情变量
     */
    public static void executeMolang(ServerPlayer player, String expression) {
        if (!isYSMLoaded()) return;
        executeCommand(player, String.format(
            "ysm molang execute %s %s",
            player.getName().getString(), expression
        ));
    }
    
    /**
     * 重载所有 YSM 模型
     */
    public static void reloadModels(ServerPlayer player) {
        if (!isYSMLoaded()) return;
        executeCommand(player, "ysm model reload");
    }
    
    /**
     * 以 OP 权限执行 YSM 命令
     */
    private static void executeCommand(ServerPlayer player, String command) {
        try {
            player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4),
                command
            );
            LOGGER.debug("执行 YSM 命令: {}", command);
        } catch (Exception e) {
            LOGGER.error("执行 YSM 命令失败: {}", command, e);
        }
    }
}
