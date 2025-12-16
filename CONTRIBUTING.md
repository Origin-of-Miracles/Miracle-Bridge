# 为 Miracle Bridge 做贡献

感谢您对 Miracle Bridge 的贡献兴趣！

## 开发环境设置

1. **前置要求**
   - JDK 17
   - Git
   - IntelliJ IDEA 或 Eclipse（推荐）

2. **克隆并设置**
   ```bash
   git clone https://github.com/Origin-of-Miracles/Miracle-Bridge.git
   cd Miracle-Bridge
   ./gradlew setupDecompWorkspace
   ./gradlew genIntellijRuns
   ```

3. **导入到 IDE**
   - IntelliJ: 以项目方式打开 `build.gradle`
   - Eclipse: 运行 `./gradlew eclipse` 然后导入

## 编码规范

请遵循[开发指南](../Docs/docs/dev/miracle_bridge_dev_guide.md#3-编码规范-coding-standards)中的规范：

- **命名**: 类名使用 PascalCase，方法名使用 camelCase
- **文档**: 所有公共 API 都需要 Javadoc
- **线程安全**: 尊重渲染/逻辑线程边界
- **优先使用事件**: 能用 Forge 事件的优先使用事件而非 Mixin

## Pull Request 流程

1. **创建功能分支**
   ```bash
   git checkout -b feature/你的功能名称
   ```

2. **进行更改**
   - 遵循编码规范
   - 如适用，添加测试
   - 更新文档

3. **充分测试**
   ```bash
   ./gradlew build
   ./gradlew runClient
   ```

4. **提交并推送**
   ```bash
   git commit -m "feat: 添加惊艳功能"
   git push origin feature/你的功能名称
   ```

5. **打开 Pull Request**
   - 描述你更改了什么以及为什么
   - 引用相关的 issue

## 提交信息格式

使用约定式提交：

- `feat:` 新功能
- `fix:` 错误修复
- `docs:` 仅文档更改
- `refactor:` 代码重构
- `test:` 添加测试
- `chore:` 维护任务

## 贡献领域

### 高优先级
- React 资源处理器
- 网络数据包系统
- 实体寻路
- TTS 集成

### 适合新手的问题
- 文档改进
- 代码注释
- 示例实现
- 测试覆盖率

## 有疑问？

加入我们的 [Discord](https://discord.gg/originofmiracles) 或开启讨论。

## 许可证

通过贡献，您同意您的贡献将根据 AGPL-3.0 许可证授权。
