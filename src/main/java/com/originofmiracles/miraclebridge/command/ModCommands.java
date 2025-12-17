package com.originofmiracles.miraclebridge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.MiracleBridge;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 模组命令注册中心
 * 
 * 注册所有 Miracle Bridge 的测试和调试命令：
 * - /miraclebridge test - 运行所有前置mod测试
 * - /miraclebridge mcef - MCEF 相关测试
 * - /miraclebridge ysm - YSM 相关测试
 */
@Mod.EventBusSubscriber(modid = MiracleBridge.MOD_ID)
public class ModCommands {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        LOGGER.info("注册 Miracle Bridge 命令...");
        
        // 注册主命令 /miraclebridge
        dispatcher.register(
            Commands.literal("miraclebridge")
                .requires(source -> source.hasPermission(2)) // OP 权限
                .then(TestCommands.register())               // /miraclebridge test
                .then(MCEFCommands.register())              // /miraclebridge mcef
                .then(YSMCommands.register())               // /miraclebridge ysm
        );
        
        // 注册简写命令 /mb
        dispatcher.register(
            Commands.literal("mb")
                .requires(source -> source.hasPermission(2))
                .redirect(dispatcher.getRoot().getChild("miraclebridge"))
        );
        
        LOGGER.info("Miracle Bridge 命令注册完成");
    }
}
