# 独立编辑器功能与宿主边界

## 审计核对

| 功能 | 实现与归属 |
| --- | --- |
| 自适应行号 | 已有 `LineNumberRenderer.calculateWidth`，按最大行号位数测量；无需另建实现。 |
| 长按/右键菜单 | 已有 selection context menu、键盘菜单、LSP 导航回调；本次增加查找和多光标操作。 |
| 自动补括号/引号 | 已有 `EditorSmartReplacement`，IME、硬键盘和宿主符号栏共用输入入口。 |
| Snippets | 已有 parser/session、占位符同步、Tab/Shift+Tab 和撤销；不另建片段引擎。 |
| Git gutter | 库接收 `EditorState.gitLineChanges` 并绘制；TinaIDE 的 GitService 异步计算 HEAD 与内存文本的 diff，校验文档版本后注入。仓库 IO 不下沉到编辑器。 |
| Inline 查找/替换 | 本次加入库内 controller、异步刷新、查找栏、匹配高亮和单事务替换。 |
| 手动多光标/列选择 | 本次补充位置 API、Alt 鼠标手势和显式触屏模式；复用现有多光标编辑计划、选区和撤销事务。 |
| 外部绘制 | 本次加入公共只读几何接口与前景/背景扩展，不公开内部可变缓存。 |

## 查找/替换

`EditorState.find` 是每份文档自己的 `EditorFindController`：

```kotlin
state.find.show()                    // Ctrl+F 或长按菜单也可打开
state.find.show(replace = true)      // Ctrl+H
state.find.search("name", EditorFindOptions(caseSensitive = true, wholeWord = true))
state.find.next()                    // F3；查找栏 Enter
state.find.previous()                // Shift+F3；查找栏 Shift+Enter
state.find.replacement = "newName"
state.find.replaceCurrent()
state.find.replaceAll()
state.find.dismiss()                 // Esc
```

- UI 查询输入防抖后在 Default dispatcher 扫描；切换查询、文档变化、退出 composition 会取消旧任务。发布结果前核对查询、选项和文档版本。
- 文档编辑后旧匹配立即失效；重新扫描不把正在输入的光标拖回首个匹配。
- 字面量与正则共用同一匹配/替换引擎，不做 lowercase 整文转换，避免 Unicode 大小写转换导致偏移漂移。
- 正则支持捕获组、命名组与零宽匹配；非法表达式或替换组返回明确错误，不部分修改文档。
- 全部替换从后往前应用实际发生变化的范围，一次事务、一次撤销，并映射已有选区；不整文覆写。
- `matches` 的范围是 UTF-16 偏移、结束位置不包含；字面量匹配不重叠。替换返回实际改变的匹配数。
- `search` / `refresh` 是同步编程入口；文本框的输入刷新走异步路径。API 修改应在编辑器/UI 线程进行。
- `EditorState.replaceAll(findText, replaceText, ...)` 作为无查找 UI 的直接编辑操作复用同一引擎，不保留另一套匹配算法。

## 多光标与矩形选区

- `Alt+点击`：添加或移除光标；最后一个光标不能移除。
- `Alt+鼠标拖动`：按像素测量每个可见视觉行，正确处理 Tab、软换行与内联提示；折叠隐藏行不参与鼠标矩形。
- `Ctrl+点击`：保留原有跳转定义，不与多光标手势抢占。
- `Ctrl+Alt+↑/↓`：现有的上下添加光标；`Ctrl+D`：现有的下一处匹配多选。
- 长按/右键“更多”：添加上/下一行光标、切换点按多光标模式、清除额外光标。触屏模式开启时有明确提示；普通行号点击仍保留宿主断点动作。
- `Esc` 清理额外光标并退出触屏模式（补全、Snippet、签名或查找可先消费 Esc）。

```kotlin
state.addCursorAt(line = 10, column = 3)
state.removeCursorAt(line = 10, column = 3)
state.toggleCursorAt(offset = 20)
state.clearSecondaryCursors()
state.selectRectangle(Position(1, 2), Position(4, 8))
```

位置 API 对越界位置、短行与 UTF-16 代理对边界进行限制；`selectRectangle` 的 column 是逻辑 UTF-16 列，鼠标矩形使用实际像素列，两者不混淆。

## 自定义渲染

通过 `state.renderExtensions` 注入 `EditorRenderExtension`，选择 `Background`（正文前）或 `Foreground`（正文后、选区手柄前）。

`EditorCustomRenderer.draw(scope, context)` 可使用 `context.visibleRows`、`lineText`、`rangeRectangles` 和配色。坐标与传入的 DrawScope 一致，已经应用横向滚动、裁剪和临时缩放。范围几何包含软换行分段、内联提示宽度、零宽匹配及换行符标记。

扩展只绘制，不在 draw 内编辑文档，不保留 context 到下一帧。每个扩展的 Canvas 变换与后续层隔离。`examples/consumer` 给出不依赖宿主的外部下划线渲染示例。

## 宿主清理

TinaIDE 的代码编辑器不再创建 `CodeSearchEngine` / `TinaTextContentProvider`，不向查看器搜索管理器注册重复的代码搜索状态；查找和替换命令直接打开 kit 查找栏。旧替换 Dialog、Dialog 状态、replaceAll 回调与结果包装已删除。

JSON/Hex 查看器的搜索、项目搜索、GitService、LSP 和文档运行时仍有独立职责，保留而非误删。

## 验证与回归

本次按要求进行 Kotlin 静态编译（含新增测试源码），不将编译测试源码表述为测试执行通过。不打包 APK，不做 Release。

已通过独立 kit 编译与外部 consumer 编译：

```bash
cd editor-kit
./gradlew :core:editor-view:compileDebugKotlin :core:editor-view:compileDebugUnitTestKotlin --no-daemon --console=plain
./gradlew -p examples/consumer :consumer:compileDebugKotlin --no-daemon --console=plain
```

重点测试源码：`EditorFindEngineTest`、`EditorFindControllerTest`、`EditorManualMultiCursorTest`、`EditorRenderExtensionTest`、菜单与宿主入口测试。

手工回归：

1. 开启软换行和内联提示，按住双指缩小，检查匹配高亮与正文保持对齐。
2. 在文件底部输入，确认顶部内联提示稳定、查找计数更新但光标不跳走。
3. 查找大小写、全词、跨行正则、`^|$`，尝试无效正则及 `$9`，确认错误可见且文本未部分修改。
4. 全部替换后一次撤销；检查反向选区、多光标与分屏标签隔离。
5. 鼠标 Alt 点击/拖动，Ctrl 点击定义，触屏菜单开启/退出点按模式，检查行号断点不受普通操作影响。
6. 核对 JSON/Hex 原有搜索和 Git gutter。

新增源码、文档为 UTF-8 无 BOM；新增用户文案同时提供默认中文和 `values-en` 英文资源。未添加依赖、兼容 shim 或宿主反向依赖。
