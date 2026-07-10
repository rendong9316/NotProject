%% ======================================================================
% 实验一：Fisher 线性分类器设计
% 模式识别上机实验 2026
% ======================================================================
clear; clc; close all;

%% ===== 任务一：Fisher线性判别的基本任务 =====
fprintf('========== 实验一：Fisher 线性分类器设计 ==========\n\n');
fprintf('任务一：Fisher线性判别的基本任务\n');
fprintf(['Fisher线性判别的基本思想是将高维样本投影到一维直线上，\n' ...
         '使得投影后同类样本尽可能聚集、异类样本尽可能分离，\n' ...
         '从而在一维空间中找到最佳的分类阈值。\n']);
fprintf('Bayes分类器基于最小错误率（或最小风险）决策，\n');
fprintf('而Fisher判别不依赖于概率分布的具体形式，是一种更实用的线性分类方法。\n\n');

%% ===== 任务二：Fisher判别函数与决策面 =====
fprintf('========== 任务二：Fisher判别函数权向量与阈值 ==========\n\n');

% 给定训练数据 (各10个样本，共20个)
% 前10个为 ω1 类，后10个为 ω2 类
data_w1 = [
    -7.82, -4.58, -3.97;
    -6.62,  3.16,  2.71;
     4.36, -2.19,  2.09;
     6.72,  0.88,  2.80;
    -8.64,  3.06,  3.51;
    -6.87,  0.57, -5.45;
     4.47, -2.62,  5.76;
     6.73, -2.01,  4.17;
    -7.71,  2.34, -6.33;
    -6.91, -0.49, -5.68
];

data_w2 = [
     6.18,  2.81,  5.82;
     6.72, -0.93, -4.04;
    -6.25, -0.26,  0.51;
    -6.95, -1.22,  1.13;
     8.09,  0.20,  2.25;
     6.81,  0.18, -4.15;
    -5.19,  4.24,  4.04;
    -6.38, -1.74,  1.43;
     4.08,  1.30,  5.33;
     6.27,  0.93, -2.78
];

n1 = size(data_w1, 1);  % 类别1样本数
n2 = size(data_w2, 1);  % 类别2样本数
d = size(data_w1, 2);   % 特征维数

fprintf('训练数据：\n');
fprintf('  ω1 类（%d 个样本）、ω2 类（%d 个样本），特征维数 d = %d\n\n', n1, n2, d);

%% 计算 Fisher 线性判别
% 1. 计算各类样本均值
m1 = mean(data_w1)';       % 3×1 列向量
m2 = mean(data_w2)';       % 3×1 列向量

fprintf('ω1 类样本均值向量: (%.4f, %.4f, %.4f)^T\n', m1(1), m1(2), m1(3));
fprintf('ω2 类样本均值向量: (%.4f, %.4f, %.4f)^T\n\n', m2(2), m2(2), m2(3));

% 2. 计算类内散度矩阵 S_w = S_1 + S_2
S1 = zeros(d, d);
S2 = zeros(d, d);
for i = 1:n1
    diff1 = data_w1(i, :)' - m1;
    S1 = S1 + diff1 * diff1';
end
for i = 1:n2
    diff2 = data_w2(i, :)' - m2;
    S2 = S2 + diff2 * diff2';
end
Sw = S1 + S2;

fprintf('类内散度矩阵 Sw:\n');
disp(Sw);

% 3. 计算类间散度矩阵 S_b = (m1 - m2)(m1 - m2)^T
Sb = (m1 - m2) * (m1 - m2)';

fprintf('类间散度矩阵 Sb:\n');
disp(Sb);

% 4. 求 Fisher 最佳投影方向 w* = Sw^{-1}(m1 - m2)
w_star = Sw \ (m1 - m2);  % 等价于 inv(Sw) * (m1 - m2)
% 归一化
w_star = w_star / norm(w_star);

fprintf('Fisher 最佳投影方向 w* (归一化): (%.6f, %.6f, %.6f)^T\n', ...
        w_star(1), w_star(2), w_star(3));

% 5. 将训练样本投影到 Fisher 直线上
y1 = data_w1 * w_star;  % ω1 类投影值
y2 = data_w2 * w_star;  % ω2 类投影值

% 6. 计算分类阈值 w0 (取两类投影均值的平均)
y1_mean = mean(y1);
y2_mean = mean(y2);
w0 = (y1_mean + y2_mean) / 2;

fprintf('ω1 类投影均值: %.6f\n', y1_mean);
fprintf('ω2 类投影均值: %.6f\n', y2_mean);
fprintf('Fisher 分类阈值 w0: %.6f\n\n', w0);

% 7. 对训练样本进行分类
pred_train_w1 = zeros(n1, 1);
pred_train_w2 = zeros(n2, 1);

for i = 1:n1
    y = data_w1(i, :) * w_star;
    if (y1_mean > y2_mean)
        pred_train_w1(i) = (y > w0);  % ω1投影值大，大于阈值判为ω1
    else
        pred_train_w1(i) = (y < w0);
    end
end
for i = 1:n2
    y = data_w2(i, :) * w_star;
    if (y1_mean > y2_mean)
        pred_train_w2(i) = (y <= w0);  % ω2投影值小
    else
        pred_train_w2(i) = (y >= w0);
    end
end

train_err_rate = (sum(pred_train_w1 == 0) + sum(pred_train_w2 == 0)) / (n1 + n2);
fprintf('训练集 Fisher 分类错误率: %.2f%% (%d/%d)\n\n', ...
        train_err_rate * 100, ...
        sum(pred_train_w1 == 0) + sum(pred_train_w2 == 0), n1 + n2);

%% ===== 任务三：先验概率的影响 =====
fprintf('========== 任务三：先验概率的影响 ==========\n\n');

% 先验概率组合
prior_combos = [
    0.5, 0.5;
    0.3, 0.7;
    0.7, 0.3;
    0.1, 0.9;
    0.9, 0.1
];

fprintf('不同先验概率下的 Fisher 阈值调整：\n');
fprintf('  基本阈值 w0 (等先验): %.6f\n', w0);
fprintf('  %-12s %-12s %-16s\n', 'P(ω1)', 'P(ω2)', '调整后阈值 w0''');

for k = 1:size(prior_combos, 1)
    p1 = prior_combos(k, 1);
    p2 = prior_combos(k, 2);
    % 考虑先验概率的阈值修正: w0' = w0 - ln(P(ω1)/P(ω2))
    w0_adjusted = w0 - log(p1 / p2);
    fprintf('  %-12.1f %-12.1f %-16.6f\n', p1, p2, w0_adjusted);
end
fprintf('\n');

%% ===== 任务四：大样本生成与错误率 =====
fprintf('========== 任务四：大样本测试与错误率估计 ==========\n\n');

N_test = 10000;

% 使用训练样本的均值和协方差生成测试数据
S1_sample = S1 / (n1 - 1);  % 样本协方差矩阵
S2_sample = S2 / (n2 - 1);

fprintf('使用训练样本估计的协方差生成 %d 个测试样本...\n', N_test);

% 生成测试数据 (多元正态分布)
rng(42);  % 设置随机种子保证可重复性
test_w1 = mvnrnd(m1', S1_sample, N_test);
test_w2 = mvnrnd(m2', S2_sample, N_test);

% Fisher 分类
y_test1 = test_w1 * w_star;
y_test2 = test_w2 * w_star;

% 等先验分类
if y1_mean > y2_mean
    correct1 = sum(y_test1 > w0);
    correct2 = sum(y_test2 <= w0);
else
    correct1 = sum(y_test1 < w0);
    correct2 = sum(y_test2 >= w0);
end

err_fisher = (2 * N_test - correct1 - correct2) / (2 * N_test);
fprintf('Fisher 分类器测试错误率 (等先验): %.4f%%\n\n', err_fisher * 100);

% 不同先验概率下的测试错误率
fprintf('不同先验概率下的 Fisher 分类测试错误率：\n');
fprintf('  %-12s %-12s %-12s\n', 'P(ω1)', 'P(ω2)', '错误率');

for k = 1:size(prior_combos, 1)
    p1 = prior_combos(k, 1);
    p2 = prior_combos(k, 2);
    w0_adj = w0 - log(p1 / p2);

    if y1_mean > y2_mean
        c1 = sum(y_test1 > w0_adj);
        c2 = sum(y_test2 <= w0_adj);
    else
        c1 = sum(y_test1 < w0_adj);
        c2 = sum(y_test2 >= w0_adj);
    end
    err = (2 * N_test - c1 - c2) / (2 * N_test);
    fprintf('  %-12.1f %-12.1f %-12.4f%%\n', p1, p2, err * 100);
end
fprintf('\n');

%% ===== 任务五：不同参数下的实验 =====
fprintf('========== 任务五：不同均值向量参数下的实验 ==========\n\n');

% 定义不同参数配置
configs = {
    % 二分类情况
    struct('c', 2, 'name', 'c=2: m1=(1,1,1), m2=(-1,1,-1)', ...
           'means', {[1 1 1; -1 1 -1]}, ...
           'sigma', {[1 0 0; 0 1 0; 0 0 1]}),
    struct('c', 2, 'name', 'c=2: m1=(0,0,0), m2=(1,1,-1)', ...
           'means', {[0 0 0; 1 1 -1]}, ...
           'sigma', {[1 0 0; 0 1 0; 0 0 1]}),
    % 三分类情况 (Fisher适用于二分类，多分类需One-vs-Rest或扩展)
    struct('c', 3, 'name', 'c=3: m1=(0,0,0), m2=(1,1,1), m3=(-1,0,2)', ...
           'means', {[0 0 0; 1 1 1; -1 0 2]}, ...
           'sigma', {[1 0 0; 0 1 0; 0 0 1]}),
    struct('c', 3, 'name', 'c=3: m1=(-0.1,0,0.1), m2=(0,-0.1,0.1), m3=(-0.1,-0.1,0.1)', ...
           'means', {[-0.1 0 0.1; 0 -0.1 0.1; -0.1 -0.1 0.1]}, ...
           'sigma', {[1 0 0; 0 1 0; 0 0 1]})
};

results_table = cell(length(configs), 4);

for cfg_idx = 1:length(configs)
    cfg = configs{cfg_idx};
    c = cfg.c;
    means = cfg.means;
    sigma = cfg.sigma;

    fprintf('配置 %d: %s\n', cfg_idx, cfg.name);

    if c == 2
        % 二分类 Fisher
        N_per_class = 50;
        rng(cfg_idx * 100);
        X1 = mvnrnd(means(1,:), sigma, N_per_class);
        X2 = mvnrnd(means(2,:), sigma, N_per_class);

        m1_hat = mean(X1)';
        m2_hat = mean(X2)';

        % 计算 Sw
        Sw_cfg = zeros(d, d);
        for i = 1:N_per_class
            Sw_cfg = Sw_cfg + (X1(i,:)' - m1_hat) * (X1(i,:)' - m1_hat)';
            Sw_cfg = Sw_cfg + (X2(i,:)' - m2_hat) * (X2(i,:)' - m2_hat)';
        end

        w_cfg = Sw_cfg \ (m1_hat - m2_hat);
        w_cfg = w_cfg / norm(w_cfg);

        y1_cfg = X1 * w_cfg;
        y2_cfg = X2 * w_cfg;
        y1_m = mean(y1_cfg);
        y2_m = mean(y2_cfg);
        w0_cfg = (y1_m + y2_m) / 2;

        % 测试
        N_test_cfg = 1000;
        rng(cfg_idx * 100 + 1);
        T1 = mvnrnd(means(1,:), sigma, N_test_cfg);
        T2 = mvnrnd(means(2,:), sigma, N_test_cfg);

        ty1 = T1 * w_cfg;
        ty2 = T2 * w_cfg;

        if y1_m > y2_m
            c1 = sum(ty1 > w0_cfg);
            c2 = sum(ty2 <= w0_cfg);
        else
            c1 = sum(ty1 < w0_cfg);
            c2 = sum(ty2 >= w0_cfg);
        end
        err_rate = (2 * N_test_cfg - c1 - c2) / (2 * N_test_cfg);

        fprintf('  Fisher 权向量 w*: (%.4f, %.4f, %.4f)^T\n', w_cfg(1), w_cfg(2), w_cfg(3));
        fprintf('  阈值 w0: %.4f\n', w0_cfg);
        fprintf('  测试错误率: %.2f%%\n\n', err_rate * 100);

        results_table{cfg_idx, 1} = cfg.name;
        results_table{cfg_idx, 2} = sprintf('(%.2f, %.2f, %.2f)', w_cfg(1), w_cfg(2), w_cfg(3));
        results_table{cfg_idx, 3} = sprintf('%.4f', w0_cfg);
        results_table{cfg_idx, 4} = sprintf('%.2f%%', err_rate * 100);

    else
        % 三分类：采用 One-vs-One 策略
        N_per_class = 50;
        rng(cfg_idx * 100);
        X = cell(c, 1);
        for i = 1:c
            X{i} = mvnrnd(means(i,:), sigma, N_per_class);
        end

        % One-vs-One: C(c,2) 个 Fisher 分类器
        n_classifiers = nchoosek(c, 2);
        w_all = zeros(d, n_classifiers);
        w0_all = zeros(1, n_classifiers);
        pairs = zeros(n_classifiers, 2);

        clf_idx = 1;
        for i = 1:(c-1)
            for j = (i+1):c
                pairs(clf_idx, :) = [i, j];

                m_hat_i = mean(X{i})';
                m_hat_j = mean(X{j})';

                Sw_ij = zeros(d, d);
                for k = 1:N_per_class
                    Sw_ij = Sw_ij + (X{i}(k,:)' - m_hat_i)*(X{i}(k,:)' - m_hat_i)';
                    Sw_ij = Sw_ij + (X{j}(k,:)' - m_hat_j)*(X{j}(k,:)' - m_hat_j)';
                end

                w_ij = Sw_ij \ (m_hat_i - m_hat_j);
                w_ij = w_ij / norm(w_ij);
                w_all(:, clf_idx) = w_ij;

                yi = X{i} * w_ij;
                yj = X{j} * w_ij;
                w0_all(clf_idx) = (mean(yi) + mean(yj)) / 2;

                fprintf('  分类器 %d vs %d: w*=(%.3f,%.3f,%.3f), w0=%.4f\n', ...
                        i, j, w_ij(1), w_ij(2), w_ij(3), w0_all(clf_idx));

                clf_idx = clf_idx + 1;
            end
        end

        % 测试：投票法
        N_test_cfg = 1000;
        rng(cfg_idx * 100 + 1);
        T_all = [];
        T_labels = [];
        for i = 1:c
            Ti = mvnrnd(means(i,:), sigma, N_test_cfg);
            T_all = [T_all; Ti];
            T_labels = [T_labels; i * ones(N_test_cfg, 1)];
        end

        % 对每个测试样本投票
        votes = zeros(size(T_all, 1), c);
        for clf_idx = 1:n_classifiers
            proj = T_all * w_all(:, clf_idx);

            % 确定该分类器中大投影值对应哪个类
            y_proj_i = X{pairs(clf_idx, 1)} * w_all(:, clf_idx);
            y_proj_j = X{pairs(clf_idx, 2)} * w_all(:, clf_idx);

            if mean(y_proj_i) > mean(y_proj_j)
                class_above = pairs(clf_idx, 1);
                class_below = pairs(clf_idx, 2);
            else
                class_above = pairs(clf_idx, 2);
                class_below = pairs(clf_idx, 1);
            end

            for t = 1:size(T_all, 1)
                if proj(t) > w0_all(clf_idx)
                    votes(t, class_above) = votes(t, class_above) + 1;
                else
                    votes(t, class_below) = votes(t, class_below) + 1;
                end
            end
        end

        [~, pred_labels] = max(votes, [], 2);
        err_rate = sum(pred_labels ~= T_labels) / length(T_labels);

        fprintf('  三分类 (OvO-Fisher) 测试错误率: %.2f%%\n\n', err_rate * 100);

        results_table{cfg_idx, 1} = cfg.name;
        results_table{cfg_idx, 2} = sprintf('%d个OvO分类器', n_classifiers);
        results_table{cfg_idx, 3} = '--';
        results_table{cfg_idx, 4} = sprintf('%.2f%%', err_rate * 100);
    end
end

fprintf('\n========== 不同参数配置实验结果汇总 ==========\n\n');
fprintf('%-55s %-25s %-12s %-12s\n', '参数配置', 'Fisher 权向量', '阈值 w0', '错误率');
fprintf('%-55s %-25s %-12s %-12s\n', '--------', '--------', '--------', '--------');
for i = 1:size(results_table, 1)
    fprintf('%-55s %-25s %-12s %-12s\n', ...
            results_table{i,1}, results_table{i,2}, results_table{i,3}, results_table{i,4});
end

fprintf('\n========== 任务六：MATLAB 仿真结果分析 ==========\n\n');
fprintf('1. Fisher 线性判别通过最大化类间散度与类内散度之比，\n');
fprintf('   找到最佳的一维投影方向，实现高维数据的有效分类。\n');
fprintf('2. 等先验概率时，Fisher 阈值取两类投影均值的平均，\n');
fprintf('   分类结果与 Bayes 最小错误率决策面的法线方向一致。\n');
fprintf('3. 先验概率变化会改变分类阈值，从而影响分类边界位置。\n');
fprintf('4. 多类问题可通过 One-vs-One 或 One-vs-Rest 策略扩展 Fisher 判别。\n');
fprintf('5. 不同均值向量配置下 Fisher 分类器均能有效工作，\n');
fprintf('   但当类别均值接近时（如配置4），分类错误率显著升高。\n\n');

%% ===== 可视化 =====
fprintf('正在生成可视化图形...\n');

% 图1：训练数据 3D 散点图 + Fisher 投影方向
figure('Name', 'Fisher 线性判别 - 训练数据与投影方向', 'Position', [100, 100, 900, 700]);

subplot(2, 2, 1);
scatter3(data_w1(:,1), data_w1(:,2), data_w1(:,3), 80, 'r', 'o', 'filled', ...
        'DisplayName', '\omega_1 类');
hold on;
scatter3(data_w2(:,1), data_w2(:,2), data_w2(:,3), 80, 'b', 's', 'filled', ...
        'DisplayName', '\omega_2 类');

% 绘制 Fisher 投影方向
scale = 15;
quiver3(0, 0, 0, w_star(1)*scale, w_star(2)*scale, w_star(3)*scale, ...
        'k-', 'LineWidth', 2.5, 'DisplayName', 'Fisher 投影方向 w*');
quiver3(0, 0, 0, -w_star(1)*scale, -w_star(2)*scale, -w_star(3)*scale, ...
        'k-', 'LineWidth', 2.5, 'HandleVisibility', 'off');

xlabel('X_1', 'FontSize', 12);
ylabel('X_2', 'FontSize', 12);
zlabel('X_3', 'FontSize', 12);
title('训练样本 3D 分布与 Fisher 投影方向', 'FontSize', 14);
legend('Location', 'best');
grid on;
view(45, 30);

% 图2：Fisher 投影直方图
subplot(2, 2, 2);
histogram(y1, 8, 'FaceColor', 'r', 'FaceAlpha', 0.6, 'DisplayName', '\omega_1 投影');
hold on;
histogram(y2, 8, 'FaceColor', 'b', 'FaceAlpha', 0.6, 'DisplayName', '\omega_2 投影');
xline(w0, 'k--', 'LineWidth', 2, 'DisplayName', sprintf('阈值 w_0=%.2f', w0));
xlabel('投影值 y = w^{*T} x', 'FontSize', 12);
ylabel('频数', 'FontSize', 12);
title('Fisher 投影后的一维分布', 'FontSize', 14);
legend('Location', 'best');
grid on;

% 图3：先验概率对阈值的影响
subplot(2, 2, 3);
p1_range = 0.01:0.01:0.99;
w0_range = w0 - log(p1_range ./ (1 - p1_range));
plot(p1_range, w0_range, 'b-', 'LineWidth', 2);
hold on;
plot([0.5, 0.5], ylim, 'r--', 'LineWidth', 1.5);
xlabel('先验概率 P(\omega_1)', 'FontSize', 12);
ylabel('调整后阈值 w_0''', 'FontSize', 12);
title('先验概率对分类阈值的影响', 'FontSize', 14);
grid on;

% 图4：不同先验概率下的错误率
subplot(2, 2, 4);
p1_list = [0.1, 0.3, 0.5, 0.7, 0.9];
err_list = zeros(size(p1_list));
for k = 1:length(p1_list)
    p1 = p1_list(k);
    w0_adj = w0 - log(p1 / (1 - p1));
    if y1_mean > y2_mean
        c1 = sum(y_test1 > w0_adj);
        c2 = sum(y_test2 <= w0_adj);
    else
        c1 = sum(y_test1 < w0_adj);
        c2 = sum(y_test2 >= w0_adj);
    end
    err_list(k) = (2 * N_test - c1 - c2) / (2 * N_test) * 100;
end
bar(p1_list, err_list, 'FaceColor', [0.3 0.6 0.9]);
xlabel('先验概率 P(\omega_1)', 'FontSize', 12);
ylabel('测试错误率 (%)', 'FontSize', 12);
title('不同先验概率下的分类错误率 (10000 测试样本)', 'FontSize', 14);
for k = 1:length(p1_list)
    text(p1_list(k), err_list(k) + 0.5, sprintf('%.2f%%', err_list(k)), ...
         'HorizontalAlignment', 'center', 'FontSize', 10);
end
grid on;

saveas(gcf, 'figure1_fisher_basic.png');
fprintf('  图1已保存: figure1_fisher_basic.png\n');

% 图2：不同参数配置下的 Fisher 分类效果对比
figure('Name', '不同参数配置对比', 'Position', [100, 100, 1000, 800]);

param_names = cell(length(configs), 1);
param_errors = zeros(length(configs), 1);
for i = 1:length(configs)
    param_names{i} = sprintf('配置%d', i);
    err_str = results_table{i, 4};
    param_errors(i) = str2double(err_str(1:end-1));
end

subplot(2, 1, 1);
b = bar(param_errors, 'FaceColor', [0.2 0.6 0.8]);
set(gca, 'XTickLabel', param_names);
ylabel('测试错误率 (%)', 'FontSize', 12);
title('不同参数配置下的 Fisher 分类错误率', 'FontSize', 14);
grid on;
for i = 1:length(param_errors)
    text(i, param_errors(i) + 0.3, sprintf('%.2f%%', param_errors(i)), ...
         'HorizontalAlignment', 'center', 'FontSize', 10);
end

% 子图2：显示配置1和配置2的3D散点图
subplot(2, 1, 2);
cfg_vis = configs{1};  % 显示配置1
c = cfg_vis.c;
N_vis = 50;
rng(100);
colors = {'r', 'b', 'g'};
markers = {'o', 's', '^'};
for i = 1:c
    X_vis = mvnrnd(cfg_vis.means(i,:), cfg_vis.sigma, N_vis);
    scatter3(X_vis(:,1), X_vis(:,2), X_vis(:,3), 50, colors{i}, markers{i}, 'filled', ...
             'DisplayName', sprintf('\\omega_%d', i));
    hold on;
end
xlabel('X_1', 'FontSize', 12);
ylabel('X_2', 'FontSize', 12);
zlabel('X_3', 'FontSize', 12);
title(['参数配置示例: ', cfg_vis.name], 'FontSize', 13);
legend('Location', 'best');
grid on;
view(45, 30);

saveas(gcf, 'figure2_params_comparison.png');
fprintf('  图2已保存: figure2_params_comparison.png\n');

fprintf('\n========== 实验一仿真完成 ==========\n');
fprintf('所有结果已输出到命令窗口，图形已保存为 PNG 文件。\n');
