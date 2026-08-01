---
name: cpp-icon-pitfalls
description: C++ Windows icon replacement pitfalls and quick fixes for this project
type: reference
---

# C++ Windows 图标替换坑记录

## 本次修改的文件

| 文件 | 改动 |
|------|------|
| `cpp/media/github.ico` | 从 `github.png` 生成多尺寸 ICO |
| `cpp/src/app.rc` | 新增 `101 ICON DISCARDABLE "media/github.ico"` |
| `cpp/src/entry.cpp` | 把 `IDI_APPLICATION` 改为 `MAKEINTRESOURCEW(101)` |

---

## 遇到的坑 & 速解

### 坑 1：bash 里跑 Windows 批处理脚本 → 找不到命令

**现象**：
```
bash: build.bat: command not found
bash: call: Is a directory
```

**速解**：用 PowerShell 调用：
```bash
powershell -Command "cd 'D:/.../cpp'; & '.\build.bat'"
```

---

### 坑 2：PIL 保存 ICO 报 `KeyError: 'ICO'`

**现象**：
```python
img.save('output.ico', format='ICO', save_all=True)
# KeyError: 'ICO'
```

**速解**：不指定 format，让 PIL 自动猜：
```python
img.save('output.ico')  # 一行搞定
```
生成的 ICO 里会包含 PNG 数据块，Windows 能识别。

---

### 坑 3：资源 ID 不一致导致图标没变

**现象**：`app.rc` 里用 ID `1`，`entry.cpp` 里却用 `IDI_APPLICATION`（系统默认图标），改完图标还是原来的样子。

**速解**：三处 ID 必须一致（推荐用 `101`，和 `config.h` 里的定义对齐）：
- `app.rc`: `101 ICON ...`
- `entry.cpp`: `LoadIconW(instance, MAKEINTRESOURCEW(101))`

---

### 坑 4：只改文件不重新构建 → 还是老图标

**现象**：改完 `app.rc` 和 `entry.cpp` 直接运行，图标没变，因为 `build/app_res.res` 还是旧的。

**速解**：先删掉 `build/` 目录，再完整重建：
```bash
rm -rf build/*
powershell -Command "cd cpp; & '.\build.bat'"
```

---

## 验证方法

1. **看 ICO 有没有 PNG 数据**：
   ```python
   with open('github.ico','rb') as f: print(b'PNG' in f.read())
   # True = Windows 能识别
   ```

2. **看 exe 图标**：右键 exe → 属性 → 详细信息 → 图标

3. **看窗口图标**：启动 exe，看左上角小图标

---

## 快速检查清单

- [ ] `github.ico` 已生成（右键打开能看到猫）
- [ ] `app.rc` 有 `101 ICON ...` 行
- [ ] `entry.cpp` 用 `MAKEINTRESOURCEW(101)` 加载图标
- [ ] `build/` 目录清理并重跑 build
- [ ] 运行 `heatmap.exe`，图标更新成功