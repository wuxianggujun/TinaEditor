# 编辑器输入与渲染边界修复

## 范围

仅修改 `editor-view`，不修改宿主、构建依赖、字体缩放策略或内联提示更新策略。
源码和本文均使用 UTF-8（无 BOM），没有新增用户可见文案。

## 1. IME 周围删除与普通退格分离

- `deleteSurroundingText` / `deleteSurroundingTextInCodePoints` 删除选区两侧，保留选中文字。
- `abcdef` 选中 `cd` 后调用 `(1, 1)`，结果为 `acdf`，选区更新为 `[1, 3)`。
- `(0, 0)` 不改变文本、选区或撤销历史；反向选区保持方向。
- 与 AOSP `BaseInputConnection` 一致，组合输入期间保护选区与 composing region 的并集。
- 多光标删除范围先排除所有受保护选区，再复用现有 planner 合并重叠编辑。
- 复用既有选区映射和 edit transaction，将两侧/多个光标的删除作为一次撤销。
- 普通单光标删除仍使用原来的 snippet-aware replacement 路径；硬件退格仍删除选中文字。

## 2. 纠错通知不再插入正文

`commitCorrection` 只确认通知，不重新提交 `newText`，不改选区、不结束组合输入、不新增撤销记录。
例如先 `commitText("the", 1)` 再通知 `CorrectionInfo(0, "teh", "the")`，正文仍为 `the`。

## 3. 缩放预览的文档末尾限制

正常滚动与缩放预览共用 `EditorState` 的垂直滚动上界计算，预览传入逆缩放后的视口高度，
保持半屏底部留白策略，不写入实际滚动、字体或视口状态。可见首行也限制在视觉行范围内。

复现场景：100 行、行高 20、视口高 600，在底部以 Y=500 放大 3 倍。
修复前预览滚动为 2033.33，可见行 `101..99` 为空；修复后限制到 1900，可见行 `95..99`。
补充短文档、空文档、放大及缩小的边界用例。

## 4. 软换行后的括号高亮

每个括号使用 `visualLineForPosition(line, column)` 定位视觉行，横坐标扣除该换行段的
`segmentStartAdvance`。同一文档行中的两个括号可以落在不同视觉行；可见行范围外的括号不绘制。
继续复用行文本读取和 prefix layout，不重复测量整行。

## 回归用例与静态编译

新增用例在 `EditorInputConnectionEditTest`、`EditorRenderViewportTest`、
`MatchingBracketHighlightRendererTest`，同时修正了原先错误的“IME 优先删除选区”断言。

本次按请求只做静态编译，不运行单元测试、仪器测试或 App 构建。在 kit 根目录执行：

```bash
./gradlew :core:editor-view:compileDebugKotlin :core:editor-view:compileDebugUnitTestKotlin --no-daemon --console=plain
```

编译测试源码不等于运行测试。后续设备回归应检查：中文组合输入、IME 删除选区周围、
撤销/重做、多光标删除、文档顶部和底部持续捏合、软换行续行的括号高亮。

### 本次验证结果（2026-10-05）

- 等待其他会话构建退出后执行，未停止其他会话的进程。
- 首次命令已完成主源码编译，测试源码编译阶段被工具 240 秒上限中断。
- 重新执行上述静态编译命令：`BUILD SUCCESSFUL in 1m 16s`，退出码 0。
- 主源码编译任务复用已完成的输出，测试源码编译完成；没有执行测试任务或 App 构建任务。
- 编译日志保存在宿主仓库 `.tmp/editor-core-compile-retry.log`。
- `git diff --check` 通过，改动文件均按 UTF-8 无 BOM 校验。
