"""
实验一：Fisher 线性分类器设计
模式识别上机实验 2026
Python 仿真实现（用于报告生成）
"""
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from scipy.stats import multivariate_normal
from itertools import combinations
import warnings
warnings.filterwarnings('ignore')

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

print("=" * 60)
print("实验一：Fisher 线性分类器设计")
print("=" * 60)

# ===== 任务一：Fisher线性判别的基本任务 =====
print("\n任务一：Fisher线性判别的基本任务")
print("-" * 40)
print("Fisher线性判别将高维样本投影到一维直线上，使同类聚集、异类分离。")
print("与Bayes分类器相比，Fisher判别不依赖分布假设，适用范围更广。")

# ===== 任务二：Fisher判别函数与决策面 =====
print("\n任务二：Fisher判别函数权向量与阈值")
print("-" * 40)

# 训练数据
data_w1 = np.array([
    [-7.82, -4.58, -3.97],
    [-6.62,  3.16,  2.71],
    [ 4.36, -2.19,  2.09],
    [ 6.72,  0.88,  2.80],
    [-8.64,  3.06,  3.51],
    [-6.87,  0.57, -5.45],
    [ 4.47, -2.62,  5.76],
    [ 6.73, -2.01,  4.17],
    [-7.71,  2.34, -6.33],
    [-6.91, -0.49, -5.68]
])

data_w2 = np.array([
    [ 6.18,  2.81,  5.82],
    [ 6.72, -0.93, -4.04],
    [-6.25, -0.26,  0.51],
    [-6.95, -1.22,  1.13],
    [ 8.09,  0.20,  2.25],
    [ 6.81,  0.18, -4.15],
    [-5.19,  4.24,  4.04],
    [-6.38, -1.74,  1.43],
    [ 4.08,  1.30,  5.33],
    [ 6.27,  0.93, -2.78]
])

n1, n2 = len(data_w1), len(data_w2)
d = data_w1.shape[1]

# 计算均值
m1 = data_w1.mean(axis=0).reshape(-1, 1)
m2 = data_w2.mean(axis=0).reshape(-1, 1)

print(f"ω1类均值: ({m1[0,0]:.4f}, {m1[1,0]:.4f}, {m1[2,0]:.4f})^T")
print(f"ω2类均值: ({m2[0,0]:.4f}, {m2[1,0]:.4f}, {m2[2,0]:.4f})^T")

# 类内散度矩阵
S1 = np.zeros((d, d))
S2 = np.zeros((d, d))
for x in data_w1:
    diff = x.reshape(-1, 1) - m1
    S1 += diff @ diff.T
for x in data_w2:
    diff = x.reshape(-1, 1) - m2
    S2 += diff @ diff.T
Sw = S1 + S2

# 类间散度矩阵
Sb = (m1 - m2) @ (m1 - m2).T

# Fisher投影方向
w_star = np.linalg.solve(Sw, m1 - m2)
w_star = w_star / np.linalg.norm(w_star)

print(f"Sw:\n{Sw}")
print(f"\nFisher权向量 w*: ({w_star[0,0]:.6f}, {w_star[1,0]:.6f}, {w_star[2,0]:.6f})^T")

# 投影
y1 = (data_w1 @ w_star).flatten()
y2 = (data_w2 @ w_star).flatten()
y1_mean = y1.mean()
y2_mean = y2.mean()
w0 = (y1_mean + y2_mean) / 2

print(f"ω1投影均值: {y1_mean:.6f}")
print(f"ω2投影均值: {y2_mean:.6f}")
print(f"分类阈值 w0: {w0:.6f}")

# 训练集分类
if y1_mean > y2_mean:
    pred1_train = (y1 > w0).astype(int)
    pred2_train = (y2 <= w0).astype(int)
else:
    pred1_train = (y1 < w0).astype(int)
    pred2_train = (y2 >= w0).astype(int)

train_err = (sum(pred1_train == 0) + sum(pred2_train == 0)) / (n1 + n2)
print(f"训练集错误率: {train_err*100:.2f}%")

# 投影值表格
print("\n训练样本 Fisher 投影值:")
print(f"{'样本':>5} {'ω1投影':>12} {'样本':>5} {'ω2投影':>12}")
for i in range(max(n1, n2)):
    s1 = f"{y1[i]:>12.6f}" if i < n1 else ""
    s2 = f"{y2[i]:>12.6f}" if i < n2 else ""
    i1_str = str(i+1) if i < n1 else ""
    i2_str = str(i+11) if i < n2 else ""
    print(f"{i1_str:>5} {s1} {i2_str:>5} {s2}")

# ===== 任务三：先验概率的影响 =====
print("\n\n任务三：先验概率的影响")
print("-" * 40)

prior_combos = [(0.5, 0.5), (0.3, 0.7), (0.7, 0.3), (0.1, 0.9), (0.9, 0.1)]
print(f"{'P(ω1)':>8} {'P(ω2)':>8} {'调整后w0':>14}")
for p1, p2 in prior_combos:
    w0_adj = w0 - np.log(p1 / p2)
    print(f"{p1:>8.1f} {p2:>8.1f} {w0_adj:>14.6f}")

# ===== 任务四：大样本生成与错误率 =====
print("\n\n任务四：大样本测试与错误率估计")
print("-" * 40)

N_test = 10000
S1_sample = S1 / (n1 - 1)
S2_sample = S2 / (n2 - 1)

np.random.seed(42)
test_w1 = np.random.multivariate_normal(m1.flatten(), S1_sample, N_test)
test_w2 = np.random.multivariate_normal(m2.flatten(), S2_sample, N_test)

y_test1 = test_w1 @ w_star.flatten()
y_test2 = test_w2 @ w_star.flatten()

if y1_mean > y2_mean:
    correct1 = np.sum(y_test1 > w0)
    correct2 = np.sum(y_test2 <= w0)
else:
    correct1 = np.sum(y_test1 < w0)
    correct2 = np.sum(y_test2 >= w0)

err_fisher = (2 * N_test - correct1 - correct2) / (2 * N_test)
print(f"Fisher等先验测试错误率: {err_fisher*100:.4f}%")

print(f"\n{'P(ω1)':>8} {'P(ω2)':>8} {'错误率':>12}")
for p1, p2 in prior_combos:
    w0_adj = w0 - np.log(p1 / p2)
    if y1_mean > y2_mean:
        c1 = np.sum(y_test1 > w0_adj)
        c2 = np.sum(y_test2 <= w0_adj)
    else:
        c1 = np.sum(y_test1 < w0_adj)
        c2 = np.sum(y_test2 >= w0_adj)
    err = (2 * N_test - c1 - c2) / (2 * N_test)
    print(f"{p1:>8.1f} {p2:>8.1f} {err*100:>10.4f}%")

# ===== 任务五：不同参数下的实验 =====
print("\n\n任务五：不同均值向量参数下的实验")
print("-" * 40)

configs = [
    {
        'name': 'c=2: m1=(1,1,1), m2=(-1,1,-1)',
        'means': np.array([[1, 1, 1], [-1, 1, -1]]),
        'sigma': np.eye(3),
        'c': 2
    },
    {
        'name': 'c=2: m1=(0,0,0), m2=(1,1,-1)',
        'means': np.array([[0, 0, 0], [1, 1, -1]]),
        'sigma': np.eye(3),
        'c': 2
    },
    {
        'name': 'c=3: m1=(0,0,0), m2=(1,1,1), m3=(-1,0,2)',
        'means': np.array([[0, 0, 0], [1, 1, 1], [-1, 0, 2]]),
        'sigma': np.eye(3),
        'c': 3
    },
    {
        'name': 'c=3: m1=(-0.1,0,0.1), m2=(0,-0.1,0.1), m3=(-0.1,-0.1,0.1)',
        'means': np.array([[-0.1, 0, 0.1], [0, -0.1, 0.1], [-0.1, -0.1, 0.1]]),
        'sigma': np.eye(3),
        'c': 3
    }
]

results = []

for cfg_idx, cfg in enumerate(configs):
    c = cfg['c']
    means = cfg['means']
    sigma = cfg['sigma']
    N_per = 50

    print(f"\n配置{cfg_idx+1}: {cfg['name']}")

    if c == 2:
        np.random.seed(cfg_idx * 100)
        X1 = np.random.multivariate_normal(means[0], sigma, N_per)
        X2 = np.random.multivariate_normal(means[1], sigma, N_per)

        m1_hat = X1.mean(axis=0).reshape(-1, 1)
        m2_hat = X2.mean(axis=0).reshape(-1, 1)

        Sw_cfg = np.zeros((3, 3))
        for x in X1:
            diff = x.reshape(-1, 1) - m1_hat
            Sw_cfg += diff @ diff.T
        for x in X2:
            diff = x.reshape(-1, 1) - m2_hat
            Sw_cfg += diff @ diff.T

        w_cfg = np.linalg.solve(Sw_cfg, m1_hat - m2_hat)
        w_cfg = w_cfg / np.linalg.norm(w_cfg)

        y1_cfg = X1 @ w_cfg.flatten()
        y2_cfg = X2 @ w_cfg.flatten()
        y1m, y2m = y1_cfg.mean(), y2_cfg.mean()
        w0_cfg = (y1m + y2m) / 2

        N_test_cfg = 1000
        np.random.seed(cfg_idx * 100 + 1)
        T1 = np.random.multivariate_normal(means[0], sigma, N_test_cfg)
        T2 = np.random.multivariate_normal(means[1], sigma, N_test_cfg)

        ty1 = T1 @ w_cfg.flatten()
        ty2 = T2 @ w_cfg.flatten()

        if y1m > y2m:
            c1, c2 = np.sum(ty1 > w0_cfg), np.sum(ty2 <= w0_cfg)
        else:
            c1, c2 = np.sum(ty1 < w0_cfg), np.sum(ty2 >= w0_cfg)

        err_rate = (2 * N_test_cfg - c1 - c2) / (2 * N_test_cfg)

        print(f"  w*: ({w_cfg[0,0]:.4f}, {w_cfg[1,0]:.4f}, {w_cfg[2,0]:.4f})^T")
        print(f"  w0: {w0_cfg:.4f}, 错误率: {err_rate*100:.2f}%")

        results.append({
            'name': cfg['name'],
            'w': f"({w_cfg[0,0]:.4f}, {w_cfg[1,0]:.4f}, {w_cfg[2,0]:.4f})",
            'w0': f"{w0_cfg:.4f}",
            'error': f"{err_rate*100:.2f}%"
        })
    else:
        np.random.seed(cfg_idx * 100)
        X = []
        for i in range(c):
            X.append(np.random.multivariate_normal(means[i], sigma, N_per))

        n_clf = c * (c - 1) // 2
        w_all = np.zeros((3, n_clf))
        w0_all = np.zeros(n_clf)
        pairs_list = list(combinations(range(c), 2))

        N_test_cfg = 1000
        np.random.seed(cfg_idx * 100 + 1)
        T_all = []
        T_labels = []
        for i in range(c):
            Ti = np.random.multivariate_normal(means[i], sigma, N_test_cfg)
            T_all.append(Ti)
            T_labels.append(np.full(N_test_cfg, i))
        T_all = np.vstack(T_all)
        T_labels = np.hstack(T_labels)

        for clf_idx, (i, j) in enumerate(pairs_list):
            m_hat_i = X[i].mean(axis=0).reshape(-1, 1)
            m_hat_j = X[j].mean(axis=0).reshape(-1, 1)

            Sw_ij = np.zeros((3, 3))
            for x in X[i]:
                diff = x.reshape(-1, 1) - m_hat_i
                Sw_ij += diff @ diff.T
            for x in X[j]:
                diff = x.reshape(-1, 1) - m_hat_j
                Sw_ij += diff @ diff.T

            w_ij = np.linalg.solve(Sw_ij, m_hat_i - m_hat_j)
            w_ij = w_ij / np.linalg.norm(w_ij)
            w_all[:, clf_idx] = w_ij.flatten()

            yi = X[i] @ w_ij.flatten()
            yj = X[j] @ w_ij.flatten()
            w0_all[clf_idx] = (yi.mean() + yj.mean()) / 2

        # 投票
        votes = np.zeros((len(T_all), c))
        for clf_idx, (i, j) in enumerate(pairs_list):
            proj = T_all @ w_all[:, clf_idx]
            yi = X[i] @ w_all[:, clf_idx]
            yj = X[j] @ w_all[:, clf_idx]

            if yi.mean() > yj.mean():
                class_a, class_b = i, j
            else:
                class_a, class_b = j, i

            above = proj > w0_all[clf_idx]
            votes[above, class_a] += 1
            votes[~above, class_b] += 1

        pred = np.argmax(votes, axis=1)
        err_rate = np.sum(pred != T_labels) / len(T_labels)
        print(f"  {n_clf}个OvO分类器, 投票错误率: {err_rate*100:.2f}%")

        results.append({
            'name': cfg['name'],
            'w': f"{n_clf}个OvO分类器",
            'w0': '--',
            'error': f"{err_rate*100:.2f}%"
        })

print("\n\n不同参数配置实验结果汇总:")
print(f"{'配置':<50} {'权向量':<30} {'阈值':<12} {'错误率':<10}")
print("-" * 102)
for r in results:
    print(f"{r['name']:<50} {r['w']:<30} {r['w0']:<12} {r['error']:<10}")

# ===== 生成图形 =====
print("\n\n正在生成图形...")

# 图1: 训练数据3D散点图 + Fisher投影
fig = plt.figure(figsize=(14, 12))

ax1 = fig.add_subplot(2, 2, 1, projection='3d')
ax1.scatter(data_w1[:, 0], data_w1[:, 1], data_w1[:, 2],
            c='red', marker='o', s=80, label=r'$\omega_1$', alpha=0.8)
ax1.scatter(data_w2[:, 0], data_w2[:, 1], data_w2[:, 2],
            c='blue', marker='s', s=80, label=r'$\omega_2$', alpha=0.8)
scale = 12
ax1.quiver(0, 0, 0,
           w_star[0, 0]*scale, w_star[1, 0]*scale, w_star[2, 0]*scale,
           color='black', linewidth=2.5, label='Fisher w*')
ax1.quiver(0, 0, 0,
           -w_star[0, 0]*scale, -w_star[1, 0]*scale, -w_star[2, 0]*scale,
           color='black', linewidth=2.5)
ax1.set_xlabel('X1')
ax1.set_ylabel('X2')
ax1.set_zlabel('X3')
ax1.set_title('Training Data 3D & Fisher Projection Direction')
ax1.legend()
ax1.view_init(30, 45)

# 图2: Fisher投影直方图
ax2 = fig.add_subplot(2, 2, 2)
ax2.hist(y1, bins=6, alpha=0.6, color='red', label=r'$\omega_1$ projection')
ax2.hist(y2, bins=6, alpha=0.6, color='blue', label=r'$\omega_2$ projection')
ax2.axvline(w0, color='black', linestyle='--', linewidth=2, label=f'Threshold w0={w0:.2f}')
ax2.set_xlabel('Projection y = w*x')
ax2.set_ylabel('Frequency')
ax2.set_title('1D Distribution After Fisher Projection')
ax2.legend()
ax2.grid(True, alpha=0.3)

# 图3: 先验概率对阈值影响
ax3 = fig.add_subplot(2, 2, 3)
p1_range = np.linspace(0.01, 0.99, 100)
w0_range = w0 - np.log(p1_range / (1 - p1_range))
ax3.plot(p1_range, w0_range, 'b-', linewidth=2)
ax3.axvline(0.5, color='red', linestyle='--', linewidth=1.5)
ax3.set_xlabel('Prior P(w1)')
ax3.set_ylabel('Adjusted Threshold w0')
ax3.set_title('Effect of Prior on Threshold')
ax3.grid(True, alpha=0.3)

# 图4: 不同先验下的错误率
ax4 = fig.add_subplot(2, 2, 4)
p1_list = np.array([0.1, 0.3, 0.5, 0.7, 0.9])
err_list = []
for p1 in p1_list:
    w0_adj = w0 - np.log(p1 / (1 - p1))
    if y1_mean > y2_mean:
        c1 = np.sum(y_test1 > w0_adj)
        c2 = np.sum(y_test2 <= w0_adj)
    else:
        c1 = np.sum(y_test1 < w0_adj)
        c2 = np.sum(y_test2 >= w0_adj)
    err_list.append((2*N_test - c1 - c2)/(2*N_test)*100)

bars = ax4.bar(p1_list, err_list, width=0.08, color='steelblue', edgecolor='black')
ax4.set_xlabel('Prior P(w1)')
ax4.set_ylabel('Test Error Rate (%)')
ax4.set_title(f'Error Rate vs Prior ({N_test} test samples)')
for i, (x, y) in enumerate(zip(p1_list, err_list)):
    ax4.text(x, y + 0.3, f'{y:.2f}%', ha='center', fontsize=9)
ax4.grid(True, alpha=0.3, axis='y')

plt.tight_layout()
plt.savefig('figure1_fisher_basic.png', dpi=150, bbox_inches='tight')
plt.close()
print("  图1已保存: figure1_fisher_basic.png")

# 图2: 不同参数配置结果对比
fig2, axes = plt.subplots(1, 2, figsize=(14, 6))

# 子图1: 错误率柱状图
param_errors = [float(r['error'].replace('%', '')) for r in results]
param_names_short = [f'Config {i+1}' for i in range(len(results))]
colors_bar = ['#2196F3', '#4CAF50', '#FF9800', '#f44336']
ax_bar = axes[0]
bars2 = ax_bar.bar(param_names_short, param_errors, color=colors_bar[:len(results)],
                   edgecolor='black', linewidth=0.5)
ax_bar.set_ylabel('Test Error Rate (%)')
ax_bar.set_title('Fisher Classification Error Rate Comparison')
for i, (x, y) in enumerate(zip(param_names_short, param_errors)):
    ax_bar.text(i, y + 0.3, f'{y:.2f}%', ha='center', fontsize=10, fontweight='bold')
ax_bar.grid(True, alpha=0.3, axis='y')

# 子图2: 配置1的3D散点图
ax_3d = axes[1]
ax_3d.remove()
ax_3d = fig2.add_subplot(1, 2, 2, projection='3d')
cfg_vis = configs[0]
np.random.seed(100)
colors_3d = ['red', 'blue', 'green']
markers_3d = ['o', 's', '^']
for i in range(cfg_vis['c']):
    X_vis = np.random.multivariate_normal(cfg_vis['means'][i], cfg_vis['sigma'], 50)
    ax_3d.scatter(X_vis[:, 0], X_vis[:, 1], X_vis[:, 2],
                  c=colors_3d[i], marker=markers_3d[i], s=50,
                  label=f'w{i+1}', alpha=0.7)
ax_3d.set_xlabel('X1')
ax_3d.set_ylabel('X2')
ax_3d.set_zlabel('X3')
ax_3d.set_title(f'Config 1: {cfg_vis["name"]}', fontsize=10)
ax_3d.legend(loc='upper left', fontsize=8)
ax_3d.view_init(25, 55)

plt.tight_layout()
plt.savefig('figure2_params_comparison.png', dpi=150, bbox_inches='tight')
plt.close()
print("  图2已保存: figure2_params_comparison.png")

# 图3: 训练数据投影详情
fig3, ax3 = plt.subplots(figsize=(10, 6))

# 绘制投影值条形图
indices = np.arange(max(n1, n2))
width = 0.35
bars1 = ax3.bar(np.arange(n1) - width/2, y1, width, color='red', alpha=0.7,
                label=r'$\omega_1$ class projection')
bars2 = ax3.bar(np.arange(n2) + width/2, y2, width, color='blue', alpha=0.7,
                label=r'$\omega_2$ class projection')
ax3.axhline(y=w0, color='black', linestyle='--', linewidth=2, label=f'Threshold: {w0:.4f}')
ax3.set_xlabel('Sample Index')
ax3.set_ylabel('Fisher Projection Value')
ax3.set_title('Training Sample Fisher Projection Values')
ax3.set_xticks(range(max(n1, n2)))
ax3.legend()
ax3.grid(True, alpha=0.3, axis='y')

plt.tight_layout()
plt.savefig('figure3_projection_detail.png', dpi=150, bbox_inches='tight')
plt.close()
print("  图3已保存: figure3_projection_detail.png")

print("\n所有结果和图形生成完毕！")
