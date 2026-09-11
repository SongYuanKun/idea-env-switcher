# Contributing

感谢你对 Env Switcher 的兴趣。

## 开发环境

- JDK 21+
- IntelliJ IDEA（建议 Ultimate 或 Community 2024.3+）
- 克隆仓库后用 IDEA 打开，等待 Gradle 同步

常用命令：

```bash
./gradlew test
./gradlew runIde
./gradlew buildPlugin
```

## 提交约定

- 一个 PR 只做一件事
- 代码注释用中文（简洁）；对外文档可用中英双语
- 新增解析 / 文件写入逻辑请附带单元测试
- 不要提交 `.env`、密钥或本机路径

## Issue / PR

- Bug：说明 IDE 版本、复现步骤、期望与实际行为
- Feature：说明使用场景与是否愿意实现

本项目为非商业开源软件，不提供付费支持。
