%% C-均值（K-means）算法实验 - 三维可视化
% 数据：20个三维样本
clear; clc; close all;

%% 原始数据输入
X = [
    -7.82, -4.58, -3.97;
    -6.62, 3.16, 2.71;
    4.36, -2.19, 2.09;
    6.72, 0.88, 2.80;
    -8.64, 3.06, 3.51;
    -6.87, 0.57, -5.45;
    4.47, -2.62, 5.76;
    6.73, -2.01, 4.17;
    -7.71, 2.34, -6.33;
    -6.91, -0.49, -5.68;
    6.18, 2.81, 5.82;
    6.72, -0.93, -4.04;
    -6.25, -0.26, 0.51;
    -6.95, -1.22, 1.13;
    8.09, 0.20, 2.25;
    6.81, 0.18, -4.15;
    -5.19, 4.24, 4.04;
    -6.38, -1.74, 1.43;
    4.08, 1.30, 5.33;
    6.27, 0.93, -2.78;
];

N = size(X, 1);  % 样本个数=20
max_iter = 100;   % 最大迭代次数

% 创建图形窗口（2×2布局）
figure('Position', [100, 100, 1200, 900]);

%% ========= (a) c=2, m1=[1,1,1], m2=[-1,1,-1] =========
fprintf('========== 实验 (a) ==========\n');
c = 2;
init_mu = [1, 1, 1; -1, 1, -1];
[labels_a, mu_a, iter_a] = cmeans(X, c, init_mu, max_iter);
fprintf('迭代次数: %d\n', iter_a);
disp('最终聚类中心:');
disp(mu_a);

% 可视化 (a)
subplot(2, 2, 1);
visualize_clusters(X, labels_a, mu_a, init_mu, ['(a) c=2, 迭代次数=' num2str(iter_a)]);

%% ========= (b) c=2, m1=[0,0,0], m2=[1,1,-1] =========
fprintf('\n========== 实验 (b) ==========\n');
c = 2;
init_mu = [0, 0, 0; 1, 1, -1];
[labels_b, mu_b, iter_b] = cmeans(X, c, init_mu, max_iter);
fprintf('迭代次数: %d\n', iter_b);
disp('最终聚类中心:');
disp(mu_b);

% 可视化 (b)
subplot(2, 2, 2);
visualize_clusters(X, labels_b, mu_b, init_mu, ['(b) c=2, 迭代次数=' num2str(iter_b)]);

%% ========= (c) c=3, m1=[0,0,0], m2=[1,1,1], m3=[-1,0,2] =========
fprintf('\n========== 实验 (c) ==========\n');
c = 3;
init_mu = [0, 0, 0; 1, 1, 1; -1, 0, 2];
[labels_c, mu_c, iter_c] = cmeans(X, c, init_mu, max_iter);
fprintf('迭代次数: %d\n', iter_c);
disp('最终聚类中心:');
disp(mu_c);

% 可视化 (c)
subplot(2, 2, 3);
visualize_clusters(X, labels_c, mu_c, init_mu, ['(c) c=3, 迭代次数=' num2str(iter_c)]);

%% ========= (d) c=3, 初始中心非常接近 =========
fprintf('\n========== 实验 (d) ==========\n');
c = 3;
init_mu = [-0.1, 0, 0.1; 0, -0.1, 0.1; -0.1, -0.1, 0.1];
[labels_d, mu_d, iter_d] = cmeans(X, c, init_mu, max_iter);
fprintf('迭代次数: %d\n', iter_d);
disp('最终聚类中心:');
disp(mu_d);

% 可视化 (d)
subplot(2, 2, 4);
visualize_clusters(X, labels_d, mu_d, init_mu, ['(d) c=3, 迭代次数=' num2str(iter_d)]);

%% ========= 结果对比分析 =========
fprintf('\n========== 结果对比分析 ==========\n');
fprintf('(a) 迭代次数: %d\n', iter_a);
fprintf('(b) 迭代次数: %d\n', iter_b);
fprintf('(c) 迭代次数: %d\n', iter_c);
fprintf('(d) 迭代次数: %d\n', iter_d);
fprintf('\n解释:\n');
fprintf('1. (a)与(b)对比：初始中心不同会导致迭代次数和最终聚类结果不同。\n');
fprintf('   (a)初始中心选在(1,1,1)和(-1,1,-1)，较合理，迭代较少；\n');
fprintf('   (b)初始中心(0,0,0)和(1,1,-1)可能偏离数据分布，需更多迭代。\n');
fprintf('2. (c)与(d)对比：(c)初始中心选在数据分布较远的位置，收敛快；\n');
fprintf('   (d)初始中心过于接近，导致收敛慢甚至可能陷入局部最优。\n');
fprintf('3. 初始中心的选择显著影响迭代次数和最终聚类质量。\n');

%% ========= C-均值算法核心函数 =========
function [labels, mu, iter] = cmeans(X, c, init_mu, max_iter)
    % X: N×d 数据矩阵
    % c: 类别数
    % init_mu: c×d 初始聚类中心
    % max_iter: 最大迭代次数
    % labels: N×1 类别标签(1~c)
    % mu: 最终聚类中心
    % iter: 实际迭代次数
    
    N = size(X, 1);
    mu = init_mu;   % 初始化聚类中心
    
    for iter = 1:max_iter
        % 步骤1：计算每个样本到各中心的距离，分配类别
        dist_matrix = zeros(N, c);
        for k = 1:c
            diff = X - mu(k, :);
            dist_matrix(:, k) = sum(diff .^ 2, 2);  % 欧氏距离平方
        end
        [~, labels] = min(dist_matrix, [], 2);  % labels: 1~c
        
        % 步骤2：更新聚类中心（每个类的均值）
        new_mu = zeros(c, size(X, 2));
        for k = 1:c
            if sum(labels == k) > 0
                new_mu(k, :) = mean(X(labels == k, :), 1);
            else
                % 如果某类没有样本，保持原中心
                new_mu(k, :) = mu(k, :);
            end
        end
        
        % 步骤3：检查是否收敛（中心变化小于阈值）
        if norm(new_mu - mu, 'fro') < 1e-6
            mu = new_mu;
            break;
        end
        mu = new_mu;
    end
end

%% ========= 可视化聚类结果（三维散点图） =========
function visualize_clusters(X, labels, mu, init_mu, title_text)
    % 定义颜色（支持2类和3类）
    colors2 = [255, 99, 132; 54, 162, 235] / 255;  % 红色，蓝色
    colors3 = [255, 99, 132; 54, 162, 235; 255, 206, 86] / 255;  % 红，蓝，黄
    
    if size(mu, 1) == 2
        colors = colors2;
    else
        colors = colors3;
    end
    
    % 绘制散点图
    hold on;
    for k = 1:size(mu, 1)
        idx = (labels == k);
        if sum(idx) > 0
            scatter3(X(idx, 1), X(idx, 2), X(idx, 3), 80, colors(k, :), 'filled', ...
                'MarkerEdgeColor', 'k', 'LineWidth', 1);
        end
    end
    
    % 绘制最终聚类中心（大菱形）
    for k = 1:size(mu, 1)
        scatter3(mu(k, 1), mu(k, 2), mu(k, 3), 200, colors(k, :), 'filled', ...
            'MarkerEdgeColor', 'k', 'LineWidth', 2, 'Marker', 'd');
    end
    
    % 绘制初始中心（黑色菱形，带白色边框）
    scatter3(init_mu(:,1), init_mu(:,2), init_mu(:,3), 150, 'k', 'd', ...
        'LineWidth', 2, 'MarkerEdgeColor', 'white');
    
    xlabel('X_1', 'FontSize', 11);
    ylabel('X_2', 'FontSize', 11);
    zlabel('X_3', 'FontSize', 11);
    title(title_text, 'FontSize', 12, 'FontWeight', 'bold');
    grid on;
    view(45, 30);  % 设置视角
    
    % 添加图例
    legend_handles = [];
    legend_names = {};
    for k = 1:size(mu, 1)
        legend_handles = [legend_handles, scatter3(NaN, NaN, NaN, 80, colors(k, :), 'filled', 'MarkerEdgeColor', 'k')];
        legend_names{end+1} = ['类别 ' num2str(k)];
    end
    legend_handles = [legend_handles, scatter3(NaN, NaN, NaN, 150, 'k', 'd', 'LineWidth', 2, 'MarkerEdgeColor', 'white')];
    legend_names{end+1} = '初始中心';
    legend(legend_handles, legend_names, 'Location', 'best', 'FontSize', 9);
    
    hold off;
end