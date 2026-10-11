# 编辑器关闭后的残留绘制帧与快照观察

## 触发条件与复现

宿主关闭 `RopeTextBuffer` 后，仍持有旧 `EditorState` 的 Canvas 节点可能再次执行绘制。
不能假定关闭资源与 UI 节点停止绘制同步完成。此前内容层在计算行号区域时会触发：

```text
EditorCanvasLayer → EditorRenderer.contentStartX → hitZones
→ RopeTextBuffer.lineCount → IllegalStateException: RopeTextBuffer is already closed
```

`EditorCanvasClosedBufferTest` 使用真实 `EditorCanvasLayer`、200 行文档、小地图及焦点光标，
先确认内容已绘制，再关闭 buffer、使内容和光标层失效，并用 Robolectric Native Graphics
显式调用宿主 View 的 `draw`。普通帧和 0.5 倍缩放预览两条用例在修复前均复现上述异常。
这与仅断言关闭后读取会抛异常的 buffer 单元测试不同，实际覆盖了 Compose 绘制入口。

## 修复边界

- `TextBuffer.isClosed` 可在关闭后安全查询。默认值对应接口的 no-op `close()`；释放资源的实现必须覆盖。
- `RopeTextBuffer` 复用原有读写锁保护关闭状态；关闭后访问文本仍然 fail-fast，不返回伪造的空文档。
- `TextBufferClosedException` 继承 `IllegalStateException`，用于区分关闭与其他状态错误。
- 两个 Canvas 入口统一使用 `drawEditorFrame`，覆盖 metrics、正文、缩放预览、滚动条、小地图、光标。
- 已关闭的帧只画背景。`isClosed` 是快照，不是跨线程租约：若关闭发生在检查之后，
  文本访问仍由 buffer 的锁保护，绘制边界只恢复明确的关闭异常，并清除半帧内容。
- 其他 `IllegalStateException`、或当前 buffer 仍打开时抛出的关闭异常会继续上抛，不掩盖编程错误。

不为整帧持有文本锁，不重复添加各个 renderer 的守卫，不增加宿主依赖、第三方库或 UI 文案。
代码与文档均为 UTF-8（无 BOM）。

## 验证

在 editor-kit 根目录运行：

```bash
./gradlew :core:text-engine:testDebugUnitTest --tests '*RopeTextBufferTest' --tests '*RopeTextBufferClosedBufferTest' --no-daemon --console=plain
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorCanvasClosedBufferTest' --tests '*EditorDrawFrameTest' --tests '*EditorScalePreviewTest' --tests '*EditorRenderViewportTest' --tests '*EditorMinimapRendererTest' --tests '*EditorRendererPerformanceSnapshotTest' --no-daemon --console=plain
```

`EditorDrawFrameTest` 额外使用另一个线程在帧中关闭 buffer，通过有界 Future 等待确定性地安排
检查/读取时序，不依赖 sleep；逐像素验证背景覆盖以及裁剪恢复，并验证无关异常不会被吞掉。

### 本次结果（2026-10-05）

- 修复前：两个真实 Canvas 回归用例均因已关闭的 RopeTextBuffer 读取失败。
- 修复后：上述用例及 `EditorRuntimeOptionsTest`、`EditorRuntimeEffectsTest` 共 65 项通过，
  0 失败、0 跳过（text-engine 30 项，editor-view 35 项）。
- 先完成两模块测试源码编译，再以前台、有界命令执行测试，没有并行启动 Gradle。
- 宿主本地诊断日志：`.tmp/editor-closed-frame-red-tests.log`、
  `.tmp/editor-closed-frame-green-compile.log`、`.tmp/editor-closed-frame-green-tests.log`。

## 宿主责任与真机验收

此修复针对残留绘制帧，不意味着关闭后的 `EditorState` 可以继续输入或请求 LSP。
宿主仍应先停止输入、后台请求和相关订阅，再释放会话资源并移除 UI 节点。
自定义有资源的 `TextBuffer` 应实现关闭状态，并用明确的关闭异常拒绝资源访问。

真机分别在普通编辑、持续缩放、光标闪烁和小地图开启时快速关闭标签或切换工程，
检查无主线程绘制崩溃，并重新打开文档验证正文、光标和高亮正常。
JVM / Robolectric 测试不替代 Android GPU、IME 和完整宿主生命周期的设备测试。

## 保存/关闭时的快照重算（2026-10-12）

TinaIDE `0.18.35` 的崩溃栈经对应 mapping 还原后，实际路径是：

```text
SnapshotStateObserver.recordInvalidation → DerivedSnapshotState.currentRecord
→ EditorState.visibleLines → EditorVisualLineMapper.visualLineMap
→ RopeTextBuffer.lineCount → TextBufferClosedException
```

该计算发生在全局 Snapshot 应用通知中，早于残留观察者移除，不经过 Canvas 绘制边界。
仅保护渲染无法覆盖它。`EditorClosedSnapshotTest` 注册真实 `SnapshotStateObserver`，
先使可见行依赖失效，再关闭 buffer，最后发送应用通知；修复前得到上述相同调用链。

- `visibleLines` 的派生计算和 `visibleDocumentLines` 查询均在关闭后返回空范围，表示旧视口已退出。
- getter 也检查关闭状态，避免没有新 Snapshot 变更时重用已缓存的可见范围。
- 检查与映射读取之间发生关闭时，仅恢复当前 buffer 已关闭的 `TextBufferClosedException`。
- 不吞无关映射错误，也不改变 `TextBuffer` 关闭后直接访问必须抛异常的约定。
- 宿主仍需为挂载中的页面保留运行时，等最后一个页面的 effects 退出后再释放资源。

```bash
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorClosedSnapshotTest' --tests '*EditorDrawFrameTest' --tests '*EditorCanvasClosedBufferTest' --tests '*EditorRuntimeEffectsTest' --tests '*EditorScaleGestureLifecycleTest' --tests '*EditorScaleTransformCoordinatorTest' --no-daemon --console=plain
./gradlew :core:editor-view:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wuxianggujun.tinaide.core.editorview.EditorLifecycleInstrumentationTest --no-daemon --console=plain
```

本轮 JVM / Robolectric 关闭回归 13 项、缩放回归 13 项，共 26 项通过，0 失败、0 跳过；
其中 5 项新测试覆盖全局失效、缓存、跨线程关闭、无关错误传播和错误关闭异常传播。

Android 14 / API 34 模拟器上的 `EditorLifecycleInstrumentationTest` 两项测试连续运行
3 轮均通过，覆盖真实全局快照失效、双指字号单次提交、焦点下文档锚点、松手后多帧稳定及随后单指滚动。
未在报告中的 Redmi 真机回测。

真实触摸还验证了 modifier 的消费顺序：Main pass 从内向外传递，transformable 必须位于
轴向 scrollable 的内侧，并用 `canPan = { false }` 把单指拖动留给 scroller。
否则 scroller 可能在 `enabled` 重组前先消费缩放移动，导致字号回调根本没有提交。
测试的单指滑动从正文区域开始，不从底边的横向滚动条热区开始；缩放结束后推进时钟，
等待既有的 100 ms 多指保护窗口结束，再验证新手势。
