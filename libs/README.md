# MCEF Library Files

此目录用于存放 MCEF (Minecraft Chromium Embedded Framework) 的 JAR 文件。

## 为什么需要手动下载？

MCEF 的官方 Maven 仓库 (mcef-download.cinemamod.com) 可能暂时不可用或返回 403 错误。
作为替代方案，你可以手动下载 MCEF JAR 文件放到此目录。

## 下载步骤

1. 访问 [Modrinth MCEF 页面](https://modrinth.com/mod/mcef/versions?g=1.20.1&l=forge)
2. 下载适用于 Minecraft 1.20.1 Forge 的 MCEF JAR
3. 将下载的 JAR 文件放入此 `libs/` 目录
4. 重新构建项目: `./gradlew build`

## 注意

- 确保下载的是开发版本（不是混淆版本），用于编译时依赖
- 运行时仍需要玩家安装 MCEF mod
- 如果 MCEF 不可用，Miracle Bridge 会自动检测并优雅降级

## 文件命名

放入此目录的文件应命名为 `mcef*.jar`，例如：
- `mcef-2.1.6-1.20.1.jar`
