"""
实验一：Fisher线性分类器设计
与 exp1_ds_web.m 完全对应的 Python 实现
用于生成实验结果和图片
"""
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from numpy.random import multivariate_normal

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei', 'DejaVu Sans']
plt.rcParams['axes.unicode_minus'] = False

print("=" * 60)
print("实验一：Fisher线性分类器设计")
print("=" * 60)

# ============ 真实参数 ============
mu1 = np.array([-2.0, -2.0])
Sigma1 = np.array([[1, 0], [0, 1]])
mu2 = np.array([2.0, 2.0])
Sigma2 = np.array([[1, 0], [0, 4]])

# ============ 小样本：每类10个 ============
N1, N2 = 10, 10
np.random.seed(2026)
X1 = multivariate_normal(mu1, Sigma1, N1).T  # 2 x N1
X2 = multivariate_normal(mu2, Sigma2, N2).T  # 2 x N2

# 计算统计量
m1 = X1.mean(axis=1)  # (2,)
m2 = X2.mean(axis=1)  # (2,)
S1 = np.cov(X1)        # 协方差矩阵 (2x2)
S2 = np.cov(X2)
Sw = (N1 - 1) * S1 + (N2 - 1) * S2
Sb = np.outer(m1 - m2, m1 - m2)

# Fisher 最优投影方向
w = np.linalg.solve(Sw, m1 - m2)

# 投影
proj1 = w @ X1
proj2 = w @ X2
m1_tilde = proj1.mean()
m2_tilde = proj2.mean()

# 确保第一类投影均值 < 第二类
if m1_tilde > m2_tilde:
    w = -w
    proj1 = -proj1
    proj2 = -proj2
    m1_tilde = proj1.mean()
    m2_tilde = proj2.mean()

# 阈值
w0 = -(m1_tilde + m2_tilde) / 2

# 分类与错误率统计
all_data = np.hstack([X1, X2])
true_labels = np.hstack([np.ones(N1), 2 * np.ones(N2)])
g_vals = w @ all_data - w0
pred = (g_vals >= 0).astype(int) + 1  # 1 for class1, 2 for class2

err1 = np.sum(pred[:N1] != 1) / N1 * 100
err2 = np.sum(pred[N1:] != 2) / N2 * 100
total_err = np.sum(pred != true_labels) / (N1 + N2) * 100

# ============ 输出小样本结果 ============
print('\n================== 小样本（每类10个）==================')
print('【估计的类均值】')
print(f'类1: [{m1[0]:.4f}, {m1[1]:.4f}]')
print(f'类2: [{m2[0]:.4f}, {m2[1]:.4f}]')
print('\n【估计的协方差矩阵】')
print('类1:\n', S1)
print('类2:\n', S2)
print('\n【类内离散度矩阵 Sw】\n', Sw)
print('\n【类间离散度矩阵 Sb】\n', Sb)
print('\n【最优投影方向 w*】\n', w)
print('\n【投影后类均值】')
print(f'类1投影均值: {m1_tilde:.4f}')
print(f'类2投影均值: {m2_tilde:.4f}')
print(f'\n【阈值 w0】 = {w0:.4f}')
print('\n【分类错误率】')
print(f'类1错误率: {err1:.2f}% ({int(np.sum(pred[:N1]!=1))}/{N1})')
print(f'类2错误率: {err2:.2f}% ({int(np.sum(pred[N1:]!=2))}/{N2})')
print(f'总平均错误率: {total_err:.2f}%')

# ============ 画图（小样本）============
fig1, ax1 = plt.subplots(figsize=(8, 8))
ax1.plot(X1[0, :], X1[1, :], 'ro', markersize=8, linewidth=1.5, label='类1')
ax1.plot(X2[0, :], X2[1, :], 'b+', markersize=10, linewidth=1.5, label='类2')

# 决策直线: w'*x = w0
x_vals = np.linspace(-5, 5, 200)
if abs(w[1]) > 1e-6:
    y_vals = (w0 - w[0] * x_vals) / w[1]
else:
    y_vals = np.zeros_like(x_vals)
    x_vals = (w0 / w[0]) * np.ones_like(x_vals)
ax1.plot(x_vals, y_vals, 'k-', linewidth=1.5, label='决策直线')
ax1.set_xlabel('x_1')
ax1.set_ylabel('x_2')
ax1.set_title('Fisher判别（每类10个样本）')
ax1.legend()
ax1.axis('equal')
ax1.grid(True)
plt.tight_layout()
fig1.savefig('figure_small_sample.png', dpi=150, bbox_inches='tight')
plt.close()
print('\n图1已保存: figure_small_sample.png')

# ============ 大样本（每类10000个）============
N1_big, N2_big = 10000, 10000
X1_big = multivariate_normal(mu1, Sigma1, N1_big).T
X2_big = multivariate_normal(mu2, Sigma2, N2_big).T

m1_big = X1_big.mean(axis=1)
m2_big = X2_big.mean(axis=1)
S1_big = np.cov(X1_big)
S2_big = np.cov(X2_big)
Sw_big = (N1_big - 1) * S1_big + (N2_big - 1) * S2_big
Sb_big = np.outer(m1_big - m2_big, m1_big - m2_big)

w_big = np.linalg.solve(Sw_big, m1_big - m2_big)
proj1_big = w_big @ X1_big
proj2_big = w_big @ X2_big
m1_tilde_big = proj1_big.mean()
m2_tilde_big = proj2_big.mean()

if m1_tilde_big > m2_tilde_big:
    w_big = -w_big
    proj1_big = -proj1_big
    proj2_big = -proj2_big
    m1_tilde_big = proj1_big.mean()
    m2_tilde_big = proj2_big.mean()

w0_big = -(m1_tilde_big + m2_tilde_big) / 2

# 分类
all_big = np.hstack([X1_big, X2_big])
true_big = np.hstack([np.ones(N1_big), 2 * np.ones(N2_big)])
g_vals_big = w_big @ all_big - w0_big
pred_big = (g_vals_big >= 0).astype(int) + 1

err1_big = np.sum(pred_big[:N1_big] != 1) / N1_big * 100
err2_big = np.sum(pred_big[N1_big:] != 2) / N2_big * 100
total_err_big = np.sum(pred_big != true_big) / (N1_big + N2_big) * 100

print('\n\n================== 大样本（每类10000个）==================')
print('【估计的类均值】')
print(f'类1: [{m1_big[0]:.4f}, {m1_big[1]:.4f}]')
print(f'类2: [{m2_big[0]:.4f}, {m2_big[1]:.4f}]')
print('\n【估计的协方差矩阵】')
print('类1:\n', S1_big)
print('类2:\n', S2_big)
print('\n【类内离散度矩阵 Sw】\n', Sw_big)
print('\n【类间离散度矩阵 Sb】\n', Sb_big)
print('\n【最优投影方向 w*】\n', w_big)
print('\n【投影后类均值】')
print(f'类1投影均值: {m1_tilde_big:.4f}')
print(f'类2投影均值: {m2_tilde_big:.4f}')
print(f'\n【阈值 w0】 = {w0_big:.4f}')
print('\n【分类错误率】')
print(f'类1错误率: {err1_big:.2f}% ({int(np.sum(pred_big[:N1_big]!=1))}/{N1_big})')
print(f'类2错误率: {err2_big:.2f}% ({int(np.sum(pred_big[N1_big:]!=2))}/{N2_big})')
print(f'总平均错误率: {total_err_big:.2f}%')

# ============ 画图（大样本）============
fig2, ax2 = plt.subplots(figsize=(8, 8))
# 透明度显示密度
ax2.scatter(X1_big[0, :], X1_big[1, :], s=3, c='r', marker='o', alpha=0.2, label='类1')
ax2.scatter(X2_big[0, :], X2_big[1, :], s=3, c='b', marker='o', alpha=0.2, label='类2')

x_vals = np.linspace(-5, 7, 200)
if abs(w_big[1]) > 1e-6:
    y_vals = (w0_big - w_big[0] * x_vals) / w_big[1]
else:
    y_vals = np.zeros_like(x_vals)
    x_vals = (w0_big / w_big[0]) * np.ones_like(x_vals)
ax2.plot(x_vals, y_vals, 'k-', linewidth=1.5, label='决策直线')
ax2.set_xlabel('x_1')
ax2.set_ylabel('x_2')
ax2.set_title('Fisher判别（每类10000个样本）')
ax2.legend(markerscale=3)
ax2.axis('equal')
ax2.grid(True)
plt.tight_layout()
fig2.savefig('figure_large_sample.png', dpi=150, bbox_inches='tight')
plt.close()
print('\n图2已保存: figure_large_sample.png')

# ============ 投影分布直方图（大样本）============
fig3, (ax3a, ax3b) = plt.subplots(1, 2, figsize=(14, 5))

# 小样本投影分布
ax3a.hist(proj1, bins=6, alpha=0.6, color='red', label='类1投影')
ax3a.hist(proj2, bins=6, alpha=0.6, color='blue', label='类2投影')
ax3a.axvline(x=-w0, color='black', linestyle='--', linewidth=2, label=f'阈值 w0={w0:.4f}')
ax3a.set_xlabel('投影值 y = w*x')
ax3a.set_ylabel('频数')
ax3a.set_title('小样本投影分布 (N=10)')
ax3a.legend()
ax3a.grid(True, alpha=0.3)

# 大样本投影分布
ax3b.hist(proj1_big, bins=50, alpha=0.5, color='red', label='类1投影', density=True)
ax3b.hist(proj2_big, bins=50, alpha=0.5, color='blue', label='类2投影', density=True)
ax3b.axvline(x=-w0_big, color='black', linestyle='--', linewidth=2, label=f'阈值 w0={w0_big:.4f}')
ax3b.set_xlabel('投影值 y = w*x')
ax3b.set_ylabel('概率密度')
ax3b.set_title('大样本投影分布 (N=10000)')
ax3b.legend()
ax3b.grid(True, alpha=0.3)

plt.tight_layout()
fig3.savefig('figure_projection_distribution.png', dpi=150, bbox_inches='tight')
plt.close()
print('图3已保存: figure_projection_distribution.png')

print('\n所有结果计算完毕！')
