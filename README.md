# TinaEditor Kit

可独立构建的 Android/Jetpack Compose 代码编辑器，从 [TinaIDE](https://github.com/wuxianggujun/TinaIDE)
抽离。TinaIDE 主工程把本仓库作为子目录通过 `settings.gradle.kts` 的 `projectDir`
映射消费同一份源码，不复制实现；其他项目按下文「在其他 Android 项目中使用」接入。

## 模块

| 模块 | 职责 |
| --- | --- |
| `:core:editor-api` | 主题键、语义 token 与 Signature Help 公共模型，并向后兼容导出文件类型能力 |
| `:core:language-support` | C/C++ 与 Makefile 文件识别规则，供宿主和 Tree-sitter 复用 |
| `:core:text-engine` | Rope、行索引、编辑历史、JNI 文本扫描 |
| `:core:tree-sitter` | 语法高亮、语言注册、折叠区域核心（不内置 grammar 依赖） |
| `:core:tree-sitter-grammars` | 可选语言 grammar bindings；宿主按需显式装配 |
| `:core:editor-view` | Compose 编辑器、IME、选择、滚动、弹窗与渲染 |

TinaIDE 的 `:core:editor-lsp`、`:feature:editor`、项目路径、编译运行、插件和偏好存储
**不属于此库**。宿主通过 `EditorState` 的回调接入补全、诊断、语义 token 等服务；
`EditorRuntimeOptions` 接入字体大小持久化和调试开关；`TinaEditor(hoverContent = ...)`
可替换默认的纯文本 Hover 内容。

## 构建

要求 JDK 17、Android SDK 36、NDK 29.0.14206865 与 CMake 3.22.1；库的
最低 Android API 为 28。请设置 `ANDROID_HOME` 指向 SDK，以便复合构建中的
Tree-sitter 子项目也能定位 SDK。源码与构建脚本均为 UTF-8。
克隆独立仓库时请初始化 Tree-sitter 源码子模块：

```bash
git submodule update --init --recursive
```

```bash
./gradlew :core:text-engine:testDebugUnitTest --no-daemon --console=plain
./gradlew :core:language-support:compileDebugKotlin --no-daemon --console=plain
./gradlew :core:tree-sitter:compileDebugKotlin --no-daemon --console=plain
./gradlew :core:tree-sitter-grammars:compileDebugKotlin --no-daemon --console=plain
./gradlew :core:editor-view:compileDebugKotlin --no-daemon --console=plain
```

上述命令在本目录运行；Windows 可用 `gradlew.bat`。

## 在其他 Android 项目中使用

当前**推荐源码复合构建**：把本仓库作为 Git 子模块（或同等方式）放到消费项目中，
初始化其 Tree-sitter 子模块，然后在消费项目的 `settings.gradle.kts` 添加：

```kotlin
includeBuild("TinaEditor")
```

消费项目的 `build.gradle.kts` 添加依赖：

```kotlin
implementation("io.github.tinaide.editor:editor-view:0.1.0-SNAPSHOT")
// 需要内置语言 grammar 时再显式添加：
// implementation("io.github.tinaide.editor:tree-sitter-grammars:0.1.0-SNAPSHOT")
```

Gradle 会用 included build 中的同名模块替换该坐标。消费项目仍需配置 `google()`
与 `mavenCentral()`。可运行 `examples/consumer` 验证跨项目源码消费：

```bash
./gradlew -p examples/consumer :consumer:compileDebugKotlin --no-daemon --console=plain
```

当前不提供 Maven/AAR 发布：部分 Tree-sitter grammar（例如 Bash、CMake）的
`4.3.2` 版本不在 Maven Central，直接发布编辑器 AAR 会生成无法解析的传递依赖。
待 grammar 产物、版本与发布仓库统一后，再增加 Maven 发布任务和独立消费者测试。

最小 Compose 用法：

```kotlin
val buffer = remember { RopeTextBuffer("Hello, editor!\n") }
val editorState = remember(buffer) { EditorState(textBuffer = buffer) }
TinaEditor(
    state = editorState,
    modifier = Modifier.fillMaxSize(),
    onToggleLineComment = { editorState.toggleLineComment("//") }
)
```

`onToggleLineComment` 为可选能力。提供后，编辑器会把 `Ctrl+/` 转发给宿主；回调返回
`true` 表示文本确实发生变化，编辑器据此同步 IME 和外部编辑状态。注释符号由宿主根据当前
文件语言决定，编辑器内核不依赖宿主的语言配置；不提供回调时不会拦截该快捷键。

应用需自行提供 Activity、Compose 主题、文件读写和语法/LSP 服务。库的包名暂保留
`com.wuxianggujun.tinaide.core.*`，避免本轮搬迁同时改动 JNI 符号与现有 API；
后续如需重命名，应单独做兼容迁移。

## Tree-sitter 初始化

`TreeSitterLanguageRegistry.resolveLanguage()` 会在初始化任何语法绑定类之前加载核心
native 库；通过注册表创建高亮器、折叠 provider 的消费者不需要在 Application 中手动加载。
语言名与文件扩展名查询不会触发 native 加载；grammar 依赖仍需按需显式添加。

宿主如果需要预加载，或直接使用 `TSParser` / `TSLanguage*` 等底层绑定，统一调用：

```kotlin
import com.wuxianggujun.tinaide.core.treesitter.TreeSitterRuntime

TreeSitterRuntime.ensureInitialized()
```

初始化使用线程安全的惰性加载：并发调用会等待，成功后不重复执行，失败原样抛出且不缓存
成功状态。注册表保留带语言上下文的异常日志并返回 null；直接调用方负责记录和处理异常。
重试不意味着可以修复已经初始化失败的语法类；这类失败需要新进程 / ClassLoader。
TinaIDE 不再在 Application 中无条件预加载；直接使用 parser 的符号索引服务在自身创建时
调用同一入口，在创建任何 parser / grammar 之前完成初始化，不影响不使用语言服务的场景。

### 高亮与 LSP 分别选择

两个能力按编辑器实例分别注入，不设置相互绑定的全局开关：

| 场景 | `EditorState.highlighter` | 语言服务回调 / LSP 会话 |
| --- | --- | --- |
| 纯文本 | 保持默认 `null` | 保持默认 `null`，宿主不创建 LSP 会话 |
| 只要语法高亮 | 按需创建并注入高亮器 | 保持默认 `null`，不创建 LSP 会话 |
| 完整代码编辑 | 按需注入高亮器 | 宿主按需绑定补全、Hover、诊断等服务 |
| 只用外部语言服务 | 保持 `null` | 宿主自行提供回调 / 语义 token |

kit 不内置 LSP 客户端；设置文件路径或创建 `EditorState` / `TinaEditor` 不会自动启动 LSP
或创建 Tree-sitter 高亮器。已有的 `highlighter` 与 `onRequestCompletion`、`onRequestHover`
等可选接口就是各自的控制入口，无需再增加重复的 `lspEnabled` / `highlightEnabled` 状态。
纯文本场景也不要创建 Tree-sitter 折叠 provider 或符号索引服务，否则它们仍然需要核心库。
切换为纯文本时，宿主需取消已有请求、解绑并按所有权释放服务；只把回调设为 null 不会替宿主
关闭已经创建的 LSP 会话。已被其他编辑器使用的共享 native 库不会因单个编辑器关闭高亮而卸载。

`TreeSitterNativeInitializationTest` 与 `TreeSitterLanguageRegistryInitializationTest`
覆盖不提前加载、成功去重、并发等待、失败重试和注册表初始化顺序。这些 JVM 测试不验证
设备 ABI、native 打包或实际 JNI 注册；实际高亮仍需冷启动设备回归。
`EditorOptionalLanguageServicesTest` 另行覆盖纯文本默认值、高亮不启动语言服务、实例之间
互不影响，以及外部服务不强制创建高亮器。

## 许可证

本库源代码按 GPL-3.0-or-later 提供，见 [LICENSE](LICENSE)。Tree-sitter Android
绑定和各语言 grammar 来自独立子模块，保留其上游许可；详见 [NOTICE.md](NOTICE.md)。
