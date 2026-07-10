"""
实验二：K-means 聚类（含3D可视化）- 与更新版 exp2_ds_web.m 对应
"""
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D

plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

def cmeans(X, c, init_mu, max_iter=100):
    N = X.shape[0]
    mu = init_mu.copy()
    for it in range(1, max_iter + 1):
        dist = np.zeros((N, c))
        for k in range(c):
            diff = X - mu[k, :]
            dist[:, k] = np.sum(diff ** 2, axis=1)
        labels = np.argmin(dist, axis=1)
        new_mu = np.zeros((c, X.shape[1]))
        for k in range(c):
            if np.sum(labels == k) > 0:
                new_mu[k, :] = X[labels == k, :].mean(axis=0)
            else:
                new_mu[k, :] = mu[k, :]
        if np.linalg.norm(new_mu - mu, 'fro') < 1e-6:
            mu = new_mu
            break
        mu = new_mu
    return labels + 1, mu, it

# 原始数据
X = np.array([
    [-7.82, -4.58, -3.97], [-6.62,  3.16,  2.71], [ 4.36, -2.19,  2.09],
    [ 6.72,  0.88,  2.80], [-8.64,  3.06,  3.51], [-6.87,  0.57, -5.45],
    [ 4.47, -2.62,  5.76], [ 6.73, -2.01,  4.17], [-7.71,  2.34, -6.33],
    [-6.91, -0.49, -5.68], [ 6.18,  2.81,  5.82], [ 6.72, -0.93, -4.04],
    [-6.25, -0.26,  0.51], [-6.95, -1.22,  1.13], [ 8.09,  0.20,  2.25],
    [ 6.81,  0.18, -4.15], [-5.19,  4.24,  4.04], [-6.38, -1.74,  1.43],
    [ 4.08,  1.30,  5.33], [ 6.27,  0.93, -2.78],
])

max_iter = 100

# 运行四次实验
configs = [
    ('(a) c=2, m1=(1,1,1), m2=(-1,1,-1)', 2, np.array([[1,1,1], [-1,1,-1]])),
    ('(b) c=2, m1=(0,0,0), m2=(1,1,-1)', 2, np.array([[0,0,0], [1,1,-1]])),
    ('(c) c=3, m1=(0,0,0), m2=(1,1,1), m3=(-1,0,2)', 3, np.array([[0,0,0], [1,1,1], [-1,0,2]])),
    ('(d) c=3, 初始中心非常接近', 3, np.array([[-0.1,0,0.1], [0,-0.1,0.1], [-0.1,-0.1,0.1]])),
]

results = []
for name, c, init_mu in configs:
    labels, mu, it = cmeans(X, c, init_mu, max_iter)
    results.append((name, labels, mu, init_mu, it))
    print(f'========== {name} ==========')
    print(f'迭代次数: {it}')
    print('最终聚类中心:')
    print(mu)
    print('标签:', labels)
    print()

# 对比分析
print('========== 结果对比分析 ==========')
for name, labels, mu, init_mu, it in results:
    print(f'{name}: 迭代次数 = {it}')

# ============ 画图（MATLAB 风格 2×2 布局）============
fig = plt.figure(figsize=(14, 10))

colors2 = np.array([[255,99,132], [54,162,235]]) / 255.0
colors3 = np.array([[255,99,132], [54,162,235], [255,206,86]]) / 255.0

for idx, (name, labels, mu, init_mu, it) in enumerate(results):
    ax = fig.add_subplot(2, 2, idx + 1, projection='3d')
    c = mu.shape[0]
    colors = colors2 if c == 2 else colors3

    # 绘制聚类样本点（带黑边填充圆）
    for k in range(c):
        mask = labels == (k + 1)
        if np.sum(mask) > 0:
            ax.scatter(X[mask, 0], X[mask, 1], X[mask, 2],
                       s=80, c=[colors[k]], marker='o', alpha=0.85,
                       edgecolors='black', linewidths=0.8)

    # 绘制最终聚类中心（大菱形）
    for k in range(c):
        ax.scatter(mu[k, 0], mu[k, 1], mu[k, 2],
                   s=200, c=[colors[k]], marker='D',
                   edgecolors='black', linewidths=1.5)

    # 绘制初始中心（黑色菱形，白色边框）
    ax.scatter(init_mu[:, 0], init_mu[:, 1], init_mu[:, 2],
               s=150, c='black', marker='D',
               edgecolors='white', linewidths=1.5, label='初始中心')

    ax.set_xlabel('X1', fontsize=10)
    ax.set_ylabel('X2', fontsize=10)
    ax.set_zlabel('X3', fontsize=10)
    ax.set_title(f'{name}\n迭代次数: {it}', fontsize=11, fontweight='bold')
    ax.view_init(30, 45)
    ax.grid(True)

    # 图例
    legend_handles = []
    legend_names = []
    for k in range(c):
        legend_handles.append(ax.scatter([],[],[], s=50, c=[colors[k]], marker='o',
                                         edgecolors='black', linewidths=0.8))
        legend_names.append(f'类别 {k+1}')
    legend_handles.append(ax.scatter([],[],[], s=80, c='black', marker='D',
                                     edgecolors='white', linewidths=1.5))
    legend_names.append('初始中心')
    ax.legend(legend_handles, legend_names, loc='best', fontsize=8)

plt.tight_layout(pad=2.0)
plt.savefig('figure_exp2_clusters.png', dpi=150, bbox_inches='tight')
plt.close()
print('\n图已保存: figure_exp2_clusters.png')
