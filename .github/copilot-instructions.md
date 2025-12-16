# Miracle Bridge - AI Coding Agent Instructions

## 项目概览

**Miracle Bridge** 是一个 Minecraft Forge 模组 (1.20.1)，作为 Origin of Miracles 生态系统的核心基础设施。它连接 Minecraft Java 引擎与现代 Web/AI 技术，而非直接提供游戏玩法。

- **模组 ID**: `miraclebridge`
- **包名**: `com.originofmiracles.miraclebridge`
- **版本**: `0.1.0-alpha`
- **Forge 版本**: `1.20.1-47.2.0`

### 核心架构 (六大模块)

```
React/TypeScript 前端 (Shittim OS)
        ↓ bridge:// 协议
┌───────────────────────────────────────────────────┐
│  browser/   → MCEF Chromium 浏览器封装            │
│  bridge/    → JS ↔ Java 双向通信 API              │
│  entity/    → 实体 AI 接口 + YSM 兼容层           │
│  network/   → Forge SimpleChannel 网络通信        │
│  config/    → 热重载配置系统                      │
│  util/      → 线程调度器 + 工具类                 │
└───────────────────────────────────────────────────┘
        ↓ Forge 事件总线
    Minecraft 1.20.1 (Forge 47.2.0)
```

## 开发环境

- **JDK 17** (必需) - 推荐 Microsoft Build of OpenJDK
- **Mappings**: Official Mojang Mappings
- **首次启动**: MCEF 会自动下载 ~200MB CEF 二进制文件
- **MCEF JAR**: 从本地 `libs/mcef-forge-2.1.6-1.20.1.jar` 加载

```bash
./gradlew setupDecompWorkspace    # 设置工作区
./gradlew genIntellijRuns         # IntelliJ 运行配置
./gradlew runClient               # 启动开发客户端
./gradlew clean build --refresh-dependencies  # 清理重建
```

## 代码规范

### 命名约定
- 类名: `PascalCase` (如 `BrowserManager`, `ThreadScheduler`)
- 接口: 以 `I` 开头 (如 `IEntityDriver`)
- 方法/变量: `camelCase`
- 常量: `UPPER_SNAKE_CASE`
- 数据包: `方向 + 功能 + Packet` (如 `C2SBridgeActionPacket`, `S2CFullSyncPacket`)

### 注释语言
- **所有 Javadoc 和代码注释使用中文**
- 公共 API 必须包含 Javadoc
- 配置项使用 `[HOT]`/`[RESTART]` 标记生效时机

### 线程安全规则 (关键!)
使用 `ThreadScheduler` 工具类管理线程切换：

| 线程类型 | 用途 | API |
|---------|------|-----|
| 客户端主线程 | 游戏逻辑、GUI | `ThreadScheduler.runOnClientThread()` |
| 服务端主线程 | 实体、世界 | `ThreadScheduler.runOnServerThread()` |
| 异步线程池 | 网络、AI、文件 I/O | `ThreadScheduler.runAsync()` |
| 延迟调度器 | 定时任务 | `ThreadScheduler.schedule()` |

⚠️ **严禁在主线程上执行阻塞操作！**

## 关键模块指南

### browser/ - MCEF 浏览器封装

**核心类**:
- `BrowserManager`: 单例管理器，处理浏览器生命周期
- `MiracleBrowser`: MCEFBrowser 的高层封装

```java
// 使用配置默认值创建浏览器
BrowserManager.getInstance().createBrowser("main", "http://localhost:5173");

// 自定义参数
BrowserManager.getInstance().createBrowser("overlay", url, 1920, 1080, true);

// 执行 JavaScript
MiracleBrowser browser = BrowserManager.getInstance().getBrowser("main");
browser.executeJavaScript("console.log('Hello from Java')");
```

### bridge/ - JS ↔ Java 通信

**BridgeAPI** 提供双向通信:

```java
// 注册本地处理器
bridgeAPI.register("getPlayerInfo", request -> {
    JsonObject response = new JsonObject();
    response.addProperty("name", "Player");
    response.addProperty("health", 20);
    return response;
});

// 推送事件到 JS
bridgeAPI.pushEvent("gameStateChanged", eventData);

// 需要服务端处理的请求
CompletableFuture<JsonObject> result = bridgeAPI.requestFromServer("teleport", data);
```

JS 端调用:
```javascript
const result = await fetch('bridge://api/getPlayerInfo', {
  method: 'POST',
  body: JSON.stringify({})
}).then(r => r.json());
```

### entity/ - 实体 AI 接口

**IEntityDriver** 接口定义标准化实体控制:
- `playAnimation(String animationId)` - 播放动画
- `setExpression(String expressionId)` - 设置表情
- `navigateTo(BlockPos target)` - 导航到目标
- `lookAt(BlockPos target)` - 看向目标
- `halt()` - 停止所有行为

**YSMCompat** 通过命令 API 与 YSM 交互:
```java
if (YSMCompat.isYSMLoaded()) {
    YSMCompat.playAnimation(player, "wave");
    YSMCompat.setModel(player, "model_id", "texture_id");
    YSMCompat.executeMolang(player, "v.expression='happy'");
}
```

### network/ - Forge 网络通信

基于 `SimpleChannel` 的数据包系统:
- `S2CFullSyncPacket`: 服务端 → 客户端全量同步
- `C2SBridgeActionPacket`: 客户端 → 服务端桥接请求

```java
// 发送到指定玩家
ModNetworkHandler.sendToPlayer(player, new S2CFullSyncPacket(data));

// 发送到服务端
ModNetworkHandler.sendToServer(new C2SBridgeActionPacket(action, payload));
```

### config/ - 热重载配置系统

支持运行时配置修改和文件监听:

**ClientConfig** (客户端配置):
- `browser.defaultWidth/Height` - 浏览器尺寸 [RESTART]
- `browser.transparentBackground` - 透明背景 [RESTART]
- `browser.devServerUrl` - Vite 开发服务器地址 [HOT]
- `debug.enabled/logBridgeRequests/logJsExecution` - 调试开关 [HOT]

**ServerConfig** (服务端配置):
- `maxRequestSize` - 最大请求体大小

```java
// 获取配置值
String devUrl = ClientConfig.getDevServerUrl();
boolean debug = ClientConfig.DEBUG_ENABLED.get();

// 配置文件监听器自动启动
ConfigWatcher.getInstance().start();
```

### util/ - 工具类

- `ThreadScheduler`: 统一线程调度 API
- `MessageHelper`: 消息格式化辅助

## 平台兼容性 (必须遵守)

| 平台 | MCEF | YSM |
|------|------|-----|
| Windows 10/11 | ✅ | ✅ |
| Linux glibc 2.31+ | ✅ | ✅ |
| macOS 11+ | ✅ | ❌ |

- YSM 功能必须使用 `YSMCompat.isYSMLoaded()` 检查
- 所有 YSM 依赖代码需优雅降级
- MCEF 初始化检查: `MCEF.isInitialized()`

## 设计原则

1. **事件优先**: 优先使用 `MinecraftForge.EVENT_BUS`，避免 Mixin
2. **模块化**: 按职责分离到 `browser/`, `bridge/`, `entity/`, `network/`, `config/`, `util/`
3. **安全性**: JS→Java 调用必须有请求大小检查和输入校验
4. **软依赖**: YSM、MCEF 不可用时优雅降级
5. **热重载**: 配置项标记 `[HOT]` 表示支持运行时修改

## 依赖配置

```groovy
repositories {
    // FabricMC maven - MCEF 传递依赖
    maven { url = 'https://maven.fabricmc.net/' }
    
    // MCEF Repository
    maven { url = 'https://mcef-download.cinemamod.com/repositories/releases' }
    maven { url = 'https://mcef-download.cinemamod.com/repositories/snapshots' }
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.2.0'
    
    // MCEF - 从本地 libs 目录加载
    compileOnly files('libs/mcef-forge-2.1.6-1.20.1.jar')
    
    // 工具库
    compileOnly 'com.google.code.findbugs:jsr305:3.0.2'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

## 提交规范

使用约定式提交:
- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档更新
- `refactor:` 代码重构
- `test:` 测试相关
- `chore:` 构建/工具变更

## 常见任务速查

```java
// 创建浏览器并显示页面
BrowserManager.getInstance().createBrowser("ui", "http://localhost:5173");

// 注册 Bridge API 处理器
bridgeAPI.register("myAction", request -> { ... });

// 在正确线程执行代码
ThreadScheduler.runOnClientThread(() -> { /* 客户端逻辑 */ });
ThreadScheduler.runAsync(() -> { /* 耗时操作 */ });

// YSM 兼容性检查
if (YSMCompat.isYSMLoaded()) { /* YSM 专属功能 */ }

// 发送网络数据包
ModNetworkHandler.sendToServer(packet);
ModNetworkHandler.sendToPlayer(player, packet);
```
