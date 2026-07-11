%% 实验二 连续波雷达测速（同时处理快慢两组数据）
clear; clc; close all;

%% ===================== 1. 实验固定参数 =====================
fc = 24.15e9;          % 发射频率 24.15GHz
c = 3e8;               % 光速
lambda = c / fc;        % 波长
Fs = 10000;            % 采样率 10kHz
N_total = 2048;        % 数据点数
t_total = (0:N_total-1)/Fs;

%% ===================== 2. 定义两组数据文件 =====================
files = {
    '测速实验slowy.txt', '慢速目标';
    '测速实验fasty.txt', '快速目标';
};

%% ===================== 3. 循环处理每组数据 =====================
for k = 1:size(files,1)
    fname = files{k,1};
    label = files{k,2};

    % 读取数据
    data = load(fname);
    I = data(:,1);
    Q = data(:,2);
    s = I + 1j*Q;

    % 截取 + 去除直流分量
    idx_start = 500;
    idx_end   = 1523;
    s_cut = s(idx_start:idx_end);
    s_cut = s_cut - mean(s_cut);
    t_cut = t_total(idx_start:idx_end);

    % ---- 画 I/Q 时域图 ----
    figure('Name', ['I/Q时域_', label]);
    subplot(2,1,1);
    plot(t_cut, real(s_cut));
    xlabel('时间/s'); ylabel('I路');
    title(['I路时域（', label, '，已去直流）']); grid on;

    subplot(2,1,2);
    plot(t_cut, imag(s_cut));
    xlabel('时间/s'); ylabel('Q路');
    title(['Q路时域（', label, '，已去直流）']); grid on;

    % ---- 原始幅度频谱 ----
    NFFT = length(s_cut);
    f = Fs/2 * linspace(-1, 1, NFFT);
    S = fftshift(fft(s_cut));
    amp = abs(S);

    figure('Name', ['多普勒频谱_', label]);
    plot(f, amp);
    xlabel('频率 / Hz');
    ylabel('幅度（原始）');
    title(['回波信号频谱（', label, '，未归一化 + 已去直流）']);
    grid on;
    xlim([-1000 1000]);

    % ---- 找峰值（自动避开直流）----
    center = round(NFFT/2);
    left  = 1 : center-10;
    right = center+10 : NFFT;

    [~, idxL] = max(abs(S(left)));
    [~, idxR] = max(abs(S(right)));

    posL = left(idxL);
    posR = right(idxR);

    if abs(S(posR)) > abs(S(posL))
        peak_pos = posR;
    else
        peak_pos = posL;
    end

    fd(k) = f(peak_pos);
    v(k) = fd(k) * lambda / 2;
end

%% ===================== 4. 输出两组结果对比 =====================
fprintf('========================================\n');
fprintf('       实验二 连续波雷达测速结果\n');
fprintf('========================================\n');
for k = 1:size(files,1)
    fprintf('【%s】\n', files{k,2});
    fprintf('  多普勒频率 fd = %.2f Hz\n', fd(k));
    fprintf('  目标速度     v = %.2f m/s\n', v(k));
    if v(k) > 0
        fprintf('  方向：远离雷达\n');
    else
        fprintf('  方向：靠近雷达\n');
    end
end
fprintf('========================================\n');
fprintf('两组速度差 = %.2f m/s\n', abs(v(2)-v(1)));
fprintf('========================================\n');
