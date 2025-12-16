# Miracle Bridge - AI Coding Agent Instructions

## 项目概览

**Miracle Bridge** 是一个 Minecraft Forge 模组 (1.20.1)，作为 Origin of Miracles 生态系统的核心基础设施。它连接 Minecraft Java 引擎与现代 Web/AI 技术，而非直接提供游戏玩法。

### 核心架构 (四大支柱)

```
React/TypeScript 前端 (Shittim OS)
        ↓ bridge:// 协议
┌─────────────────────────────────────┐
│  browser/   → MCEF Chromium 封装    │
│  bridge/    → JS ↔ Java 双向通信    │
│  entity/    → 实体 AI 接口 + YSM    │
└─────────────────────────────────────┘
        ↓ Forge 事件总线
    Minecraft 1.20.1 (Forge 47.2.0)
```

## 开发环境

- **JDK 17** (必需) - 推荐 Microsoft Build of OpenJDK
- **Mappings**: Official Mojang Mappings
- **首次启动**: MCEF 会自动下载 ~200MB CEF 二进制文件

```bash
./gradlew setupDecompWorkspace    # 设置工作区
./gradlew genIntellijRuns         # IntelliJ 运行配置
./gradlew runClient               # 启动开发客户端
./gradlew clean build --refresh-dependencies  # 清理重建
```

## 代码规范

### 命名约定
- 类名: `PascalCase` (如 `BrowserManager`)
- 接口: 以 `I` 开头 (如 `IEntityDriver`)
- 方法/变量: `camelCase`
- 常量: `UPPER_SNAKE_CASE`

### 注释语言
- **所有 Javadoc 和代码注释使用中文**
- 公共 API 必须包含 Javadoc

### 线程安全规则 (关键!)
- **渲染线程**: 所有 Webview 纹理操作
- **Server/Client Thread**: 游戏逻辑 (实体、物品)
- **异步线程池**: 网络请求、AI 推理、文件 I/O
- ⚠️ **严禁阻塞主线程**

## 关键模块指南

### browser/ - MCEF 浏览器封装
```java
// 示例: 创建透明背景浏览器
MiracleBrowser browser = new MiracleBrowser(true);
browser.create("https://shittim.os/", 1920, 1080);
browser.executeJavaScript("console.log('Hello from Java')");
```

### bridge/ - JS ↔ Java 通信
```java
// 注册新的桥梁处理器
bridgeAPI.register("myAction", request -> {
    JsonObject response = new JsonObject();
    response.addProperty("result", "success");
    return response;
});

// 推送事件到 JS
bridgeAPI.pushEvent("gameStateChanged", eventData);
```

JS 端通过 `bridge://api/{action}` 协议调用:
```javascript
await fetch('bridge://api/myAction', { method: 'POST', body: JSON.stringify({}) })
```

### entity/ - 实体 AI 接口
- `IEntityDriver`: 标准化实体控制接口
- `YSMCompat`: Yes Steve Model 命令封装 (通过 `/ysm` 命令交互，无直接 Java API)

## 平台兼容性 (必须遵守)

| 平台 | MCEF | YSM |
|------|------|-----|
| Windows 10/11 | ✅ | ✅ |
| Linux glibc 2.31+ | ✅ | ✅ |
| macOS 11+ | ✅ | ❌ |

- YSM 功能必须使用 `YSMCompat.isYSMLoaded()` 检查
- 所有 YSM 依赖代码需优雅降级

## 设计原则

1. **事件优先**: 优先使用 `MinecraftForge.EVENT_BUS`，避免 Mixin
2. **模块化**: 按职责分离到 `browser/`, `bridge/`, `entity/`, `network/`
3. **安全性**: JS→Java 调用必须有权限检查和输入校验
4. **软依赖**: YSM、MCEF 不可用时优雅降级

## 依赖配置

```groovy
// MCEF 仓库
maven { url = 'https://mcef-download.cinemamod.com/repositories/releases' }

// 依赖声明
compileOnly fg.deobf('com.cinemamod:mcef:2.1.6-1.20.1')
runtimeOnly fg.deobf('com.cinemamod:mcef-forge:2.1.6-1.20.1')
```

## 提交规范

使用约定式提交: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
