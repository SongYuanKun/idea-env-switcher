# Env Switcher GTR 无浏览器发布设计

原始设计日期：2026-09-29；实施修订：2026-10-02。

## 实施修订（2026-10-02）

以下记录实施时的现场证据和调整，优先于下方保留的原始草案。

- 0.3.2 已在 GitHub Release 和 Marketplace Stable 上线，Marketplace 更新 `1181789` 的 `approve=true`。两平台外层签名 ZIP 不同，包内 JAR 完全相同，SHA-256 为 `7682f6e5d2711922cadc413ca6f6e38330540dfa027a37a6d94b07c8cb934c95`。不再重复上传此版本。
- 用户已要求完成待办并直接集成 main。实施文件为 Bash 入口 `scripts/release-on-gtr.sh`、Python 3 标准库编排模块 `scripts/release_gtr.py`、安全测试 `scripts/tests/test_release_gtr.py`、README、CHANGELOG、Wrapper 分发校验值及本文。CI 检查发现 Actions Node 20/setup-java v4 弃用告警后，另外更新 build.yml 中三个 action 的版本并固定官方 commit SHA、runner 固定 ubuntu-24.04；其 build-only 职责不变，不安装持久 runner 或服务。
- 沿用已有 `~/.jdks/jdk-21.0.12.1+1`，不新增系统安装。官方 Adoptium Temurin 发布 tar 的 SHA-256 为 `ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94`，已校验并与本机全部 454 个文件/符号链接比对，tree digest 同为 `8752eb91e52234353bb3163bcbf05d8b943daeb181d6f13b1ae68613ccd5b5c5`。准备/实际上传前重新核验 JDK。
- `gh` 固定官方 2.70.0 Linux amd64 二进制，已从经官方 checksums 核验的 tar 确认其二进制摘要。Gradle 9.0.0 Wrapper 和分发包固定官方 SHA-256，完整 properties 拒绝未知值和重复键。独立 ZIP Signer CLI 固定 JetBrains 0.1.43 及官方 asset digest。无需 curl/jq 或第三方 Python 包；使用 git、gh、openssl 和标准库。
- 所有动作都在仓库级排他锁中运行，防止两个发布者重复上传或 prepare 清理另一个发布者已核验的产物。实际写入前以排他创建和 fsync 保存无秘密 inflight marker；未知结果只读回查一次，不自动重试或覆盖 marker。
- tag 与 HEAD 比较所有插件构建输入，允许后续文档/发布工具提交；Wrapper properties 的校验值变化独立按固定内容验证，其他构建输入不能漂移。所有命令要求 GTR/main/HEAD=origin/main；实际 prepare/publish 还要求已跟踪工作树干净。dry-run 可容忍非构建文档的编辑。
- 只读 dry-run 与已发布且内容一致的正式路径不需要签名材料或 token，输出外部写请求数 0。它们通过官方 HTTPS 检查 plugin ID/version 并比较所有运行时文件，提供分发内容一致性证据；没有作者证书时不宣称已独立验证作者签名。新的实际上传仍必须验证既有作者证书、加密私钥、准备清单以及独立 ZIP 签名。
- GTR 重建的 0.3.2 与历史 Mac 包仅 `META-INF/MANIFEST.MF` 的 Build-OS/Build-JVM 信息不同。旧版本只比较两平台现有包；新上传保持包内 JAR/所有运行时文件逐字节一致的要求，不为重新上传旧版本放宽内容检查。
- `prepare` 清单保存在 ignored `build/release/prepared.json`，记录 HEAD/version、证书及 ZIP/JAR/全部运行时文件摘要；正式上传重新验签并核验这些摘要。签名子进程关闭 daemon、build cache 和 configuration cache，秘密不进入日志、命令参数、持久环境或跨主机重定向。
- 现场运行了无密钥 Gradle test/verifyPlugin/buildPlugin，结果为 BUILD SUCCESSFUL、263.5701.42 Compatible。发布安全测试覆盖并发锁、认证刷新后发现待审核更新、未知上传结果成功/失败回读、零写请求、内容冲突及不安全凭据权限。真实 0.3.2 dry-run 已通过。
- 本机凭据目录尚不存在；Mac SSH 入口仍拒绝连接。真实签名 prepare 和后续新版本上传仍待用户提供现有凭据的安全位置，未声称这两项已完成。此限制不影响当前已上线的 0.3.2。

## 原始设计（2026-09-29，历史草案）

## 1. 背景与目标

Env Switcher 是公开 GitHub 仓库。现有 `.github/workflows/build.yml` 在 GitHub 托管的 `ubuntu-latest` runner 上运行无密钥的 `test` 和 `buildPlugin`，不负责签名或发布。目标是让构建、测试、签名、GitHub Release 与 JetBrains Marketplace 发布均由 `kun-GTR` 发起并执行；正常发布路径不使用 Chrome/Chromium，Mac 不承担后续构建、签名或发布。

不在 GTR 安装 GitHub self-hosted runner。GitHub 官方不建议公开仓库使用持久自托管 runner；发布由人在 GTR 上按需启动本地脚本，避免公开仓库事件触发带凭据的主机任务。

## 2. 范围与非目标

后续实施仅新增 `scripts/release-on-gtr.sh` 并补充 `README.md` 的 GTR 发布说明；现有 GitHub Actions build-only 工作流保持原样。发布脚本包含 `prepare`、`publish` 和只读的 `publish --dry-run`。不设置定时器、守护服务或 tag 监听，不修改插件功能，也不把发布任务转移到 Mac。本文不授权提交、推送或发布。

## 3. GTR 工具链

- 在 `/home/kun/.local/opt` 安装**固定版本**的 Temurin JDK 21；记录下载来源和预期 SHA-256，安装前用 `sha256sum` 校验，校验失败即停。脚本显式设置 `JAVA_HOME`，并将该 JDK 的 `bin` 与 `/home/kun/.local/bin` 加入子进程 `PATH`；不修改系统默认 Java。
- 使用仓库已有 Gradle Wrapper（当前 `gradle/wrapper/gradle-wrapper.properties` 指向 Gradle 9.0.0）和 `/home/kun/.local/bin/gh`。脚本还依赖 `curl`、`jq`、`openssl`、`sha256sum`；预检其可执行性和版本。外部工具、JDK 与下载物须固定版本并校验来源及摘要，Gradle Wrapper 和依赖解析异常时停止，不以未校验的临时下载替代。
- 当前 `build.gradle.kts` 已定义 Java 21、`verifyPlugin`、`signPlugin` 和从环境变量读取的签名参数；脚本调用这些现有任务。Plugin Verifier 所需 IDE/依赖缺失或离线验证失败时，`prepare` 失败，不跳过检查。

## 4. 凭据与权限

GTR 凭据目录 `/home/kun/.config/idea-env-switcher` 权限为 `0700`；`signing/` 目录同为 `0700`。`signing/chain.crt`、`signing/private.pem`、`signing/private-key-password`、`marketplace.token` 均为 `0600`。`private.pem` 始终保留加密形态；权限、加密状态和证书链有效性均纳入预检。

现有证书与加密私钥只允许**一次性通过 SSH 从 Mac 迁移到 GTR**。这是初始凭据迁移的唯一 Mac 参与点，不在本轮执行；迁移完成后 Mac 不再参与构建、签名或发布。私钥密码与 Marketplace token 不进入仓库、持久环境配置、命令参数、日志、构建缓存或 shell history。创建受限文件时设 `umask 077`，在 GTR 交互终端用 `read -rsp` 读取，再由 shell 内建 `printf` 写入文件；不回显值。

Marketplace permanent token 由用户在 **GTR Firefox** 登录 JetBrains Profile 的 **My Tokens** 页面一次性生成；不使用 GTR Chrome/Chromium。需要用户登录和生成 token 时暂停；没有 token 不尝试上传。GitHub 认证沿用 GTR `gh` CLI 的 credential store，预检 `gh auth status`，不把 PAT 复制到仓库或另建凭据文件。脚本以 `set -euo pipefail` 运行，禁止 `set -x`；秘密仅在实际签名或 API 子进程的环境中短暂提供，退出时 `unset`，签名运行禁用 Gradle 构建缓存和配置缓存，避免秘密落入持久缓存。

## 5. 发布脚本接口与流程

### 通用预检

`prepare` 和 `publish` 都要求 `hostname` 精确为 `kun-GTR`，Git 工作树无已跟踪文件的暂存或未暂存改动，当前分支为 `main`，且 `HEAD` 等于更新后的 `origin/main`。先只读查询远端并更新本地跟踪引用及 tag；若网络失败或远端状态无法确认则停止。读取 `build.gradle.kts` 的 `version`，要求 `CHANGELOG.md` 存在对应版本条目，且 tag `v<version>` 存在。tag 指向的 commit 可以不同于 `HEAD`，但两者的 Git tree 对象必须完全相同；这覆盖 v0.3.2 的 revert/reapply 历史，同时排除代码内容漂移。发布说明取人工维护的对应 changelog/release notes，绝不从原始 git log 自动生成。

预检还验证凭据文件权限、工具版本、JDK 和 CLI 校验值、GitHub/Marketplace 只读 API 可达性及目标版本状态。只读检查不打印 token、私钥或密码。`publish` 重新检查主机、Git、版本、tag、凭据、签名产物和远端版本，不能只信任上一次 `prepare` 的结论。

### `scripts/release-on-gtr.sh prepare`

执行预检后，在 GTR 使用现有 Wrapper 运行 `./gradlew clean test verifyPlugin signPlugin`；实际调用附加 `--no-daemon --no-build-cache --no-configuration-cache`，避免长驻进程和构建缓存保留签名输入。只将 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD` 作为该 Gradle 子进程的临时环境变量提供。构建成功后，用**独立的 Marketplace ZIP Signer CLI** 和 `chain.crt` 对生成的签名 ZIP 验签，并检查包内插件 JAR、版本和 ZIP 摘要。CLI 与 `signPlugin` 不共享“验签成功”的判断路径。

输出本地发布预览：版本、tag/HEAD tree、签名 ZIP 路径与 SHA-256、包内 JAR 摘要、人工发布说明、GitHub Release/Marketplace 目标版本的只读检测结果。可把无秘密的预览清单置于 ignored 的 `build/`；不创建 GitHub Release、不上传 Marketplace，也不进行其他对外写入。任何测试、Verifier、签名或独立验签失败都使 `prepare` 失败。

### `scripts/release-on-gtr.sh publish [--dry-run]`

`--dry-run` 重做关键预检、读取远端状态并输出将执行或将跳过的步骤，不发写请求。正式 `publish` 要求同一 HEAD/version 的 `prepare` 产物和验签证据；检查通过且确有缺失目标时，用户须在**当前发布上下文**明确确认目标版本和两个发布目的地，脚本才允许写入。确认不能被旧会话、环境变量或仅仅运行过 `prepare` 代替。

先检查 GitHub tag Release 和附件，再检查 JetBrains Marketplace 对应 plugin/version/channel。已存在时核验版本、签名状态、附件或下载包内容：优先比较外层 ZIP SHA-256；若签名时间戳等元数据使 ZIP 摘要不同，则独立验签并逐字节比较包内插件 JAR。内容不一致立即停止；内容一致则视为该目标已完成并跳过，不重复上传。发布 v0.3.2 的 dry-run/已存在路径应识别 GitHub 和 Marketplace 都已上线且零写请求。

有缺失目标时，先用 `gh` 创建 GitHub Release，附加 GTR 生成的**签名 ZIP**，正文采用人工维护的该版本 changelog/release notes；只读回查 tag、正文、附件和内容。随后通过 `POST https://plugins.jetbrains.com/api/updates/upload` 上传相同签名 ZIP，`pluginId=34289`，channel 为 Stable（空值），不传 `isHidden=true`；最后只读回查 Marketplace 版本、channel 和下载包内容。若某次写请求的结果未知（超时、连接中断或响应无法解析），先只读回查；确认已完成则按已存在路径核验，仍不能确认就停止并报告，绝不自动重试写请求。GitHub 成功而 Marketplace 失败时保留已完成状态，后续再次运行时只处理缺失目标。

## 6. 安全模型

受保护资产为签名私钥、私钥密码、Marketplace token 和 GitHub auth。主要威胁是日志或 shell history 泄露、命令行和 `/proc` 暴露、恶意依赖或脚本、错误版本、重复提交，以及 GTR 主机入侵。

控制措施包括受限文件权限、加密私钥、无持久秘密环境变量、秘密不经命令参数、固定版本与下载校验、无需浏览器的常规发布路径、不使用公开仓库 self-hosted runner、Git/tag/tree 分阶段校验、发布前当前上下文确认、远端内容回读，以及未知写入结果不盲重试。脚本日志只记录操作状态和非秘密摘要，不记录请求头、token 或含秘密的进程环境。

残余风险：GTR 若被完全攻破，攻击者仍可能读取磁盘和运行时凭据；同一用户权限下的运行时 `/proc` 暴露也不能只靠脚本消除。JetBrains permanent token 没有可替代的 OIDC 发行路径，必须支持在 Profile 撤销、轮换，并更新 GTR 受限文件。

## 7. 验证与验收

1. 对脚本运行 `bash -n`；若 GTR 有 `shellcheck`，再运行 `shellcheck`。
2. 在 0.3.2 的 GTR `main` 上运行 `prepare`，实际完成当前 GTR 构建、测试、Plugin Verifier、签名和 Marketplace ZIP Signer CLI 独立验签，记录退出码和无秘密的产物摘要。
3. 运行 `publish --dry-run` 及正式发布的已存在检测，确认 GitHub Release 与 Marketplace 0.3.2 均被识别为已上线，写请求数为零。
4. 下载 Marketplace 0.3.2 包并提取内部插件 JAR，与 GTR 新生成签名包内对应 JAR 做逐字节比较。GTR 本地产物必须用现有作者 `chain.crt` 独立验签通过。Marketplace 外层 ZIP 可因平台重写或重签而 SHA-256 不同，不要求它使用作者 `chain.crt` 通过同一种验签；官方 HTTPS 下载成功、更新元数据正确且内部 JAR 一致，构成平台分发内容证据。
5. 最终 `git status` 只显示预期设计文档、脚本与 README 的跟踪修改；所有构建产物留在 ignored 路径，不进入提交。现有 `.github/workflows/build.yml` 无变化。

## 8. 实施文件与停止条件

后续实施仅修改 `docs/superpowers/specs/2026-09-29-gtr-headless-release-design.md`、`scripts/release-on-gtr.sh`、`README.md`；不修改 `.github/workflows/build.yml`。若缺 JDK、签名材料或 Marketplace token，或需用户在 GTR Firefox 登录 JetBrains Profile 并生成 token，则停在相应人工步骤；没有 token 不上传。任何预检、内容核验或远端回查不确定时停止并报告。

本设计不包含提交、推送或发布新版本的授权；这些动作须在后续获得各自明确授权后才能执行。
