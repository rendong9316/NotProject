%% 对比雷达平滑航迹 vs 原始量测点
% 绿色实线 = outputPointList (卡尔曼平滑滤波后)
% 红色圆点 = asscPointList   (雷达原始关联量测)

clear; close all;

data = load('track_20251210175946.mat');
trackList = data.trackList;

fprintf('总航迹数: %d\n', length(trackList));

smooth_counts = zeros(length(trackList), 1);
raw_counts    = zeros(length(trackList), 1);

figure('Position', [80, 80, 1400, 900]);
hold on;

h_smooth = [];
h_raw    = [];

for i = 1:length(trackList)
    t = trackList(i);

    % --- outputPointList: 平滑滤波航迹 ---
    if isfield(t, 'outputPointList') && ~isempty(t.outputPointList)
        opl = t.outputPointList;
        if iscell(opl), pts_struct = opl{1}; else, pts_struct = opl; end
        if ~isempty(pts_struct) && isfield(pts_struct, 'lat')
            lat_s = [pts_struct.lat]';
            lon_s = [pts_struct.lon]';
            smooth_counts(i) = length(lat_s);
            if i == 1
                h_smooth = plot(lon_s, lat_s, '-', 'Color', [0 0.7 0], 'LineWidth', 2);
            else
                plot(lon_s, lat_s, '-', 'Color', [0 0.7 0], 'LineWidth', 1.2);
            end
        end
    end

    % --- asscPointList: 原始量测点 ---
    if isfield(t, 'asscPointList') && ~isempty(t.asscPointList)
        apl = t.asscPointList;
        if iscell(apl), pts_struct = apl{1}; else, pts_struct = apl; end
        if ~isempty(pts_struct) && isfield(pts_struct, 'lat')
            lat_r = [pts_struct.lat]';
            lon_r = [pts_struct.lon]';
            raw_counts(i) = length(lat_r);
            if i == 1
                h_raw = plot(lon_r, lat_r, 'r.', 'MarkerSize', 10);
            else
                plot(lon_r, lat_r, 'r.', 'MarkerSize', 6);
            end
        end
    end
end

legend([h_smooth, h_raw], ...
    {sprintf('平滑滤波 (%d条)', sum(smooth_counts > 0)), ...
     sprintf('原始量测 (%d条)', sum(raw_counts > 0))}, ...
    'Location', 'northeast');

xlabel('经度 Longitude (°E)');
ylabel('纬度 Latitude (°N)');
title('雷达航迹对比 — 卡尔曼平滑(绿线) vs 原始量测(红点)');
grid on;
axis equal tight;

fprintf('平滑航迹: %d 条, 平均 %.1f 点/条\n', ...
    sum(smooth_counts > 0), mean(smooth_counts(smooth_counts > 0)));
fprintf('原始量测: %d 条, 平均 %.1f 点/条\n', ...
    sum(raw_counts > 0), mean(raw_counts(raw_counts > 0)));

% ===== 放大局部：第1条航迹详细对比 =====
figure('Position', [80, 80, 1400, 900]);

t1 = trackList(1);

opl = t1.outputPointList;
if iscell(opl), pts_s = opl{1}; else, pts_s = opl; end
subplot(2,2,1);
plot([pts_s.lon], [pts_s.lat], 'g-o', 'LineWidth', 1.5, 'MarkerSize', 6);
title(sprintf('第1条航迹 平滑滤波 (%d点)', length(pts_s)));
xlabel('经度'); ylabel('纬度'); grid on; axis equal;

apl = t1.asscPointList;
if iscell(apl), pts_r = apl{1}; else, pts_r = apl; end
subplot(2,2,2);
plot([pts_r.lon], [pts_r.lat], 'r-o', 'LineWidth', 1.5, 'MarkerSize', 8);
title(sprintf('第1条航迹 原始量测 (%d点)', length(pts_r)));
xlabel('经度'); ylabel('纬度'); grid on; axis equal;

subplot(2,2,[3 4]);
plot([pts_s.lon], [pts_s.lat], 'g-', 'LineWidth', 2); hold on;
scatter([pts_r.lon], [pts_r.lat], 60, 'r', 'filled', 'MarkerEdgeColor', 'k');
legend('平滑滤波', '原始量测点', 'Location', 'best');
title('第1条航迹 叠加对比');
xlabel('经度'); ylabel('纬度'); grid on; axis equal;
