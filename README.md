# KeroBrowser 开发文档

> **项目代号**: MyBrowser / KeroBrowser
> **核心引擎**: Mozilla GeckoView (Firefox Engine)
> **架构**: Android Native (Kotlin) + Remote Build Server

## 1. 项目概述 (Project Overview)

这是一个基于 **Mozilla GeckoView** 内核的 Android 浏览器项目。
与基于 System WebView 的浏览器不同，本项目内嵌了完整的 Gecko 渲染引擎，意味着：
*   **独立性**：不受 Android 系统 WebView 版本限制，内核独立更新。
*   **扩展性**：原生支持 WebExtensions (如 uBlock Origin)。
*   **隐私性**：完全可控的追踪保护和数据隔离。

本项目采用 **“手机开发 + 服务器编译”** 的特殊工作流，旨在利用服务器的高性能和高带宽进行构建和发布。

---

## 2. 技术栈 (Tech Stack)

*   **编程语言**: Kotlin (JVM Target 17)
*   **构建工具**: Gradle 8.5 (配合 AGP 8.2.0)
*   **核心依赖**:
    *   `org.mozilla.geckoview:geckoview-omni:{version}` (稳定版通道)
    *   AndroidX / Material Design Components
*   **目标设备**:
    *   **Min SDK**: 24 (Android 7.0)
    *   **Target SDK**: 34 (Android 14)
    *   **主要架构**: `arm64-v8a` (针对现代 Android 手机优化)

---

## 3. 项目结构 (Directory Structure)

```text
MyBrowser/
├── .gitignore                # Git 忽略配置 (非常重要，防止上传构建产物)
├── build.gradle              # 项目级构建脚本
├── settings.gradle           # 模块引用设置
├── gradle.properties         # 全局配置 (开启 AndroidX)
├── local.properties          # SDK 路径配置 (不上传 Git，需本地生成)
└── app/                      # 主模块
    ├── build.gradle          # 模块级构建脚本 (含 GeckoView 版本和分包配置)
    └── src/
        └── main/
            ├── AndroidManifest.xml   # 权限与 Activity 声明
            ├── java/com/kero/browser/
            │   └── MainActivity.kt   # 核心逻辑 (加载内核)
            └── res/layout/
                └── activity_main.xml # 界面布局
```

---

## 4. 构建环境搭建 (Build Environment Setup)

本项目推荐在 **Linux 服务器 (Debian/Ubuntu)** 上进行无头(Headless)编译。

### 4.1 基础依赖
确保服务器安装了 JDK 17 和基础工具：
```bash
sudo apt update
sudo apt install openjdk-17-jdk unzip wget git curl -y
```

### 4.2 Android SDK (命令行版)
1.  下载 Google `commandlinetools-linux`。
2.  解压并重命名目录结构为 `cmdline-tools/latest`。
3.  配置环境变量：
    ```bash
    export ANDROID_HOME=$HOME/android-sdk
    export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
    ```
4.  安装构建组件：
    ```bash
    yes | sdkmanager --licenses
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
    ```

### 4.3 Gradle (手动安装)
由于 AGP 8.2.0 需要 Gradle 8.2+，系统自带的通常太老。
1.  下载 **Gradle 8.5** 二进制包 (`gradle-8.5-bin.zip`)。
2.  解压并添加到 PATH。

### 4.4 本地配置 (关键)
在项目根目录创建 `local.properties` 文件，指向 SDK 路径：
```properties
sdk.dir=/root/android-sdk
# 或者 /home/username/android-sdk
```

---

## 5. 编译与发布流程 (Build & Release Workflow)

### 5.1 编译命令
在项目根目录下执行：
```bash
# 赋予执行权限 (如果需要)
chmod +x ./gradle-8.5/bin/gradle

# 编译 Debug 包
gradle assembleDebug
```

### 5.2 产物输出 (Artifacts)
由于配置了 `splits`，编译会生成两个 APK 文件：
1.  **瘦身版 (推荐)**: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (~150MB)
2.  **全兼容版**: `app/build/outputs/apk/debug/app-universal-debug.apk` (~560MB)

### 5.3 发布到 GitHub Releases
使用 GitHub CLI (`gh`) 直接从服务器发布，利用服务器带宽。

```bash
# 登录 (仅需一次)
gh auth login

# 创建 Release 并上传双版本
gh release create v0.1 \
    app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
    app/build/outputs/apk/debug/app-universal-debug.apk \
    --title "v0.1 Alpha" \
    --notes "GeckoView 121.0 内核构建"
```

---

## 6. 开发工作流 (Development Lifecycle)

这是一个 **端到端** 的闭环开发流程：

1.  **手机端 (Coding)**:
    *   在手机上使用编辑器修改 Kotlin 代码。
    *   `git add .` -> `git commit` -> `git push` 推送到 GitHub 仓库。

2.  **服务器端 (Building)**:
    *   `git pull` 拉取最新代码。
    *   执行 `gradle assembleDebug` 进行编译。
    *   执行 `gh release create` 发布新版本。

3.  **手机端 (Testing)**:
    *   访问 GitHub Releases 页面。
    *   下载 `arm64` 版本 APK 进行覆盖安装测试。

---

## 7. 常见问题与维护 (FAQ)

### Q1: 如何更新内核版本？
修改 `app/build.gradle` 中的 `ext` 块：
```groovy
ext {
    // 访问 maven.mozilla.org 查询最新的 Omni 版本号
    geckoviewVersion = "121.0.20231211174248" 
}
```
修改后重新编译，Gradle 会自动下载新内核。

### Q2: 编译报错 `License not accepted`
运行以下命令并一路输入 `y`：
```bash
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```

### Q3: 为什么 GitHub Push 失败？
检查是否尝试直接 Push 了 APK 文件。
**严禁**将 `build/` 目录或 `.apk` 文件添加到 git 版本控制中。请检查 `.gitignore` 文件是否包含 `build/`。
APK 必须通过 Releases 功能发布。

### Q4: 如何开启 AndroidX 支持？
如果报错 `AndroidX dependencies`，检查根目录 `gradle.properties` 是否包含：
```properties
android.useAndroidX=true
```

---

## 8. 给 AI 助手的提示 (Prompt for AI)

如果你是帮助维护此项目的 AI，请注意：
1.  **不要尝试生成 gradlew 脚本**，我们使用手动下载的 Gradle 8.5。
2.  **GeckoView 版本号** 必须是 Maven 仓库中真实存在的完整时间戳版本，不能编造。
3.  **文件操作**：在修改 `build.gradle` 时，注意保留 `splits` (分包) 配置，否则 APK 体积会膨胀到 500MB+。
4.  **环境感知**：当前环境是无头 Linux 服务器，无法打开浏览器下载，请提供 `wget` 或 `curl` 命令。


```markdown
# KeroBrowser (GeckoView Edition)

> 一个基于 Mozilla GeckoView 内核的 Android 浏览器，采用 **"Mobile Coding + Server Building"** 的极客工作流构建。

---

## 🤖 Context for AI Agents (AI 助手必读)
**如果你是协助开发的 AI，请务必遵守以下规则：**

1.  **构建环境 (Build Environment)**:
    - 我们**不使用**标准的 `./gradlew` 脚本，因为服务器环境特殊。
    - **必须使用**手动指定的 Gradle 路径： `./gradle-8.5/bin/gradle`。
    - **JDK 版本**: Java 17 (Required by GeckoView).
    - **Android SDK**: API 34 (UpsideDownCake), MinSdk: 24.

2.  **核心配置 (Critical Configs)**:
    - **GeckoView 版本**: 必须使用 Maven 仓库中真实存在的时间戳版本 (例如 `121.0.20231211174248`)，**严禁**编造版本号。
    - **APK 分包 (Splits)**: `app/build.gradle` 中配置了 `splits { abi { ... } }` 以生成 Arm64 瘦身包。**修改构建脚本时，绝对不要删除或破坏 splits 代码块**，否则 APK 体积会膨胀至 500MB+。
    - **AndroidX**: 项目强制开启 AndroidX，`gradle.properties` 中必须包含 `android.useAndroidX=true`。

3.  **UI/UX 规范**:
    - 布局文件必须保持 ID 稳定：地址栏为 `@+id/address_bar`，按钮为 `@+id/go_button`。
    - 必须在 `AndroidManifest.xml` 中保留 `android:usesCleartextTraffic="true"` 以兼容 HTTP 站点。

---

## 🛠️ 技术架构 (Technical Architecture)

- **语言**: Kotlin (JVM Target 17)
- **内核**: Mozilla GeckoView (Channel: Omni)
- **构建系统**: Gradle 8.5 + Android Gradle Plugin (AGP) 8.2.0
- **目标架构**:
  - `arm64-v8a` (主推，体积 ~150MB)
  - `universal` (备用，体积 ~560MB)

## 📂 项目结构说明 (Project Structure)

```text
MyBrowser/
├── .gitignore                # 严格过滤 build/ 和 local.properties
├── build.gradle              # Root Project Config
├── gradle.properties         # Enable AndroidX
├── local.properties          # [不上传] 指向服务器 SDK 路径 (e.g., sdk.dir=/root/android-sdk)
├── README.md                 # You are here
└── app/
    ├── build.gradle          # Module Config (含 GeckoView 版本 & Splits 配置)
    └── src/
        └── main/
            ├── AndroidManifest.xml   # 权限、Activity、CleartextTraffic
            ├── java/com/kero/browser/
            │   └── MainActivity.kt   # 核心逻辑 (Session, NavigationDelegate)
            └── res/layout/
                └── activity_main.xml # 界面布局 (LinearLayout + GeckoView)
```

## 🚀 编译与发布指南 (Build & Release)

本项目运行在无头 Linux 服务器上，无 Android Studio 图形界面。

### 1. 准备环境 (First Run)
确保 `local.properties` 已创建并指向正确的 SDK 路径。

### 2. 编译指令 (Build Command)
```bash
# 清理 (可选)
./gradle-8.5/bin/gradle clean

# 编译 Debug 包 (自动生成双版本)
./gradle-8.5/bin/gradle assembleDebug
```

### 3. 发布指令 (Release via GitHub CLI)
使用 `gh` 命令行工具利用服务器带宽直接上传：

```bash
gh release create v0.X \
    app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
    app/build/outputs/apk/debug/app-universal-debug.apk \
    --title "v0.X Update" \
    --notes "更新日志..."
```

## 📝 常见问题 (Troubleshooting)

- **Q: 编译报错 "overrides nothing"**
  - **A**: 通常是 `NavigationDelegate` 等接口的函数签名不匹配。检查 GeckoView 对应版本的 API 文档，确认参数是否为 Nullable，或是否增加了新参数 (如 permissions)。

- **Q: 编译报错 "License not accepted"**
  - **A**: 运行 `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses`。

- **Q: 按钮点击无反应**
  - **A**: 检查 `activity_main.xml` 中的 ID 是否与 `MainActivity.kt` 中的 `findViewById` 一致。
```