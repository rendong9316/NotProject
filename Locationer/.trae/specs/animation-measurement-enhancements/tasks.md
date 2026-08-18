# 实施任务清单（tasks.md）

> 关联规格：`spec.md`
> 任务状态：`pending / in_progress / blocked / completed / cancelled`

---

## 任务 1：全局动画补全（特性 1：A1-A7 共 7 处动画）

**优先级**：high  
**关联验收项**：AC-A1, AC-A2, AC-A3, AC-A4, AC-A5, AC-A6, QR-Ani-1, QR-Ani-2  
**前置依赖**：无  
**预计修改文件**：`MainActivity.kt`, `MapScreen.kt`, `MyScreen.kt`, `ToolsScreen.kt`, `FavoritesScreen.kt`（若需）

### 分解子步骤
1.1 **MainActivity.kt 三 Tab 切换动画（A5）**：  
  - 将 `contentAlignment { Tab ... }` 下的三屏切换逻辑，从 `alpha(0.0f/1.0f) + zIndex(0/1)` 改为 `AnimatedContent(targetState = currentTab)` + `slideIntoContainer(方向)` + `fadeThrough()`
  - 方向映射：按 `currentTab.ordinal - targetTab.ordinal` 的正负决定 `SlideDirection.Left / Right`（序数：0 地图, 1 工具, 2 我的）
  - 测试导航事件（`viewModel.navigationEvent`）触发切换也走同一动画分支

1.2 **MapScreen UnifiedPanel 展开动画（A1）**：  
  - `if (expanded) { Column(...) }` → 改为 `AnimatedVisibility(expanded, enter=expandVertically(tween(220, FastOutSlowInEasing))+fadeIn(), exit=shrinkVertically()+fadeOut()) { Column(...) }`
  - 外层 Card 加上 `Modifier.animateContentSize()` 让折叠栏高度自然过渡
  - 验证测量模式驱动的自动收起也走动画（`LaunchedEffect(measurementPickMode)` 触发）

1.3 **MyScreen TabRow 指示器动画（A2+A6）**：  
  - `TabRow(indicator = { tabPositions -> TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage])) })` 替换现有无动画 indicator（如果之前是 `TabRowDefaults.Indicator` 默认实现）
  - 验证点击 Tab 与手势滑动 HorizontalPager 两种方式都能滑动指示器
  - 同时应用 `Modifier.animateContentSize` 到收藏夹 Tab 的展开条目容器（A4 一部分）

1.4 **ToolsScreen 测量结果滚动 + 卡片展开动画（A3+A4）**：  
  - 找到现有 `scrollState.scrollToItem(measCardIndex)` 调用，改为 `scope.launch { scrollState.animateScrollToItem(measCardIndex) }`
  - 测量 Card 的内容 `Column` 包 `animateContentSize()`
  - 旗标列表每条展开块：`if (flag.isExpanded) { opsRow }` → `AnimatedVisibility(flag.isExpanded, enter=expandVertically(180)+fadeIn()) { opsRow }`

1.5 **收藏夹条目展开动画（A4 补全）**：  
  - `FavoritesScreen.kt`（或 `MyScreen.kt` 中收藏夹 Tab 的条目组件）每条的展开操作区同样加 `AnimatedVisibility + animateContentSize`

1.6 **镜头跳转缓动封装（A6）**：  
  - 统一定义 `object CameraAnim { const val JUMP_MS = 400; const val FIRST_LOCATE_MS = 600 }`
  - `firstLocateDone = true` 分支的 `animateCamera(zoom 17f)` → `animateCamera(CU, FIRST_LOCATE_MS, null)`
  - JumpTarget 变化的 `animateCamera` → `animateCamera(CU, JUMP_MS, null)`
  - 每次调用 animateCamera 之前先 `aMap.stopAnimation()` 取消可能正在运行的旧动画（避免叠加）
  - `addMarker` 的蓝点当前位置 `rotateAngle` 动画：目前直接赋值，若有跳变改用 `ValueAnimator` 插值（可选增强，不强求）

### 测试要求（Test Requirements）
- **TR-1.1** rule：打开 App 连点折叠面板按钮 5 次，无闪退且每次有动画  
- **TR-1.2** rule：三 Tab 切换 地图→工具→我的 共 6 次，肉眼可见滑动过渡  
- **TR-1.3** rule：工具页放 5 点测面积 → 开始计算 → 返回工具页结果区出现时有滚动动画  
- **TR-1.4** rule：旗标列表点展开/收起 3 条，有展开动画  
- **TR-1.5** rule：夜间模式切换动画无白屏闪烁  
- **TR-1.6** rubric (0-3, pass≥2)：动画流畅度评估，Profiler 平均帧时间 < 22ms，无 Jank  

### 状态
- Status: pending
- Completion Evidence: 

---

## 任务 2：测量记录保存 + 历史视图（特性 2）

**优先级**：high  
**关联验收项**：AC-MH1, AC-MH2, AC-MH3, AC-MH4, QR-MH-1  
**前置依赖**：无（与任务 1 并行开发，冲突可后合并）  
**预计修改文件**：`MapViewModel.kt`（新增历史模型和存取），`ToolsScreen.kt`（UI + 对话）

### 分解子步骤
2.1 **MapViewModel.kt 新增 MeasurementRecord + 持久化封装**：  
  - 定义 `data class MeasurementRecord(id, name, mode, totalDist, totalArea, waypoints, segments, savedAt)`
  - 现有 `SavedMeasurement` 与 MeasurementRecord 共享嵌套结构（waypoints/segments 复用 MeasurementPoint/Segment），无需重复类
  - 新增：
    ```
    private val _measurementHistory = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    val measurementHistory: StateFlow<...> = _measurementHistory.asStateFlow()
    private fun loadHistory(): List<MeasurementRecord>  // 从 SP key="measurement_history" 读 JSONArray
    private fun saveHistory(list)                        // 序列化写回 SP
    fun saveCurrentMeasurementAs(name: String): Boolean  // 从当前 measurementState/mode/totalDist 等构造 Record 并保存；返回 false 若 state != COMPLETED
    fun renameHistory(id: Long, newName: String)
    fun deleteHistory(id: Long)
    fun clearAllHistory()
    fun loadHistoryRecord(id: Long): MeasurementRecord?  // 根据 id 取出，并将 waypoints/segments/totals/mode/state 全部 restore 到 _measurementXxx 字段，state=COMPLETED 并调用 requestSwitchToMap()
    ```
  - 历史上限判断：`saveCurrentMeasurementAs` 中 list.size >= 500 直接返回 false 并发送 `UiMessage`；size == 100 时保存同时发一条建议清理 `UiMessage`（一次保存最多发 1 次"已满"，不重复）
  - init 块中调用 `loadHistory()` 初始化

2.2 **ToolsScreen 工具页 UI：测量卡片改造**：  
  - 测量卡片标题栏（目前是"📏 测距 / 测面积 🚮清除"）：模式切换和清除按钮保留，**右侧新增两个按钮** 📋历史 / 💾保存（💾仅在 state==COMPLETED 时 enabled）
  - 点击 💾 保存：弹出 `AlertDialog`（用现有 FText 和 Material3）
    - 标题：`FText("保存测量记录", 16, Bold)`
    - 摘要：一行 `FText(测距，3 测点，总长 42.18m，14 号)`
    - OutlinedTextField 默认名 `String.format("%s_%tmm%td_%tH%tM", modeName, now, now, now, now)`（测距_0817_2235）
    - 取消 / 保存两个 TextButton
    - 保存成功 Toast "已保存为「xxx」"
  - 点击 📋 历史：卡片内部状态 `histView = true`，整个卡片主体从当前测量 UI 切换为历史列表（注意：模式切换 + 开始/撤销/清除组是"测量视图"顶部操作栏，始终保留不换；中间结果区 ↔ 历史列表切换）
    - 历史视图顶部：返回 ← 按钮（切回测量视图）+ "🗑 清空历史"按钮（有确认弹窗）
    - 历史条目 LazyColumn，每条显示：图标📏/📐 + 名称 + 摘要（总长/总面积 + N 测点）+ 保存时间 `yyyy-MM-dd HH:mm`，右侧 3 个 IconButton：▶️载入 / ✏️重命名 / 🗑️删除
    - ▶️载入：调用 `viewModel.loadHistoryRecord(id)` → 自动切地图并保留状态为 COMPLETED，用户点击"撤销"即可开始继续加点或重新测量
    - ✏️重命名：另一个 `AlertDialog`，输入框预填原名
    - 🗑️删除：简单二次确认（避免与批量删除的复杂 confirm 重复）

2.3 **SP 迁移测试**：构造有 `"measurement_history"` key 的旧 JSON（含 3 条记录）验证 loadHistory 解析成功；无 key 时返回 emptyList()

### 测试要求（Test Requirements）
- **TR-2.1** rule：测一条测距 3 点 → 完成 → 点击保存 → 自定义名 → 保存成功 Toast → 📋历史能看到 1 条  
- **TR-2.2** rule：测 5 条保存 5 条 → 冷启动 App → 历史仍 5 条 → 随机 1 条载入 → 地图上有对应 N 个测点 + 折线 + 结果正确  
- **TR-2.3** rule：`measurementState = PLACING` 时「💾保存」按钮 disabled 不可点击  
- **TR-2.4** rule：SP 塞 500 条记录 → 保存第 501 条 Toast "历史已满" 且没新增  
- **TR-2.5** rule：重命名 1 条、删除 1 条、清空 → 历史数量正确  
- **TR-2.6** rubric (0-3, pass≥2)：500 条滑动流畅度评估（LazyColumn 默认 LazyVerticalStaggeredGrid 性能足够，白块 ≤ 1 个算 3 分）

### 状态
- Status: pending
- Completion Evidence: 

---

## 任务 3：地图页距离气泡（特性 3）

**优先级**：high  
**关联验收项**：AC-DB1, AC-DB2, AC-DB3, QR-DB-1  
**前置依赖**：无（可并行），但最好在任务 4 Marker 重建全量的逻辑上一起改，避免重复  
**预计修改文件**：`MapScreen.kt`（createDistanceBubbleBitmap + 气泡管理 LaunchedEffect）

### 分解子步骤
3.1 **新增 createDistanceBubbleBitmap 工具函数**：
  ```kotlin
  private fun createDistanceBubbleBitmap(
      text: String,
      textSizeSp: Float = 24f,
      textColor: Int = Color.BLACK,   // 动态根据 isSystemInDarkTheme() 调
      bgColor: Int = Color.WHITE,
      cornerRadiusPx: Float = 16f,
  ): Bitmap {
      // TextPaint 测量文字宽高 → 构造 width=textWidth+48, height=textHeight+24 的 ARGB_8888 Bitmap
      // Canvas.drawRoundRect 画背景 → drawText 居中写字
      // 返回 Bitmap（不回收，BitmapDescriptor 由调用方管理）
  }
  ```
  - 注意：**文字 vs 背景对比度**：夜间模式背景 `#CC282828` 文字 `#FFFFFF`；日间背景 `#F5F5F5F5` 文字 `#FF111111`
  - 边距：左右各 24px，上下各 12px（保证气泡紧凑）

3.2 **气泡 Marker 管理接入**：
  - 在 MapScreen 的 waypoint 相关 remember 块新增 `var segmentBubbles by remember { mutableStateOf<List<Marker>>(emptyList()) }`
  - 在**现有 waypoint 渲染 LaunchedEffect**（同一块内，避免两次遍历）中加入气泡逻辑：
    1. 先清旧 `segmentBubbles.forEach { it.remove() }; segmentBubbles = emptyList()` （与现有 oldMarkers/oldPolyline 的清除保持一致节奏）
    2. `waypoints.size >= 2` 时构建段列表
       ```
       val segs = buildList {
           for (i in 0 until waypoints.size - 1) add(i to waypoints[i] to waypoints[i+1])
           if (AREA && size >= 3) add(size-1 to waypoints.last() to waypoints.first())
       }
       ```
    3. 每段计算中点经纬度 `midLat = (a.lat + b.lat)/2, midLon = (a.lon + b.lon)/2`；计算距离文本 `formatDist(a.gcj.distanceTo(b.gcj))`
    4. `addMarker(MarkerOptions().position(LatLng(midLat, midLon)).icon(fromBitmap(createBubble(text, theme))).anchor(0.5f,0.5f)).also { it.setClickable(false); it.zIndex = 1000f /* 底层，低于旗标 */ }`
    5. 新 Marker 收集到 `segmentBubbles`

3.3 **冲突处理**：`setClickable(false)` + `zIndex = 1000f`（旗标 zIndex 不设默认 0，浮层 FAB 不属高德图层层级）。若气泡仍挡住旗标点击，可将气泡 zIndex 设为负值（如果高德 SDK 支持负 zIndex 且正常渲染）。

3.4 **视觉评估**：在 `北京故宫` 范围 + 城市街区缩放 17 级、地形缩放 12 级各拍 1 张白天/黑夜截图，评估可读性和遮挡。

### 测试要求（Test Requirements）
- **TR-3.1** rule：测距 2 点 → 1 气泡；测距 5 点 → 4 气泡；清除 → 0 气泡  
- **TR-3.2** rule：面积 4 点 → 4 气泡（含闭合段）；撤销 1 点 → 3 气泡  
- **TR-3.3** rule：点击气泡所在位置不触发 Marker click（setClickable=false），仍可点击底下的旗标  
- **TR-3.4** rule：夜间模式气泡白底深色字改为深底浅字  
- **TR-3.5** rubric (0-3, pass≥2)：截图评估清晰度+遮挡程度  

### 状态
- Status: pending
- Completion Evidence: 

---

## 任务 4：工具页批量加入收藏夹（特性 4）

**优先级**：medium  
**关联验收项**：AC-FB1, AC-FB2, AC-FB3  
**前置依赖**：`FavoritesViewModel.addFromFlag()` 已就绪；需要 ToolsScreen 能同时访问 `MapViewModel` 和 `FavoritesViewModel`（目前 ToolsScreen 只拿到了 MapViewModel？需确认，否则通过 hoisting 或 compositionLocal 传）  
**预计修改文件**：`ToolsScreen.kt` + `MainActivity.kt`（若需传 FavoritesViewModel 引用到 ToolsScreen 调用处）

### 分解子步骤
4.1 **批量添加 API 封装**（在调用处写一个 inline helper，避免 VM 间耦合）：
  ```kotlin
  // 在 ToolsScreen 顶层作用域
  fun batchAddToFavorites(
      selectedFlags: List<Flag>,
      favVM: FavoritesViewModel,
      onToast: (String) -> Unit,
  ) {
      var added = 0; var dup = 0
      for (f in selectedFlags) { if (favVM.addFromFlag(f)) added++ else dup++ }
      when {
          added > 0 && dup == 0 -> onToast("已添加 $added 条到收藏夹")
          added > 0 && dup > 0 -> onToast("已添加 $added 条，$dup 条已在收藏夹未重复添加")
          else                 -> onToast("选中的 $dup 条都已在收藏夹中")
      }
  }
  ```

4.2 **UI：按钮插入**：
  - 找到 ToolsScreen 中 `Selected 2+` 显示的批量操作栏（目前按钮顺序 = 计算距离 → 删除选中 → 取消选择）
  - 在「计算距离」之后、「删除选中」之前插入 `FilledTonalButton(...) { Icon(Filled.Add); Text("添加到收藏夹") }`
  - 批量栏整个容器目前有按钮数量限制，检查 maxWidth 是否换行或溢出；如溢出改 `FlowRow` 而非水平 Row

4.3 **FavoritesViewModel 引用传递**：
  - 若 ToolsScreen 的函数签名目前只含 `viewModel: MapViewModel`，则在 MainActivity 中调用 ToolsScreen 的地方新增 `favVM: FavoritesViewModel = viewModel()` 参数向下透传（已存在 FavoritesViewModel 的 instance，此操作是零成本 hoisting）
  - `MainActivity.kt` 中 MyScreen 也同样是拿的同一 instance，不会重复创建

4.4 **选中状态保持**：按钮点击事件调用完 `batchAddToFavorites` 后 **不调用** `selected = emptySet()`；保留选中（验证点 AC-FB3）。

### 测试要求（Test Requirements）
- **TR-4.1** rule：工具页放 3 个旗标 → 全选 3 个 → 点「添加到收藏夹」→ 收藏夹页 3 条，Toast 显示"已添加 3 条"  
- **TR-4.2** rule：选中 3 条（其中 1 条已在收藏）→ Toast 显示"已添加 2 条，1 条已在收藏夹未重复添加"  
- **TR-4.3** rule：操作后眼睛图标仍全亮（选中不自动取消）  
- **TR-4.4** rule：仅选 0/1 条时批量栏不显示，按钮不存在  

### 状态
- Status: pending
- Completion Evidence: 

---

## 任务 5：单框粘贴坐标自动拆分（特性 5）

**优先级**：medium  
**关联验收项**：AC-SP1, AC-SP2, AC-SP3, AC-SP4, AC-SP5, QR-SP-1  
**前置依赖**：无（独立）  
**预计修改文件**：`MapScreen.kt`（UnifiedPanel 内新增输入区块 + 解析逻辑）

### 分解子步骤
5.1 **新增 paste-and-parse 工具函数（顶层 / MapScreen 内部）**：
  ```kotlin
  /**
   * 从文本中解析出 (lon, lat)
   * 规则：支持 , ， ; \s 分隔；
   *   - 一端 >90 绝对值 → 那端是经度（无论前后顺序）
   *   - 两端均 ≤90 → 取较大绝对值作经度（启发式）
   * 返回 Coord?（null = 无法解析/越界）
   */
  private fun parsePasteCoord(text: String): CT.Coord? {
      val cleaned = text.trim().replace("，", ",").replace(";", ",")
      val parts = cleaned.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
      if (parts.size < 2) return null
      val a = parts[0].toDoubleOrNull() ?: return null
      val b = parts[1].toDoubleOrNull() ?: return null
      // 判断顺序
      val (lonCand, latCand) = when {
          abs(a) > 90 && abs(b) <= 90 -> a to b
          abs(b) > 90 && abs(a) <= 90 -> b to a
          abs(a) > abs(b)             -> a to b   // 启发：大者为经度
          else                        -> b to a
      }
      if (lonCand !in -180.0..180.0 || latCand !in -90.0..90.0) return null
      return CT.Coord(lonCand, latCand)
  }
  ```

5.2 **UnifiedPanel 插入单框输入区**：
  - 位置：`搜索地址` OutlinedTextField 区块之后，`FText("跳转坐标")` + 经纬度输入框 之前
  - 视觉：与搜索框同级别 `OutlinedTextField`，
    - Label = `FText("粘贴坐标（经度,纬度）", 11)`
    - placeholder = `FText("如 116.397428, 39.90923", 12)`
    - leadingIcon = `Icon(Default.Paste, contentDescription = "粘贴")`
    - trailingIcon = `Icon(Default.ContentPasteGo, contentDescription = "从剪贴板读取并解析", Modifier.clickable { pasteFromClipboard() })`
    - keyboardOptions = `KeyboardOptions(imeAction = ImeAction.Done)`，Ime Done 触发 `applyParsed(text)`

5.3 **应用解析结果**（`applyParsed(raw: String)`）：
  - 先调 `parsePasteCoord(raw)` → null → Toast "格式错误，请输入 经度,纬度 格式（如 116.397428, 39.90923）"，返回
  - 非 null → 调用 `viewModel.updateLonText("%.6f".format(result.lon))`；`viewModel.updateLatText("%.6f".format(result.lat))`
  - 若触发时 `aMap != null && aMap.mapType 就绪`（即 initGeocodeSearch 已调用），**自动调用** `viewModel.fetchReverseGeocode(result.lon, result.lat)`（与跳转按钮行为一致，让用户立即看到地址）
  - 启发式反序提示：如果"两端都≤90"走的是启发式路径，附加一条淡色 Toast "按较大值为经度识别，必要时请手动调换"
  - 输入框文本保留为已解析值，颜色改为 `MaterialTheme.colorScheme.primary`（或 onSurfaceVariant 淡色）提示用户"已应用"，下次聚焦 typing 恢复 onSurface 黑色

5.4 **📋 剪贴板直接读取**：
  - trailingIcon click 时：
    ```kotlin
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip
    if (clip == null || clip.itemCount == 0) { Toast "剪贴板为空"; return }
    val txt = clip.getItemAt(0).coerceToText(context).toString()
    // 把 txt 填入输入框 textFieldValue + 自动调用 applyParsed
    ```

5.5 **验证 lonText/latText 持久化**：因 `updateLonText`/`updateLatText` 已经内部 `savePref`，单框拆分的 lon/lat 自然持久化，AC-SP5 自动满足。

### 测试要求（Test Requirements）
- **TR-5.1** rule：4 种分隔符（, ，空格;）+ 正序文本 → lon/lat 正确填入  
- **TR-5.2** rule：反序文本 "39.90923, 116.397428" → lon=116.397428, lat=39.90923  
- **TR-5.3** rule："90, 180"（边界）→ 180 作经度，lon=180, lat=90  
- **TR-5.4** rule："abc,def" / "91, 30" / "30, 181" 均报错且原 lon/lat 未变  
- **TR-5.5** rule：点击📋剪贴板按钮 → 复制 "116.397428, 39.90923" 到系统剪贴板 → 解析成功  
- **TR-5.6** rubric (0-3, pass≥2)：2 名"虚拟测试者"直觉性评分（不看说明能否正确使用）  

### 状态
- Status: pending
- Completion Evidence: 

---

## 任务 6：端到端联调 + 编译 + 回归验证

**优先级**：high  
**关联验收项**：所有 AC  
**前置依赖**：任务 1-5 全部完成  
**预计修改文件**：可能修复零散 bug 时修改相关文件

### 步骤
6.1 `gradlew :app:compileDebugKotlin` 零错误零警告
6.2 按 spec.md 的每个 AC 人工走一遍 check 清单，在 Completion Evidence 记结果
6.3 夜间/浅色切换一次，验证动画/气泡颜色正确
6.4 冷启动验证：保存 3 条测量历史 + 3 条收藏 + 3 条旗标 → 杀进程 → 重开，数据全在

### 测试要求（Test Requirements）
- **TR-6.1** rule：`compileDebugKotlin` exit code 0
- **TR-6.2** rule：所有 AC 人工 checklist 全部通过
- **TR-6.3** rubric (0-3, pass≥2)：端到端无阻塞 bug，交互流畅度整体提升明显

### 状态
- Status: pending
- Completion Evidence: 

---

## 依赖关系图

```
Task 1 (动画)   Task 2 (历史)   Task 3 (气泡)   Task 4 (批量收藏)   Task 5 (单框)
       ↓              ↓              ↓                ↓                 ↓
       └──────────────────────────────┴────────────────┴─────────────────┘
                                         ↓
                                  Task 6 (联调验证)
```

Task 1-5 互相独立，可并行；Task 6 是所有完成后最终的收尾。
