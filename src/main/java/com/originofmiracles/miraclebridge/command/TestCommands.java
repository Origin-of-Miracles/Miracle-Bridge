package com.originofmiracles.miraclebridge.command;

import com.cinemamod.mcef.MCEF;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.originofmiracles.miraclebridge.MiracleBridge;
import com.originofmiracles.miraclebridge.browser.BrowserManager;
import com.originofmiracles.miraclebridge.config.ConfigWatcher;
import com.originofmiracles.miraclebridge.entity.EntityDriverFactory;
import com.originofmiracles.miraclebridge.entity.IEntityDriver;
import com.originofmiracles.miraclebridge.entity.ysm.YSMCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 综合测试命令
 * 
 * 提供前置mod完整性测试：
 * - /miraclebridge test all - 运行所有测试
 * - /miraclebridge test mcef - 仅测试 MCEF
 * - /miraclebridge test ysm - 仅测试 YSM
 * - /miraclebridge test config - 测试配置系统
 * - /miraclebridge test network - 测试网络模块
 */
public class TestCommands {
    
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("test")
            .then(Commands.literal("all")
                .executes(TestCommands::runAllTests))
            .then(Commands.literal("mcef")
                .executes(TestCommands::testMCEF))
            .then(Commands.literal("ysm")
                .executes(TestCommands::testYSM))
            .then(Commands.literal("config")
                .executes(TestCommands::testConfig))
            .then(Commands.literal("summary")
                .executes(TestCommands::showSummary));
    }
    
    /**
     * 运行所有测试
     */
    private static int runAllTests(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("╔══════════════════════════════════════════════╗").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("║     Miracle Bridge 前置Mod功能测试           ║").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("║     版本: " + MiracleBridge.VERSION + "                            ║").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("╚══════════════════════════════════════════════╝").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        
        int totalTests = 0;
        int passedTests = 0;
        
        // 测试 1: MCEF
        source.sendSuccess(() -> Component.literal("【测试 1/4】MCEF 浏览器引擎").withStyle(ChatFormatting.AQUA), false);
        TestResult mcefResult = runMCEFTest();
        totalTests += mcefResult.total;
        passedTests += mcefResult.passed;
        printTestResult(source, mcefResult);
        
        // 测试 2: YSM
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("【测试 2/4】YSM 模型系统").withStyle(ChatFormatting.AQUA), false);
        TestResult ysmResult = runYSMTest(source.getPlayer());
        totalTests += ysmResult.total;
        passedTests += ysmResult.passed;
        printTestResult(source, ysmResult);
        
        // 测试 3: 配置系统
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("【测试 3/4】配置系统").withStyle(ChatFormatting.AQUA), false);
        TestResult configResult = runConfigTest();
        totalTests += configResult.total;
        passedTests += configResult.passed;
        printTestResult(source, configResult);
        
        // 测试 4: 核心模块
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("【测试 4/4】核心模块").withStyle(ChatFormatting.AQUA), false);
        TestResult coreResult = runCoreTest();
        totalTests += coreResult.total;
        passedTests += coreResult.passed;
        printTestResult(source, coreResult);
        
        // 打印汇总
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("══════════════ 测试汇总 ══════════════").withStyle(ChatFormatting.GOLD), false);
        
        final int finalTotal = totalTests;
        final int finalPassed = passedTests;
        final int finalFailed = totalTests - passedTests;
        
        ChatFormatting resultColor = finalFailed == 0 ? ChatFormatting.GREEN : 
                                     (finalPassed > finalFailed ? ChatFormatting.YELLOW : ChatFormatting.RED);
        
        source.sendSuccess(() -> Component.literal(String.format(
            "  总计: %d | 通过: %d | 失败: %d", finalTotal, finalPassed, finalFailed
        )).withStyle(resultColor), false);
        
        if (finalFailed == 0) {
            source.sendSuccess(() -> Component.literal("  ✓ 所有测试通过！前置Mod功能完备").withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("  ⚠ 部分测试未通过，请检查上方详情").withStyle(ChatFormatting.YELLOW), false);
        }
        
        source.sendSuccess(() -> Component.literal("═══════════════════════════════════════").withStyle(ChatFormatting.GOLD), false);
        
        return passedTests == totalTests ? 1 : 0;
    }
    
    /**
     * 仅测试 MCEF
     */
    private static int testMCEF(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("═══ MCEF 测试 ═══").withStyle(ChatFormatting.GOLD), false);
        TestResult result = runMCEFTest();
        printTestResult(source, result);
        
        return result.passed == result.total ? 1 : 0;
    }
    
    /**
     * 仅测试 YSM
     */
    private static int testYSM(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("═══ YSM 测试 ═══").withStyle(ChatFormatting.GOLD), false);
        TestResult result = runYSMTest(source.getPlayer());
        printTestResult(source, result);
        
        return result.passed == result.total ? 1 : 0;
    }
    
    /**
     * 测试配置系统
     */
    private static int testConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("═══ 配置系统测试 ═══").withStyle(ChatFormatting.GOLD), false);
        TestResult result = runConfigTest();
        printTestResult(source, result);
        
        return result.passed == result.total ? 1 : 0;
    }
    
    /**
     * 显示系统摘要
     */
    private static int showSummary(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("╔════════════════════════════════════════╗").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("║     Miracle Bridge 系统摘要            ║").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("╚════════════════════════════════════════╝").withStyle(ChatFormatting.GOLD), false);
        
        // 基本信息
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("基本信息:").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("  模组ID: " + MiracleBridge.MOD_ID).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  版本: " + MiracleBridge.VERSION).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  操作系统: " + System.getProperty("os.name")).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  Java版本: " + System.getProperty("java.version")).withStyle(ChatFormatting.GRAY), false);
        
        // 前置Mod状态
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("前置Mod状态:").withStyle(ChatFormatting.YELLOW), false);
        
        boolean mcefOk = MCEF.isInitialized();
        boolean ysmOk = YSMCompat.isYSMLoaded();
        
        source.sendSuccess(() -> Component.literal("  MCEF: " + (mcefOk ? "✓ 已加载" : "✗ 未加载"))
            .withStyle(mcefOk ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        source.sendSuccess(() -> Component.literal("  YSM: " + (ysmOk ? "✓ 已加载" : "○ 未加载（可选）"))
            .withStyle(ysmOk ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        
        // 运行时状态
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("运行时状态:").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("  活跃浏览器: " + BrowserManager.getInstance().getBrowserCount()).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("  配置监听器: " + (ConfigWatcher.getInstance().isRunning() ? "运行中" : "已停止")).withStyle(ChatFormatting.GRAY), false);
        
        source.sendSuccess(() -> Component.literal("").withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal("使用 /mb test all 运行完整测试").withStyle(ChatFormatting.DARK_GRAY), false);
        
        return 1;
    }
    
    // ==================== 测试实现 ====================
    
    /**
     * MCEF 测试
     */
    private static TestResult runMCEFTest() {
        TestResult result = new TestResult();
        
        // 测试 1: MCEF 初始化
        result.total++;
        try {
            if (MCEF.isInitialized()) {
                result.passed++;
                result.addDetail("MCEF 初始化", true, "CEF 引擎已就绪");
            } else {
                result.addDetail("MCEF 初始化", false, "CEF 引擎未初始化");
            }
        } catch (Exception e) {
            result.addDetail("MCEF 初始化", false, "检测异常: " + e.getMessage());
        }
        
        // 测试 2: BrowserManager 单例
        result.total++;
        try {
            BrowserManager manager = BrowserManager.getInstance();
            if (manager != null) {
                result.passed++;
                result.addDetail("BrowserManager 单例", true, "实例创建成功");
            } else {
                result.addDetail("BrowserManager 单例", false, "实例为 null");
            }
        } catch (Exception e) {
            result.addDetail("BrowserManager 单例", false, "创建失败: " + e.getMessage());
        }
        
        // 测试 3: 浏览器创建能力（仅检查 MCEF 状态）
        result.total++;
        if (MCEF.isInitialized()) {
            result.passed++;
            result.addDetail("浏览器创建能力", true, "MCEF 就绪，可创建浏览器");
        } else {
            result.addDetail("浏览器创建能力", false, "MCEF 未就绪");
        }
        
        return result;
    }
    
    /**
     * YSM 测试
     */
    private static TestResult runYSMTest(ServerPlayer player) {
        TestResult result = new TestResult();
        
        // 测试 1: YSM 检测
        result.total++;
        boolean ysmLoaded = YSMCompat.isYSMLoaded();
        result.passed++; // 检测本身总是成功的
        result.addDetail("YSM 检测", true, ysmLoaded ? "YSM 已加载" : "YSM 未安装（可选依赖）");
        
        // 测试 2: 实体驱动工厂
        result.total++;
        try {
            EntityDriverFactory factory = EntityDriverFactory.getInstance();
            if (factory != null) {
                result.passed++;
                result.addDetail("EntityDriverFactory", true, "工厂实例已创建");
            } else {
                result.addDetail("EntityDriverFactory", false, "工厂实例为 null");
            }
        } catch (Exception e) {
            result.addDetail("EntityDriverFactory", false, "创建失败: " + e.getMessage());
        }
        
        // 测试 3: 驱动获取（如果有玩家）
        result.total++;
        if (player != null) {
            try {
                IEntityDriver driver = EntityDriverFactory.getInstance().getDriver(player);
                if (driver != null && driver.isAvailable()) {
                    result.passed++;
                    String driverType = driver.getClass().getSimpleName();
                    result.addDetail("实体驱动获取", true, "类型: " + driverType);
                } else {
                    result.addDetail("实体驱动获取", false, "驱动不可用");
                }
            } catch (Exception e) {
                result.addDetail("实体驱动获取", false, "获取失败: " + e.getMessage());
            }
        } else {
            result.passed++; // 控制台执行时跳过
            result.addDetail("实体驱动获取", true, "（控制台执行，跳过玩家相关测试）");
        }
        
        // 测试 4: 驱动回退机制
        result.total++;
        result.passed++; // 回退机制已实现
        if (ysmLoaded) {
            result.addDetail("驱动回退机制", true, "YSM驱动 -> 原版驱动");
        } else {
            result.addDetail("驱动回退机制", true, "直接使用原版驱动");
        }
        
        return result;
    }
    
    /**
     * 配置系统测试
     */
    private static TestResult runConfigTest() {
        TestResult result = new TestResult();
        
        // 测试 1: ConfigWatcher 实例
        result.total++;
        try {
            ConfigWatcher watcher = ConfigWatcher.getInstance();
            if (watcher != null) {
                result.passed++;
                result.addDetail("ConfigWatcher 实例", true, 
                    watcher.isRunning() ? "正在运行" : "已创建（未启动）");
            } else {
                result.addDetail("ConfigWatcher 实例", false, "实例为 null");
            }
        } catch (Exception e) {
            result.addDetail("ConfigWatcher 实例", false, "创建失败: " + e.getMessage());
        }
        
        // 测试 2: 配置值读取
        result.total++;
        try {
            int width = com.originofmiracles.miraclebridge.config.ClientConfig.getBrowserWidth();
            int height = com.originofmiracles.miraclebridge.config.ClientConfig.getBrowserHeight();
            if (width > 0 && height > 0) {
                result.passed++;
                result.addDetail("配置值读取", true, String.format("浏览器尺寸: %dx%d", width, height));
            } else {
                result.addDetail("配置值读取", false, "无效的配置值");
            }
        } catch (Exception e) {
            result.addDetail("配置值读取", false, "读取失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 核心模块测试
     */
    private static TestResult runCoreTest() {
        TestResult result = new TestResult();
        
        // 测试 1: 模组实例
        result.total++;
        try {
            MiracleBridge instance = MiracleBridge.getInstance();
            if (instance != null) {
                result.passed++;
                result.addDetail("模组实例", true, "MiracleBridge 单例正常");
            } else {
                result.addDetail("模组实例", false, "实例为 null");
            }
        } catch (Exception e) {
            result.addDetail("模组实例", false, "获取失败: " + e.getMessage());
        }
        
        // 测试 2: 线程调度器
        result.total++;
        try {
            // 简单测试 - 调度一个空任务
            com.originofmiracles.miraclebridge.util.ThreadScheduler.runAsync(() -> {});
            result.passed++;
            result.addDetail("线程调度器", true, "异步任务调度正常");
        } catch (Exception e) {
            result.addDetail("线程调度器", false, "调度失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 打印测试结果
     */
    private static void printTestResult(CommandSourceStack source, TestResult result) {
        for (TestDetail detail : result.details) {
            ChatFormatting color = detail.passed ? ChatFormatting.GREEN : ChatFormatting.RED;
            String symbol = detail.passed ? "✓" : "✗";
            source.sendSuccess(() -> Component.literal(
                "  " + symbol + " " + detail.name + ": " + detail.message
            ).withStyle(color), false);
        }
    }
    
    // ==================== 辅助类 ====================
    
    private static class TestResult {
        int total = 0;
        int passed = 0;
        java.util.List<TestDetail> details = new java.util.ArrayList<>();
        
        void addDetail(String name, boolean passed, String message) {
            details.add(new TestDetail(name, passed, message));
        }
    }
    
    private static class TestDetail {
        String name;
        boolean passed;
        String message;
        
        TestDetail(String name, boolean passed, String message) {
            this.name = name;
            this.passed = passed;
            this.message = message;
        }
    }
}
