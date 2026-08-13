# Locationer — CLAUDE.md

## 坐标显示共识（不可违反）

### 规则一：所有坐标显示保留小数点后 6 位

凡是在 UI 中向用户展示的坐标值（经度、纬度），格式统一为：

```kotlin
"%.6f".format(value)          // 单个坐标分量
"%.6f,%.6f".format(lon, lat)  // 合并显示
```

**禁止使用** `%.5f`、`%.4f`、`%.2f` 等更少精度的格式来展示坐标。距离、角度、面积等非坐标数值不受此约束。

### 规则二：GCJ02（国测局坐标）与 WGS84 必须同时显示

所有向用户展示的坐标信息区域，必须**并列显示两套坐标**：

- **GCJ02**（高德/火星坐标，国内主流地图系统）
- **WGS84**（GPS 原始坐标，国际通用标准）

每个坐标行应带有明确标签，例如：

```kotlin
CoordsRow(label = "GCJ02", coord = gcjCoord, color = ...)
CoordsRow(label = "WGS84", coord = wgsCoord, color = ...)
```

**禁止只展示单一坐标系**。剪贴板复制内容（单次传输）允许只带一种，但 UI 显示必须两种并存。

---

## 页面导航方案（不可违反）

### 规则三：地图页与工具箱页实例长期共存

首页使用底部导航栏切换地图页和工具箱页。**两个页面实例在 composition 中始终存活，切 tab 时只控制显示与隐藏，不销毁任何页面实例。**

**实现方式：alpha + zIndex 叠加**

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .alpha(if (selectedTab == 0) 1f else 0f)
                .zIndex(if (selectedTab == 0) 1f else 0f),
        ) { MapScreen() }
        Box(
            modifier = Modifier.fillMaxSize()
                .alpha(if (selectedTab == 1) 1f else 0f)
                .zIndex(if (selectedTab == 1) 1f else 0f),
        ) { ToolsScreen() }
    }
    // ... 底部导航栏
}
```

**关键约束：**

- 禁止使用 `when(selectedTab)` 直接渲染页面——切 tab 时会销毁非激活页的 composition，导致 MapView 重建、镜头位置丢失。
- 禁止用 `weight(if tab==0) 1f else 0f` 隐藏非激活页——`AndroidView`(MapView) 在 measure 高度为 0 时会触发崩溃。
- 禁止使用 `AnimatedContent`——动画完成后旧页面会被完全移出 composition，恢复切回时 MapView 仍然重建。
- 必须使用 alpha（0/1）+ zIndex（0/1）：alpha=0 隐藏、alpha=1 显示；zIndex 保证激活页在上层接收触摸事件。Instant switch，无动画。

---

## 已知合规检查记录

| 位置 | GCJ02 6位 | WGS84 6位 | 状态 |
|---|---|---|---|
| 底部信息面板（当前位置） | ✅ | ✅ | 已合规 |
| 旗标行坐标（旗标管理） | ✅ | ❌ **待修复** | 仅显示 GCJ02 |
| 距离/方位角输入框 | ✅ | — | 仅单套，属正确行为 |
| 坐标跳转输入栏 | ✅ | — | 仅单套，属正确行为 |
| 复制剪贴板内容 | ✅ | — | 仅 GCJ02，合理 |

> 注：剪贴板复制和工具卡片输入框属于单坐标系操作场景，不受「双坐标同时显示」规则约束。
