# 缩放预览裁剪回归

## 触发与根因

双指缩小后保持按住（尤其从较大字号缩到 50% 或更小），旧实现只缩放 Canvas 矩阵：

- `DrawScope.size` 未变，内部 `clipRect` 跟着矩阵缩小，正文被限制在屏幕中间的小矩形。
- `EditorState.visibleLines` 仍是手势开始前的窗口，新露出的行没有绘制。
- 松手后的字号提交和重新布局会掩盖上述错误，因此只验证最终字号无法发现回归。

## 渲染约束

`EditorScalePreview` 是正文和光标共用的预览入口。它将逻辑绘制尺寸改为物理尺寸除以缩放比例，
同时传入只读的 `EditorRenderViewport`。焦点变换转换为临时滚动偏移：

```text
previewScroll = max(0, originalScroll + pivot - pivot / scale)
screenPosition = scale * (documentPosition - previewScroll)
```

正文、行号、折叠标记、选区、诊断、括号、空白符、inlay hints 和光标必须使用同一帧的视口。
固定行号栏在左侧缩放，不随正文横向位移。真实字号、滚动状态、软换行布局及持久化仍由原有协调器管理。
退出绘制时必须恢复 Canvas 和 DrawScope 尺寸，不能影响物理坐标下的滚动条、小地图。

绘制裁剪使用完整 Canvas 尺寸；滚动上界使用扣除 IME 遮挡的有效视口高度，并按预览比例换算。
预览到最终字号的锚点交接、精度与宿主回写约束见 [字号精度与缩放收尾](font-scale-handoff.md)。

新增绘制层时不要直接读取 `state.visibleLines` / `state.visualLineTopInViewport`；
应使用 `EditorRenderFrameContext` 的对应字段/方法，否则缩放预览会再次出现局部缺失。

## 自动验证

在 editor-kit 根目录执行：

```bash
./gradlew :core:editor-view:testDebugUnitTest --tests '*EditorScalePreviewTest' --tests '*EditorRenderViewportTest' --no-daemon --console=plain
```

测试覆盖真实渲染调用的行数与文字基线、正文/光标裁剪框、0.25/0.5/1/2/4 倍视口、
焦点保持、文档起点、固定/滚动行号、冻结的软换行布局，以及预览退出后的状态恢复。
这些是 Robolectric 渲染管线和几何测试，不替代 Android GPU 或真机多指输入验证。

## 真机验收

1. 打开有数百行和长行的 C/C++ 文件，先增大字号，再连续缩小，双指保持按住至少 3 秒。
2. 分别在文件顶部、中段（含横向滚动）、末尾操作；中心和屏幕边缘都试一次。
3. 开关软换行、固定行号；带选区、光标、诊断和折叠区域各试一次。
4. 按住期间新露出的内容应立即绘制，不能出现中间小矩形；顶部不能被拉出空白。
5. 松手后检查字号、焦点、点击定位、选区拖动、滚动条、小地图；连续缩放不应残留临时坐标。
