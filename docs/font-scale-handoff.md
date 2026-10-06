# 字号精度与缩放收尾

## 已消除的回跳来源

- 宿主把连续字号转为整数保存，然后经设置流回写，导致已按小数字号对齐的滚动位置与新行高不一致。
- 手势逐帧忽略小于 `0.002` 的比例变化，松手又忽略小于 `0.1sp` 的最终变化，使慢速缩放丢失输入或在松手时恢复旧字号。
- 宿主回调早于 metrics、滚动和软换行提交，回调可观察到一半更新的状态。
- 手势收尾依赖 Compose 重组观察 `isTransformInProgress`，短手势的开始/结束可能被合并而漏掉提交。
- 软换行锚点在修改 Paint、滚动和字体度量后才捕获，混用了新坐标与旧换行分段。
- 缩放预览以完整 Canvas 高度计算滚动边界，但最终布局使用扣除 IME 遮挡的有效视口，文档末尾会再次夹紧。

## 约束与实现

`EditorFontSize` 位于 `editor-api`，统一默认值与 `8..48sp` 范围。存储与回调保留 Float 精度；
设置 UI 可以限制手动选择的步长，但不得量化手势产生的值。TinaIDE 继续使用原 String 偏好键，
旧整数配置无需迁移，编辑器仍不依赖宿主配置模块。非有限的手势输入被忽略，损坏的持久化值回退默认值。

手势期间继续复用旧 Paint、布局缓存与冻结的软换行；预览比例使用实际 `sp -> px` 转换。
通过 `EditorTransformableState` 复用 Compose 的手势识别与互斥机制，在实际 transform 的 `finally` 中收尾，
不再用 `LaunchedEffect(isTransformInProgress)` 猜测生命周期。结束时依次执行：

1. 从最后预览视口捕获文档锚点，仍使用旧字号的 prefix layout 和换行分段。
2. 测量目标字号，按新布局计算滚动；软换行解除冻结后立即对齐同一字符。
3. 在一个 Compose snapshot 中提交字号、完整视口和预览退出状态；可观察视口按批次发布。
4. 完成后通知宿主保存字号。设置流回传同一 Float，不再触发第二次字号变化。

不再把缩放锚点留到下一次 `draw/updateMetrics` 才处理。没有增加定时器、补偿动画或宿主反向依赖。
回到初始字号、关闭文档或宿主回调抛出异常时，也会释放预览与换行冻结状态；回调异常继续向上传播。

## 回归入口

在 kit 根目录分别准备编译、运行测试。以下为前台定向命令；后台执行测试时必须设置 60 秒上限。
运行前确认其他会话的构建已结束，前台验证也应设置有限的超时时间：

```bash
./gradlew :core:editor-view:compileDebugKotlin :core:editor-view:compileDebugUnitTestKotlin --no-daemon --console=plain
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorScaleTransformCoordinatorTest' --tests '*EditorRuntimeOptionsTest' --no-daemon --console=plain
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorScaleGestureLifecycleTest' --tests '*EditorRenderViewportTest' --tests '*EditorScalePreviewTest' --tests '*EditorPaintApplyMemoTest' --no-daemon --console=plain
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorStateWordWrapTest' --tests '*EditorStateEventTest' --no-daemon --console=plain
```

宿主定向测试：

```bash
./gradlew :core:config:testDebugUnitTest --tests '*EditorFontSizePreferencesTest' --no-daemon --console=plain
./gradlew :app:testArm64DebugUnitTest --tests '*EditorFontSizeHostIntegrationTest' --no-daemon --console=plain
```

`EditorScaleTransformCoordinatorTest` 使用确定性的 Paint 度量验证真实协调器、视口、prefix cache 和换行映射，
不是 GPU 字体栅格化测试。`EditorScaleGestureLifecycleTest` 验证两次重组间完成的 transform、取消路径，
并使用 Native Graphics 的字体度量比较最后预览与提交后的可见行位置，以及下一次正常度量是否再次改变视口。
宿主用例通过 Compose 双指输入接入真实偏好回调和 StateFlow 回传，
并比较提交时与后续帧的字号、可观察视口。Robolectric Native Graphics 仍不能替代设备 HWUI 验证。

设备回归需覆盖：关闭/开启软换行、固定/滚动行号、顶部/中段/底部、键盘遮挡、慢速小幅捏合、
快速连续捏合、`8sp/48sp` 边界及切换文件。字号改变导致的正常软换行重排与锚点回跳应分开判断。

## 验证记录（2026-10-06）

- kit 定向单测：68 项通过，0 失败、0 跳过；包含缩放协调器、手势生命周期、Native Graphics、
  视口、软换行、关闭文档以及 popup/overlay 回归。
- 宿主字号偏好：3 项通过，覆盖浮点往返、旧整数配置和非法值处理。
- kit 主源码/单测源码、宿主 `:app:compileArm64DebugUnitTestKotlin` 编译通过。
- 宿主真实双指输入集成和 Android 设备回归仍待验收；以上数字不包含这两项，
  不把 JVM Native Graphics 结果等同于设备 HWUI 验收。

源码与文档使用 UTF-8 无 BOM，无新增用户可见文案或第三方依赖。
