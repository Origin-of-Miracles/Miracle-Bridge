package com.originofmiracles.miraclebridge.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.entity.EntityDriverFactory;
import com.originofmiracles.miraclebridge.entity.IEntityDriver;
import com.originofmiracles.miraclebridge.entity.ysm.YSMCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * YSM 测试命令
 * 
 * 提供 YSM (Yes Steve Model) 的测试功能：
 * - /miraclebridge ysm status - 检查 YSM 是否加载
 * - /miraclebridge ysm anim <animation> - 播放动画
 * - /miraclebridge ysm stop - 停止动画
 * - /miraclebridge ysm expr <expression> - 设置表情
 * - /miraclebridge ysm model <modelId> <textureId> - 设置模型
 * - /miraclebridge ysm nav <x> <y> <z> - 导航到指定位置
 */
public class YSMCommands {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("ysm")
            .then(Commands.literal("status")
                .executes(YSMCommands::checkStatus))
            .then(Commands.literal("anim")
                .then(Commands.argument("animation", MessageArgument.message())
                    .executes(YSMCommands::playAnimation)))
            .then(Commands.literal("stop")
                .executes(YSMCommands::stopAnimation))
            .then(Commands.literal("expr")
                .then(Commands.argument("expression", MessageArgument.message())
                    .executes(YSMCommands::setExpression)))
            .then(Commands.literal("model")
                .then(Commands.argument("modelId", MessageArgument.message())
                    .then(Commands.argument("textureId", MessageArgument.message())
                        .executes(YSMCommands::setModel))))
            .then(Commands.literal("nav")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(YSMCommands::navigateTo)))
            .then(Commands.literal("look")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(YSMCommands::lookAt)))
            .then(Commands.literal("halt")
                .executes(YSMCommands::halt))
            .then(Commands.literal("driver")
                .executes(YSMCommands::checkDriver));
    }
    
    /**
     * 检查 YSM 状态
     */
    private static int checkStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("========== YSM 状态检查 ==========").withStyle(ChatFormatting.GOLD), false);
        
        boolean loaded = YSMCompat.isYSMLoaded();
        
        if (loaded) {
            source.sendSuccess(() -> Component.literal("✓ YSM 已加载").withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("  高级模型控制: 可用").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  动画支持: 可用").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  表情支持: 可用").withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("✗ YSM 未加载").withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  将使用原版实体驱动回退方案").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("  部分功能不可用：").withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.literal("    - 自定义动画").withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(() -> Component.literal("    - 表情控制").withStyle(ChatFormatting.DARK_GRAY), false);
            source.sendSuccess(() -> Component.literal("    - 模型切换").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        
        // 检查平台兼容性
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("平台兼容性:").withStyle(ChatFormatting.YELLOW), false);
        
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            source.sendSuccess(() -> Component.literal("  ✓ Windows - 完全支持").withStyle(ChatFormatting.GREEN), false);
        } else if (os.contains("linux")) {
            source.sendSuccess(() -> Component.literal("  ✓ Linux - 完全支持").withStyle(ChatFormatting.GREEN), false);
        } else if (os.contains("mac")) {
            source.sendSuccess(() -> Component.literal("  ✗ macOS - 不支持 YSM").withStyle(ChatFormatting.RED), false);
            source.sendSuccess(() -> Component.literal("    YSM 2.x 使用 C++ 原生库，不兼容 macOS").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            source.sendSuccess(() -> Component.literal("  ? " + os + " - 未知兼容性").withStyle(ChatFormatting.YELLOW), false);
        }
        
        source.sendSuccess(() -> Component.literal("===================================").withStyle(ChatFormatting.GOLD), false);
        
        return 1;
    }
    
    /**
     * 播放动画
     */
    private static int playAnimation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        try {
            String animation = MessageArgument.getMessage(ctx, "animation").getString();
            
            IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
            
            source.sendSuccess(() -> Component.literal("播放动画: " + animation).withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  驱动: " + driver.getClass().getSimpleName()).withStyle(ChatFormatting.GRAY), false);
            
            driver.playAnimation(animation);
            
            source.sendSuccess(() -> Component.literal("✓ 动画请求已发送").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("播放动画时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 停止动画
     */
    private static int stopAnimation(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        if (YSMCompat.isYSMLoaded()) {
            YSMCompat.stopAnimation(player);
            source.sendSuccess(() -> Component.literal("✓ 已停止动画").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("YSM 未加载，无需停止").withStyle(ChatFormatting.YELLOW), false);
        }
        
        return 1;
    }
    
    /**
     * 设置表情
     */
    private static int setExpression(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        try {
            String expression = MessageArgument.getMessage(ctx, "expression").getString();
            
            IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
            
            source.sendSuccess(() -> Component.literal("设置表情: " + expression).withStyle(ChatFormatting.YELLOW), false);
            driver.setExpression(expression);
            source.sendSuccess(() -> Component.literal("✓ 表情请求已发送").withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("设置表情时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 设置模型
     */
    private static int setModel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        if (!YSMCompat.isYSMLoaded()) {
            source.sendFailure(Component.literal("YSM 未加载，无法设置模型"));
            return 0;
        }
        
        try {
            String modelId = MessageArgument.getMessage(ctx, "modelId").getString();
            String textureId = MessageArgument.getMessage(ctx, "textureId").getString();
            
            source.sendSuccess(() -> Component.literal("设置模型: " + modelId + " / " + textureId).withStyle(ChatFormatting.YELLOW), false);
            YSMCompat.setModel(player, modelId, textureId);
            source.sendSuccess(() -> Component.literal("✓ 模型请求已发送").withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("设置模型时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 导航到指定位置
     */
    private static int navigateTo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        try {
            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
            
            IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
            
            source.sendSuccess(() -> Component.literal("开始导航至: " + pos.toShortString()).withStyle(ChatFormatting.YELLOW), false);
            source.sendSuccess(() -> Component.literal("  驱动: " + driver.getClass().getSimpleName()).withStyle(ChatFormatting.GRAY), false);
            
            driver.navigateTo(pos);
            
            source.sendSuccess(() -> Component.literal("✓ 导航已开始").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("导航时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 看向指定位置
     */
    private static int lookAt(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        try {
            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
            
            IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
            driver.lookAt(pos);
            
            source.sendSuccess(() -> Component.literal("✓ 已看向: " + pos.toShortString()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("lookAt 时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 停止所有行为
     */
    private static int halt(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        try {
            IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
            driver.halt();
            
            source.sendSuccess(() -> Component.literal("✓ 已停止所有行为").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("halt 时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 检查当前使用的驱动类型
     */
    private static int checkDriver(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        
        if (player == null) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }
        
        IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
        
        source.sendSuccess(() -> Component.literal("当前实体驱动信息:").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  类型: " + driver.getClass().getSimpleName()).withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("  可用: " + driver.isAvailable()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  导航中: " + driver.isNavigating()).withStyle(ChatFormatting.GRAY), false);
        
        BlockPos navTarget = driver.getNavigationTarget();
        if (navTarget != null) {
            source.sendSuccess(() -> Component.literal("  导航目标: " + navTarget.toShortString()).withStyle(ChatFormatting.GRAY), false);
        }
        
        return 1;
    }
}
