%% 实验三 调频连续波雷达测距（同时处理静态+动态两组数据）
clear; clc; close all;

%% ===================== 1. 实验固定参数 =====================
fc = 24.15e9;          % 发射频率 24.15GHz
c = 3e8;               % 光速
Fs = 10000;            % 采样率 10kHz
N = 2048;              % 数据点数
t = (0:N-1)/Fs;        % 时间轴

% FMCW 测距参数（依据指导书实验三 2.3 节）
% 雷达周期 32ms = CW(22ms) + FMCW(10ms)，其中 FMCW 段 UP(5ms) + DOWN(5ms)
T_fmcw = 10e-3;        % FMCW 三角波调制周期 10ms（UP+DOWN总时长，用于测距公式）
B = 250e6;             % 扫频带宽 250MHz

%% ===================== 2. 定义两组数据文件 =====================
files = {
    '测距实验range_still.txt', '静态目标';
    '测距实验range_move.txt',  '动态目标';
};

%% ===================== 3. 循环处理每组数据 =====================
for k = 1:size(files,1)
    fname = files{k,1};
    label = files{k,2};

    % 读取IQ数据
    data = load(fname);
    I = data(:,1);
    Q = data(:,2);
    s = I + 1j*Q;          % 复信号

    % 截取数据 + 去除直流分量
    s_cut = s(500:1523);
    s_cut = s_cut - mean(s_cut);
    t_cut = t(500:1523);

    % ---- 画I/Q时域波形 ----
    figure('Name', ['FMCW测距_IQ时域_', label]);
    subplot(2,1,1);
    plot(t_cut, real(s_cut), 'LineWidth',1);
    xlabel('时间 / s'); ylabel('I 路幅度');
    title(['I路时域波形（', label, '，已去直流）']); grid on;

    subplot(2,1,2);
    plot(t_cut, imag(s_cut), 'LineWidth',1);
    xlabel('时间 / s'); ylabel('Q 路幅度');
    title(['Q路时域波形（', label, '，已去直流）']); grid on;

    % ---- 原始幅度频谱 ----
    NFFT = length(s_cut);
    f = Fs/2 * linspace(-1, 1, NFFT);
    S = fftshift(fft(s_cut));
    amp = abs(S);

    figure('Name', ['FMCW测距_差频频谱_', label]);
    plot(f, amp, 'LineWidth',1.2);
    xlabel('差频频率 / Hz'); ylabel('原始幅度');
    title(['FMCW差频频谱（', label, '，已去直流）']);
    grid on;
    xlim([-2000 2000]);

    % ---- 提取差频 fb（分别取正负半轴峰值）----
    center = round(NFFT/2);
    left  = 1 : center - 5;
    right = center + 5 : NFFT;

    [~, idxL] = max(abs(S(left)));
    [~, idxR] = max(abs(S(right)));

    fb1 = f(left(idxL));
    fb2 = f(right(idxR));

    fb(k) = (abs(fb1) + abs(fb2)) / 2;

    % ---- 计算目标距离 ----
    % R = fb_avg * c * T_fmcw / (4 * B)，其中 T_fmcw = 10ms 为三角波全周期
    R(k) = fb(k) * c * T_fmcw / (4 * B);
end

%% ===================== 4. 输出两组结果对比 =====================
fprintf('========================================\n');
fprintf('    实验三 调频法测距结果（去直流）\n');
fprintf('    T_fmcw = 10 ms, B = 250 MHz\n');
fprintf('========================================\n');
for k = 1:size(files,1)
    fprintf('【%s】\n', files{k,2});
    fprintf('  差频 fb = %.2f Hz\n', fb(k));
    fprintf('  目标距离 R = %.3f 米\n', R(k));
end
fprintf('========================================\n');
fprintf('两组距离差 = %.3f 米\n', abs(R(2)-R(1)));
fprintf('========================================\n');
