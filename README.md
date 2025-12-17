# Miracle Bridge

**Origin of Miracles 生态系统的核心前置模组**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)](https://www.minecraft.net/)
[![Forge](https://img.shields.io/badge/Forge-47.2.0-orange.svg)](https://files.minecraftforge.net/)
[![License](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)

## 概述

Miracle Bridge 不是一个玩法模组——它是连接 Minecraft Java 引擎与现代 Web/AI 技术的**基础设施**。

### 核心能力

- **Chromium Webview 集成** - 通过 MCEF 实现完整浏览器渲染
- **JS ↔ Java 桥梁** - React 前端与 Minecraft 后端的双向通信
- **实体 AI 接口** - 用于 LLM 驱动角色行为的标准化 API
- **YSM 兼容** - 与 Yes Steve Model 无缝集成以实现高级动画

## 快速开始

### 前置要求

- **JDK 17** ([下载](https://docs.microsoft.com/zh-cn/java/openjdk/download#openjdk-17))
- **Minecraft 1.20.1**
- **Forge 47.2.0+**
- **网络连接**（仅首次启动，用于 MCEF 设置）

### 开发环境设置

```bash
./gradlew setupDecompWorkspace
./gradlew genIntellijRuns
./gradlew runClient
```

## 平台支持

| 平台 | MCEF (浏览器) | YSM (动画) |
|------|---------------|------------|
| Windows 10/11 | 支持 | 支持 |
| Linux glibc 2.31+ | 支持 | 支持 |
| macOS 11+ | 支持 | 不支持* |

*YSM 使用与 macOS 不兼容的 C++ 原生库。功能将优雅降级。

## 架构

```
┌─────────────────────────────────────────┐
│       React/TypeScript 前端              │
│  (OS 界面, MomoTalk, 战斗界面)          │
└───────────────┬─────────────────────────┘
                │ JS ↔ Java 桥梁
┌───────────────▼─────────────────────────┐
│         Miracle Bridge (本模组)          │
│  ┌──────────────────────────────────┐   │
│  │  MCEF (Chromium 渲染)            │   │
│  ├──────────────────────────────────┤   │
│  │  BridgeAPI (通信层)              │   │
│  ├──────────────────────────────────┤   │
│  │  实体驱动器 (YSM/原版)           │   │
│  └──────────────────────────────────┘   │
└───────────────┬─────────────────────────┘
                │
┌───────────────▼─────────────────────────┐
│      Minecraft 1.20.1 (Forge)           │
│   (世界状态, 实体系统, 等)              │
└─────────────────────────────────────────┘
```

## 文档

- **[开发指南](../Docs/docs/dev/miracle_bridge_dev_guide.md)** - 完整技术文档
- **[贡献指南](CONTRIBUTING.md)** - 如何贡献

## API 示例

### JavaScript → Java

```javascript
// 从 React 调用 Minecraft 函数
const playerInfo = await fetch('bridge://api/getPlayerInfo', {
  method: 'POST',
  body: JSON.stringify({})
}).then(r => r.json());

console.log(playerInfo.name, playerInfo.health);

// 传送玩家
await fetch('bridge://api/teleport', {
  method: 'POST',
  body: JSON.stringify({ x: 100, y: 64, z: 200 })
});
```

### Java → JavaScript

```java
// 向 React 前端推送事件
BridgeAPI bridge = new BridgeAPI(browser);
bridge.pushEvent("newMail", Map.of(
  "from", "老师",
  "subject", "任务更新",
  "body", "查看档案以了解详情。"
));
```

### 实体控制 (YSM)

```java
// 控制学生动画
YSMEntityDriver driver = new YSMEntityDriver(player);
driver.playAnimation("wave");
driver.setExpression("happy");
driver.lookAt(new BlockPos(100, 64, 200));
```

## 依赖项

- **[MCEF](https://github.com/CinemaMod/mcef)** - Chromium 嵌入式框架
- **[Yes Steve Model](https://ysm.cfpa.team/)** (可选) - 高级角色模型
- **Forge** - 模组加载器
- **Gson** - JSON 序列化

## 许可证

本项目采用 **AGPL-3.0 许可证** - 详见 [LICENSE](LICENSE)。

## 贡献

我们欢迎贡献！请先阅读我们的[贡献指南](CONTRIBUTING.md)。

## 致谢

- **CinemaMod 团队** - MCEF 及持续维护
- **TartaricAcid** - Yes Steve Model
- **Mojang** - Minecraft
- **Forge 团队** - 模组框架

## 相关项目

- **[Origin of Miracles 文档](../Docs)** - 项目文档
- **[Shittim OS](../Shittim-OS)** - 游戏内操作系统界面

---

*由 Origin of Miracles 开发团队构建*
