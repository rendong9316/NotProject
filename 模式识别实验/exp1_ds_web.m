%% 实验一：Fisher线性分类器设计（修正版，输出完整计算结果）
clear; clc; close all;

%% 真实参数
mu1 = [-2; -2]; Sigma1 = [1, 0; 0, 1];
mu2 = [ 2;  2]; Sigma2 = [1, 0; 0, 4];

%% 小样本：每类10个
N1 = 10; N2 = 10;
rng(2026);  % 固定随机种子，保证可重复
X1 = mvnrnd(mu1, Sigma1, N1)';
X2 = mvnrnd(mu2, Sigma2, N2)';

% --- 计算统计量 ---
m1 = mean(X1, 2);
m2 = mean(X2, 2);
S1 = cov(X1');
S2 = cov(X2');
Sw = (N1-1)*S1 + (N2-1)*S2;
Sb = (m1 - m2)*(m1 - m2)';

% --- Fisher最优投影方向 ---
w = Sw \ (m1 - m2);

% 计算两类样本在 w 上的投影值
proj1 = w' * X1;
proj2 = w' * X2;
m1_tilde = mean(proj1);
m2_tilde = mean(proj2);

% 确保第一类的投影均值小于第二类（若相反则翻转 w 和 w0 符号）
if m1_tilde > m2_tilde
    w = -w;
    proj1 = -proj1;
    proj2 = -proj2;
    m1_tilde = mean(proj1);
    m2_tilde = mean(proj2);
end

% 阈值 (根据公式 w0 = -(m1_tilde+m2_tilde)/2)
w0 = -(m1_tilde + m2_tilde) / 2;

% --- 分类与错误率统计 ---
% 判别函数 g = w'*x - w0, g>=0 判为第一类，否则第二类
all_data = [X1, X2];
true_labels = [ones(1,N1), 2*ones(1,N2)];
g_vals = w' * all_data - w0;
pred = (g_vals >= 0) + 1;   % 逻辑真->1，假->2
err1 = sum(pred(1:N1) ~= 1) / N1 * 100;
err2 = sum(pred(N1+1:end) ~= 2) / N2 * 100;
total_err = (sum(pred ~= true_labels)) / (N1+N2) * 100;

% --- 输出小样本全部计算结果 ---
fprintf('================== 小样本（每类10个）==================\n');
fprintf('【估计的类均值】\n');
fprintf('类1: [%.4f, %.4f]\n', m1(1), m1(2));
fprintf('类2: [%.4f, %.4f]\n', m2(1), m2(2));
fprintf('\n【估计的协方差矩阵】\n');
fprintf('类1:\n'); disp(S1);
fprintf('类2:\n'); disp(S2);
fprintf('\n【类内离散度矩阵 Sw】\n'); disp(Sw);
fprintf('\n【类间离散度矩阵 Sb】\n'); disp(Sb);
fprintf('\n【最优投影方向 w*】\n'); disp(w(:)');
fprintf('\n【投影后类均值】\n');
fprintf('类1投影均值: %.4f\n', m1_tilde);
fprintf('类2投影均值: %.4f\n', m2_tilde);
fprintf('\n【阈值 w0】 = %.4f\n', w0);
fprintf('\n【分类错误率】\n');
fprintf('类1错误率: %.2f%% (%d/%d)\n', err1, sum(pred(1:N1)~=1), N1);
fprintf('类2错误率: %.2f%% (%d/%d)\n', err2, sum(pred(N1+1:end)~=2), N2);
fprintf('总平均错误率: %.2f%%\n', total_err);

% --- 画图（小样本）---
figure('Name','小样本 N=10');
plot(X1(1,:), X1(2,:), 'ro', 'MarkerSize',8,'LineWidth',1.5); hold on;
plot(X2(1,:), X2(2,:), 'b+', 'MarkerSize',8,'LineWidth',1.5);
% 决策直线: w'*x = w0
x_vals = linspace(-5,5,200);
if abs(w(2)) > 1e-6
    y_vals = (w0 - w(1)*x_vals) / w(2);
else
    y_vals = zeros(size(x_vals));
    x_vals = w0/w(1) * ones(size(x_vals));
end
plot(x_vals, y_vals, 'k-', 'LineWidth',1.5);
xlabel('x_1'); ylabel('x_2'); title('Fisher判别（每类10个样本）');
legend('类1','类2','决策直线');
axis equal; grid on;

%% ================== 大样本（每类10000个）==================
N1_big = 10000; N2_big = 10000;
X1_big = mvnrnd(mu1, Sigma1, N1_big)';
X2_big = mvnrnd(mu2, Sigma2, N2_big)';

% 统计量
m1_big = mean(X1_big,2);
m2_big = mean(X2_big,2);
S1_big = cov(X1_big');
S2_big = cov(X2_big');
Sw_big = (N1_big-1)*S1_big + (N2_big-1)*S2_big;
Sb_big = (m1_big - m2_big)*(m1_big - m2_big)';

% Fisher
w_big = Sw_big \ (m1_big - m2_big);
proj1_big = w_big' * X1_big;
proj2_big = w_big' * X2_big;
m1_tilde_big = mean(proj1_big);
m2_tilde_big = mean(proj2_big);
if m1_tilde_big > m2_tilde_big
    w_big = -w_big;
    proj1_big = -proj1_big;
    proj2_big = -proj2_big;
    m1_tilde_big = mean(proj1_big);
    m2_tilde_big = mean(proj2_big);
end
w0_big = -(m1_tilde_big + m2_tilde_big) / 2;

% 分类
all_big = [X1_big, X2_big];
true_big = [ones(1,N1_big), 2*ones(1,N2_big)];
g_vals_big = w_big' * all_big - w0_big;
pred_big = (g_vals_big >= 0) + 1;
err1_big = sum(pred_big(1:N1_big) ~= 1) / N1_big * 100;
err2_big = sum(pred_big(N1_big+1:end) ~= 2) / N2_big * 100;
total_err_big = (sum(pred_big ~= true_big)) / (N1_big+N2_big) * 100;

% 输出大样本结果
fprintf('\n\n================== 大样本（每类10000个）==================\n');
fprintf('【估计的类均值】\n');
fprintf('类1: [%.4f, %.4f]\n', m1_big(1), m1_big(2));
fprintf('类2: [%.4f, %.4f]\n', m2_big(1), m2_big(2));
fprintf('\n【估计的协方差矩阵】\n');
fprintf('类1:\n'); disp(S1_big);
fprintf('类2:\n'); disp(S2_big);
fprintf('\n【类内离散度矩阵 Sw】\n'); disp(Sw_big);
fprintf('\n【类间离散度矩阵 Sb】\n'); disp(Sb_big);
fprintf('\n【最优投影方向 w*】\n'); disp(w_big(:)');
fprintf('\n【投影后类均值】\n');
fprintf('类1投影均值: %.4f\n', m1_tilde_big);
fprintf('类2投影均值: %.4f\n', m2_tilde_big);
fprintf('\n【阈值 w0】 = %.4f\n', w0_big);
fprintf('\n【分类错误率】\n');
fprintf('类1错误率: %.2f%% (%d/%d)\n', err1_big, sum(pred_big(1:N1_big)~=1), N1_big);
fprintf('类2错误率: %.2f%% (%d/%d)\n', err2_big, sum(pred_big(N1_big+1:end)~=2), N2_big);
fprintf('总平均错误率: %.2f%%\n', total_err_big);

% --- 画图（大样本）---
figure('Name','大样本 N=10000');
% 使用小点 + 透明度以显示密度
scatter(X1_big(1,:), X1_big(2,:), 3, 'r', 'filled', 'MarkerFaceAlpha',0.2); hold on;
scatter(X2_big(1,:), X2_big(2,:), 3, 'b', 'filled', 'MarkerFaceAlpha',0.2);
x_vals = linspace(-5,7,200);
if abs(w_big(2)) > 1e-6
    y_vals = (w0_big - w_big(1)*x_vals) / w_big(2);
else
    y_vals = zeros(size(x_vals));
    x_vals = w0_big/w_big(1) * ones(size(x_vals));
end
plot(x_vals, y_vals, 'k-', 'LineWidth',1.5);
xlabel('x_1'); ylabel('x_2'); title('Fisher判别（每类10000个样本）');
legend('类1','类2','决策直线');
axis equal; grid on;