# Env Switcher

IntelliJ IDEA 插件：在项目中一键切换命名环境配置（dev / staging / prod …）。

切换后会把当前 profile 的变量写入项目根目录 `.env`，并在状态栏显示当前环境。

## Features

- 从项目根目录 `env-profiles.json` 读取多套环境
- `Tools → Switch Environment`（快捷键 `Ctrl+Alt+E`）快速切换
- 自动生成 / 覆盖 `.env`
- 状态栏显示当前 profile，点击可再次切换
- `Tools → Reload Environment Profiles` 热重载配置

## Quick start

1. 将 [examples/env-profiles.json](examples/env-profiles.json) 复制到你的项目根目录，命名为 `env-profiles.json`，按需修改。
2. 用 Gradle 运行沙箱 IDE：

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew runIde
```

3. 在沙箱中打开任意带 `env-profiles.json` 的项目，执行 **Tools → Switch Environment**。

### `env-profiles.json` 格式

```json
{
  "profiles": [
    {
      "name": "dev",
      "description": "Local development",
      "env": {
        "APP_ENV": "dev",
        "API_BASE": "http://localhost:8080"
      }
    }
  ]
}
```

## Build

```bash
./gradlew buildPlugin
```

产物：`build/distributions/idea-env-switcher-0.1.0.zip`，可在 IDE 中 **Install Plugin from Disk**。

## Requirements

- JDK 21+
- IntelliJ IDEA 2024.3+（`sinceBuild=243`）

## Roadmap (post-0.1)

- [ ] 记住上次选择的 profile（workspace 持久化）
- [ ] 将变量注入 Run Configuration
- [ ] Settings 页：编辑 profile / 忽略规则
- [ ] 支持 `.env.*.yml` 多文件布局
- [ ] 发布到 JetBrains Marketplace

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).
