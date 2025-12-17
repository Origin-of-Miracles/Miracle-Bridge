package com.originofmiracles.miraclebridge.command;

import org.slf4j.Logger;

import com.cinemamod.mcef.MCEF;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.browser.MiracleBrowser;
import com.originofmiracles.miraclebridge.config.ClientConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;

/**
 * MCEF 测试命令
 * 
 * 提供 MCEF 浏览器引擎的测试功能：
 * - /miraclebridge mcef status - 检查 MCEF 初始化状态
 * - /miraclebridge mcef create <name> [url] - 创建浏览器实例
 * - /miraclebridge mcef close <name> - 关闭浏览器实例
 * - /miraclebridge mcef list - 列出所有浏览器实例
 * - /miraclebridge mcef js <name> <code> - 执行 JavaScript 代码
 */
public class MCEFCommands {
    
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("mcef")
            .then(Commands.literal("status")
                .executes(MCEFCommands::checkStatus))
            .then(Commands.literal("create")
                .then(Commands.argument("name", MessageArgument.message())
                    .executes(ctx -> createBrowser(ctx, null))
                    .then(Commands.argument("url", MessageArgument.message())
                        .executes(ctx -> createBrowser(ctx, MessageArgument.getMessage(ctx, "url").getString())))))
            .then(Commands.literal("close")
                .then(Commands.argument("name", MessageArgument.message())
                    .executes(MCEFCommands::closeBrowser)))
            .then(Commands.literal("list")
                .executes(MCEFCommands::listBrowsers))
            .then(Commands.literal("js")
                .then(Commands.argument("name", MessageArgument.message())
                    .then(Commands.argument("code", MessageArgument.message())
                        .executes(MCEFCommands::executeJS))));
    }
    
    /**
     * 检查 MCEF 初始化状态
     */
    private static int checkStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("========== MCEF 状态检查 ==========").withStyle(ChatFormatting.GOLD), false);
        
        // 检查 MCEF 是否初始化
        boolean initialized = MCEF.isInitialized();
        if (initialized) {
            source.sendSuccess(() -> Component.literal("✓ MCEF 已初始化").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendFailure(Component.literal("✗ MCEF 未初始化").withStyle(ChatFormatting.RED));
            source.sendFailure(Component.literal("  请确保 MCEF mod 已正确安装"));
            return 0;
        }
        
        // 检查 CEF 版本
        try {
            String cefVersion = getCefVersion();
            source.sendSuccess(() -> Component.literal("  CEF 版本: " + cefVersion).withStyle(ChatFormatting.GRAY), false);
        } catch (Exception e) {
            source.sendSuccess(() -> Component.literal("  CEF 版本: 未知").withStyle(ChatFormatting.GRAY), false);
        }
        
        // 检查当前浏览器数量
        int browserCount = BrowserManager.getInstance().getBrowserCount();
        source.sendSuccess(() -> Component.literal("  活跃浏览器: " + browserCount).withStyle(ChatFormatting.GRAY), false);
        
        // 检查配置
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("配置信息:").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("  默认宽度: " + ClientConfig.getBrowserWidth()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  默认高度: " + ClientConfig.getBrowserHeight()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  透明背景: " + ClientConfig.BROWSER_TRANSPARENT_BACKGROUND.get()).withStyle(ChatFormatting.GRAY), false);
        
        String devUrl = ClientConfig.getDevServerUrl();
        source.sendSuccess(() -> Component.literal("  开发服务器: " + (devUrl != null && !devUrl.isEmpty() ? devUrl : "未配置")).withStyle(ChatFormatting.GRAY), false);
        
        source.sendSuccess(() -> Component.literal("====================================").withStyle(ChatFormatting.GOLD), false);
        
        return 1;
    }
    
    /**
     * 创建浏览器实例
     */
    private static int createBrowser(CommandContext<CommandSourceStack> ctx, String url) {
        CommandSourceStack source = ctx.getSource();
        
        try {
            String name = MessageArgument.getMessage(ctx, "name").getString();
            
            if (!MCEF.isInitialized()) {
                source.sendFailure(Component.literal("MCEF 未初始化，无法创建浏览器"));
                return 0;
            }
            
            // 使用配置的 URL 或指定的 URL
            String actualUrl = url != null ? url : ClientConfig.getDevServerUrl();
            if (actualUrl == null || actualUrl.isEmpty()) {
                actualUrl = "https://www.minecraft.net/";
            }
            
            source.sendSuccess(() -> Component.literal("正在创建浏览器 '" + name + "'...").withStyle(ChatFormatting.YELLOW), false);
            
            MiracleBrowser browser = BrowserManager.getInstance().createBrowser(name, actualUrl);
            
            if (browser != null) {
                final String finalUrl = actualUrl;
                source.sendSuccess(() -> Component.literal("✓ 浏览器创建成功").withStyle(ChatFormatting.GREEN), false);
                source.sendSuccess(() -> Component.literal("  名称: " + name).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal("  URL: " + finalUrl).withStyle(ChatFormatting.GRAY), false);
                return 1;
            } else {
                source.sendFailure(Component.literal("✗ 浏览器创建失败"));
                return 0;
            }
        } catch (Exception e) {
            LOGGER.error("创建浏览器时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 关闭浏览器实例
     */
    private static int closeBrowser(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        try {
            String name = MessageArgument.getMessage(ctx, "name").getString();
            
            if (!BrowserManager.getInstance().hasBrowser(name)) {
                source.sendFailure(Component.literal("浏览器 '" + name + "' 不存在"));
                return 0;
            }
            
            BrowserManager.getInstance().closeBrowser(name);
            source.sendSuccess(() -> Component.literal("✓ 已关闭浏览器: " + name).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("关闭浏览器时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 列出所有浏览器实例
     */
    private static int listBrowsers(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        var names = BrowserManager.getInstance().getBrowserNames();
        
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有活跃的浏览器实例").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        
        source.sendSuccess(() -> Component.literal("活跃的浏览器实例 (" + names.size() + "):").withStyle(ChatFormatting.GOLD), false);
        for (String name : names) {
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(name);
            String info = browser != null ? " (纹理ID: " + browser.getTextureId() + ")" : "";
            source.sendSuccess(() -> Component.literal("  - " + name + info).withStyle(ChatFormatting.GREEN), false);
        }
        
        return 1;
    }
    
    /**
     * 执行 JavaScript 代码
     */
    private static int executeJS(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        try {
            String name = MessageArgument.getMessage(ctx, "name").getString();
            String code = MessageArgument.getMessage(ctx, "code").getString();
            
            MiracleBrowser browser = BrowserManager.getInstance().getBrowser(name);
            if (browser == null) {
                source.sendFailure(Component.literal("浏览器 '" + name + "' 不存在"));
                return 0;
            }
            
            source.sendSuccess(() -> Component.literal("执行 JavaScript: " + code).withStyle(ChatFormatting.YELLOW), false);
            browser.executeJavaScript(code);
            source.sendSuccess(() -> Component.literal("✓ 已发送执行请求").withStyle(ChatFormatting.GREEN), false);
            
            return 1;
        } catch (Exception e) {
            LOGGER.error("执行 JavaScript 时发生错误", e);
            source.sendFailure(Component.literal("错误: " + e.getMessage()));
            return 0;
        }
    }
    
    /**
     * 获取 CEF 版本信息
     */
    private static String getCefVersion() {
        // MCEF 可能没有直接暴露版本 API，返回 MCEF mod 版本
        return "MCEF 2.1.6 for 1.20.1";
    }
}
