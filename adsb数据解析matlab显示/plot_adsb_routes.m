%% ADS-B 航线地图绘制
% 读取 ADS-B CSV 数据，按飞机分组，在地图上绘制所有航线轨迹
clear; clc; close all;

%% 1. 读取 CSV 文件
csv_file = '2026-04-27 09-30-00.csv';
fprintf('正在读取 %s ...\n', csv_file);

% 使用 detectImportOptions 加速读取
opts = detectImportOptions(csv_file);
opts.VariableNames = {'ICAO', 'Lat', 'Lon', 'Heading', 'Alt', 'Speed', ...
    'Reserved', 'Receiver', 'Type', 'Reg', 'Timestamp', ...
    'Origin', 'Dest', 'Flight', 'Flag1', 'VRate', 'ICAOFlight', 'Flag2', 'Airline'};
opts = setvartype(opts, {'ICAO','Receiver','Type','Reg','Timestamp',...
    'Origin','Dest','Flight','ICAOFlight','Airline'}, 'string');
opts = setvartype(opts, {'Reserved','Flag1','Flag2'}, 'string');  % 空字段按字符串读

data = readtable(csv_file, opts);
n_total = height(data);
fprintf('读取完成：共 %d 条记录\n', n_total);

% 字符串字段的 <missing> 替换为 'N/A'，避免 char() 报错
str_cols = {'ICAO','Receiver','Type','Reg','Timestamp','Origin','Dest','Flight','ICAOFlight','Airline'};
for c = 1:length(str_cols)
    col = str_cols{c};
    data.(col) = fillmissing(data.(col), 'constant', "N/A");
end

%% 2. 数据清洗
% 过滤掉经纬度缺失的记录
valid = ~isnan(data.Lat) & ~isnan(data.Lon);
data = data(valid, :);
fprintf('有效位置记录：%d 条（过滤 %d 条）\n', height(data), n_total - height(data));

%% 3. 按 ICAO 地址分组，按时间排序
[icao_groups, ~, icao_id] = unique(data.ICAO);
n_aircraft = length(icao_groups);
fprintf('共 %d 架飞机\n', n_aircraft);

%% 4. 绘制航线
% 检查是否有 Mapping Toolbox
has_map_toolbox = license('test', 'MAP_Toolbox') && ~isempty(ver('map'));

if has_map_toolbox
    fprintf('使用 Mapping Toolbox (geoplot) 绘制...\n');
    figure('Name', 'ADS-B 航线图', 'Position', [100 100 1200 700]);
    geobasemap satellite;  % 卫星底图，也可以用 'streets' 'topographic' 等
    hold on;

    % 使用 jet 颜色映射为不同飞机分配颜色
    cmap = jet(n_aircraft);

    for i = 1:n_aircraft
        idx = (icao_id == i);
        rows = data(idx, :);
        % 按时间戳排序
        [~, sort_idx] = sort(rows.Timestamp);
        rows = rows(sort_idx, :);

        % 至少要有 2 个点才能画线
        if height(rows) >= 2
            geoplot(rows.Lat, rows.Lon, '-', 'LineWidth', 1.2, 'Color', cmap(i,:));
        else
            geoplot(rows.Lat, rows.Lon, '.', 'MarkerSize', 8, 'Color', cmap(i,:));
        end
    end
    hold off;

    % 设置地图范围（根据数据自动适配）
    lat_pad = 2;
    lon_pad = 2;
    geolimits([min(data.Lat)-lat_pad max(data.Lat)+lat_pad], ...
              [min(data.Lon)-lon_pad max(data.Lon)+lon_pad]);

    title(sprintf('ADS-B 航线图 (%s)', datestr(datetime(data.Timestamp(1)), 'yyyy-mm-dd HH:MM')), ...
        'FontSize', 14);
    subtitle(sprintf('%d 架飞机 | %d 条位置记录', n_aircraft, height(data)), 'FontSize', 11);

else
    % 没有 Mapping Toolbox，使用普通 plot + 简易世界地图
    fprintf('未检测到 Mapping Toolbox，使用简易地图绘制...\n');
    figure('Name', 'ADS-B 航线图', 'Position', [100 100 1200 800]);
    hold on;

    % 简易海岸线（东亚区域）
    coast = load('coast.mat');  % 尝试加载内置海岸线数据
    try
        plot(coast.long, coast.lat, 'k-', 'LineWidth', 0.5);
    catch
        fprintf('注意：未找到 coast.mat，仅绘制航线\n');
    end

    cmap = jet(n_aircraft);
    for i = 1:n_aircraft
        idx = (icao_id == i);
        rows = data(idx, :);
        [~, sort_idx] = sort(rows.Timestamp);
        rows = rows(sort_idx, :);

        if height(rows) >= 2
            plot(rows.Lon, rows.Lat, '-', 'LineWidth', 1.2, 'Color', cmap(i,:));
        else
            plot(rows.Lon, rows.Lat, '.', 'MarkerSize', 8, 'Color', cmap(i,:));
        end
    end
    hold off;

    xlabel('经度 (°E)', 'FontSize', 12);
    ylabel('纬度 (°N)', 'FontSize', 12);
    axis equal;
    grid on;

    xlim([min(data.Lon)-2 max(data.Lon)+2]);
    ylim([min(data.Lat)-2 max(data.Lat)+2]);

    title(sprintf('ADS-B 航线图 (%s)', datestr(datetime(data.Timestamp(1)), 'yyyy-mm-dd HH:MM')), ...
        'FontSize', 14);
    subtitle(sprintf('%d 架飞机 | %d 条位置记录', n_aircraft, height(data)), 'FontSize', 11);
end

%% 5. 输出统计信息
fprintf('\n===== 统计信息 =====\n');
%fprintf('数据时间范围：%s ~ %s\n', ...
  % char(min(data.Timestamp)), char(max(data.Timestamp)));
fprintf('飞机数量：%d\n', n_aircraft);

% 航班统计（排除 N/A）
valid_flights = data.Flight ~= "N/A";
unique_flights = unique(data.Flight(valid_flights));
fprintf('航班数量：%d\n', length(unique_flights));

% 起降机场统计
valid_origin = data.Origin ~= "N/A";
fprintf('起飞机场：%d 个\n', length(unique(data.Origin(valid_origin))));
valid_dest = data.Dest ~= "N/A";
fprintf('目的机场：%d 个\n', length(unique(data.Dest(valid_dest))));

% 航司统计
valid_airline = data.Airline ~= "N/A";
airlines = unique(data.Airline(valid_airline));
fprintf('航空公司：%d 家\n', length(airlines));

% 按点迹数量从大到小排列所有航迹批号
point_counts = accumarray(icao_id, 1);
[~, sort_idx] = sort(point_counts, 'descend');
fprintf('\n===== 航迹批号（按点迹数降序）=====\n');
for i = 1:n_aircraft
    k = sort_idx(i);
    % 获取航班信息
    fidx = find(icao_id == k, 1);
    fprintf('  [%3d] %s  点数: %4d  航班: %-6s  注册号: %-7s  航线: %s->%s\n', ...
        i, char(icao_groups(k)), point_counts(k), ...
        char(data.Flight(fidx)), char(data.Reg(fidx)), ...
        char(data.Origin(fidx)), char(data.Dest(fidx)));
end

fprintf('\n===== 交互式单机航迹查询 =====\n');
fprintf('可用的 ICAO Address (前20个)：\n');
for i = 1:min(20, n_aircraft)
    % 获取该飞机的代表性信息
    idx = find(icao_id == i, 1);
    flight_info = char(data.Flight(idx));
    reg_info = char(data.Reg(idx));
    route_info = sprintf('%s->%s', char(data.Origin(idx)), char(data.Dest(idx)));
    fprintf('  [%2d] %s  航班: %-6s  注册号: %-7s  航线: %s\n', ...
        i, char(icao_groups(i)), flight_info, reg_info, route_info);
end
if n_aircraft > 20
    fprintf('  ... 共 %d 架，请查看上方完整列表或输入任意 ICAO\n', n_aircraft);
end

% 构建 ICAO -> 数据索引映射，加速查询
icao_map = containers.Map('KeyType', 'char', 'ValueType', 'any');
for i = 1:n_aircraft
    icao_map(char(icao_groups(i))) = (icao_id == i);
end

fprintf('\n输入 ICAO Address 查询单机航迹（如 88510D），输入 q 退出：\n');

while true
    query = input('> ', 's');
    if strcmpi(query, 'q') || strcmpi(query, 'quit') || strcmpi(query, 'exit')
        break;
    end

    query_upper = upper(strtrim(query));

    if ~icao_map.isKey(query_upper)
        fprintf('未找到 ICAO: %s，请重新输入（或 q 退出）\n', query_upper);
        continue;
    end

    % 提取该飞机数据
    idx = icao_map(query_upper);
    rows = data(idx, :);
    [~, sort_idx] = sort(rows.Timestamp);
    rows = rows(sort_idx, :);

    % 获取航班信息
    flight_no = char(rows.Flight(1));
    reg = char(rows.Reg(1));
    ac_type = char(rows.Type(1));
    origin = char(rows.Origin(1));
    dest = char(rows.Dest(1));
    airline = char(rows.Airline(1));
    ts_vec = datetime(rows.Timestamp);

    fprintf('找到 %s：%d 个位置点 | %s %s | %s->%s | %s\n', ...
        query_upper, height(rows), ac_type, reg, origin, dest, flight_no);

    %% 6. 单机航迹图
    fig = figure('Name', sprintf('航迹 - %s (%s)', query_upper, flight_no), ...
        'Position', [100 100 1200 700]);

    % 地图范围边距（两个分支共用）
    lat_margin = max(0.5, (max(rows.Lat) - min(rows.Lat)) * 0.3);
    lon_margin = max(0.5, (max(rows.Lon) - min(rows.Lon)) * 0.3);

    if has_map_toolbox
        % 上半部分：地理坐标区（手动指定位置）
        gx = geoaxes('Position', [0.06 0.56 0.90 0.40]);
        geobasemap(gx, 'satellite');
        hold(gx, 'on');
        if height(rows) >= 2
            geoplot(gx, rows.Lat, rows.Lon, '-', 'LineWidth', 2, 'Color', [1 0.3 0]);
            geoplot(gx, rows.Lat(2:end-1), rows.Lon(2:end-1), 'o', ...
                'MarkerSize', 4, 'MarkerFaceColor', [1 0.6 0], 'MarkerEdgeColor', 'none');
            geoplot(gx, rows.Lat(1), rows.Lon(1), 'go', 'MarkerSize', 10, 'MarkerFaceColor', 'g');
            geoplot(gx, rows.Lat(end), rows.Lon(end), 'ro', 'MarkerSize', 10, 'MarkerFaceColor', 'r');
        else
            geoplot(gx, rows.Lat, rows.Lon, 'o', 'MarkerSize', 10, 'Color', [1 0.3 0]);
        end
        hold(gx, 'off');
        geolimits(gx, [min(rows.Lat)-lat_margin max(rows.Lat)+lat_margin], ...
                      [min(rows.Lon)-lon_margin max(rows.Lon)+lon_margin]);
        title(gx, sprintf('%s (%s)   %s -> %s', query_upper, flight_no, origin, dest), 'FontSize', 13);

        % 下半部分左：高度剖面
        ax2 = subplot(2, 2, 3);
        stairs(ts_vec, rows.Alt, 'b-', 'LineWidth', 1.5);
        xlabel('时间 (UTC)'); ylabel('高度 (ft)');
        title('高度剖面');
        grid on;
        ax2.XAxis.TickLabelFormat = 'HH:mm';

        % 下半部分右：地速剖面
        ax3 = subplot(2, 2, 4);
        plot(ts_vec, rows.Speed, 'r-', 'LineWidth', 1.5);
        xlabel('时间 (UTC)'); ylabel('地速 (kt)');
        title('地速剖面');
        grid on;
        ax3.XAxis.TickLabelFormat = 'HH:mm';

    else
        % ---- 无 Mapping Toolbox：纯 subplot 布局 ----
        ax1 = subplot(2, 2, [1 2]);
        try
            coast = load('coast.mat');
            plot(coast.long, coast.lat, 'k-', 'LineWidth', 0.5);
        catch
        end
        hold on;
        if height(rows) >= 2
            plot(rows.Lon, rows.Lat, '-', 'LineWidth', 2, 'Color', [1 0.3 0]);
            plot(rows.Lon(2:end-1), rows.Lat(2:end-1), 'o', ...
                'MarkerSize', 4, 'MarkerFaceColor', [1 0.6 0], 'MarkerEdgeColor', 'none');
            plot(rows.Lon(1), rows.Lat(1), 'go', 'MarkerSize', 10, 'MarkerFaceColor', 'g');
            plot(rows.Lon(end), rows.Lat(end), 'ro', 'MarkerSize', 10, 'MarkerFaceColor', 'r');
        else
            plot(rows.Lon, rows.Lat, 'o', 'MarkerSize', 10, 'Color', [1 0.3 0]);
        end
        hold off;
        axis equal; grid on;
        xlabel('经度 (°E)'); ylabel('纬度 (°N)');
        xlim([min(rows.Lon)-lon_margin max(rows.Lon)+lon_margin]);
        ylim([min(rows.Lat)-lat_margin max(rows.Lat)+lat_margin]);
        title(sprintf('%s (%s)   %s -> %s', query_upper, flight_no, origin, dest), 'FontSize', 13);

        ax2 = subplot(2, 2, 3);
        stairs(ts_vec, rows.Alt, 'b-', 'LineWidth', 1.5);
        xlabel('时间 (UTC)'); ylabel('高度 (ft)');
        title('高度剖面');
        grid on;
        ax2.XAxis.TickLabelFormat = 'HH:mm';

        ax3 = subplot(2, 2, 4);
        plot(ts_vec, rows.Speed, 'r-', 'LineWidth', 1.5);
        xlabel('时间 (UTC)'); ylabel('地速 (kt)');
        title('地速剖面');
        grid on;
        ax3.XAxis.TickLabelFormat = 'HH:mm';
    end

    % 总标题
    sgtitle(sprintf('ICAO: %s | 航班: %s | %s | %s→%s | 注册号: %s | 航司: %s', ...
        query_upper, flight_no, ac_type, origin, dest, reg, airline), ...
        'FontSize', 12, 'FontWeight', 'bold');

    fprintf('航迹图已绘制。继续查询或输入 q 退出。\n\n');
end

fprintf('脚本执行完毕。\n');
