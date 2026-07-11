%% ==================== 实验一 B：匹配滤波与目标测距 ====================
clear; clc; close all;

%% 1. 读取数据（发射 + 回波 IQ两路）
tx_data = load('transmit_data.txt');
I_tx = tx_data(:,1);
Q_tx = tx_data(:,2);
s_tx = I_tx + 1j*Q_tx;
N_tx = length(s_tx);

rx_data = load('echo_data.txt');
I_rx = rx_data(:,1);
Q_rx = rx_data(:,2);
s_rx = I_rx + 1j*Q_rx;
N_rx = length(s_rx);

%% 2. 系统参数
Fs = 40e6;          % 采样率 40MHz
c = 3e8;            % 光速
t_tx = (0:N_tx-1)/Fs;

%% ==================== B1：发射信号 时域 + 频谱 ====================
figure(1);
subplot(2,1,1);
plot(t_tx*1e6, real(s_tx), 'LineWidth',1);
xlabel('时间 / \mus'); ylabel('实部');
title('B1：发射信号时域波形'); grid on;

NFFT1 = N_tx;
f1 = Fs/2 * linspace(-1,1,NFFT1);
S_TX = fftshift(fft(s_tx));
S_amp = abs(S_TX) / max(abs(S_TX));
S_dB = 20*log10(S_amp + eps);

subplot(2,1,2);
plot(f1/1e6, S_dB, 'LineWidth',1);
xlabel('频率 / MHz'); ylabel('幅度 / dB');
title('B1：发射信号频谱（dB归一化）');
xlim([-20,20]); ylim([-60,0]); grid on;

%% ==================== B2：参考函数（系统函数）频谱 ====================
% 按指导书：发射信号补零到与回波信号等长
N_fft = N_rx;
s_tx_pad = [s_tx; zeros(N_fft - N_tx, 1)];
S_TX_PAD = fft(s_tx_pad);
H = conj(S_TX_PAD);       % 系统函数（匹配滤波器）

% 参考函数频谱
figure(2);
f2 = Fs/2 * linspace(-1,1,N_fft);
H_shift = fftshift(H);
H_amp = abs(H_shift) / max(abs(H_shift));
H_dB = 20*log10(H_amp + eps);
plot(f2/1e6, H_dB, 'LineWidth',1);
xlabel('频率 / MHz'); ylabel('幅度 / dB');
title('B2：参考函数频谱（dB归一化）');
xlim([-20,20]); ylim([-60,0]); grid on;

%% ==================== B3：匹配滤波 + 目标检测 ====================
% 回波补零
s_rx_pad = [s_rx; zeros(N_fft - N_rx, 1)];
S_RX = fft(s_rx_pad);

% 频域相乘 → 匹配滤波
s_out = ifft(S_RX .* H);
s_out = fftshift(s_out);
amp_out = abs(s_out);

% 时间轴（偶数长度修正零点偏移）
if mod(N_fft, 2) == 0
    t_out = (-N_fft/2 : N_fft/2-1) / Fs;
else
    t_out = (-(N_fft-1)/2 : (N_fft-1)/2) / Fs;
end

% dB归一化
amp_dB = 20*log10(amp_out / max(amp_out) + eps);

% ---- 目标检测：近距范围内取幅度最高的3个采样点 ----
max_target_time = 0.5e-6;
near_mask = t_out >= 0 & t_out <= max_target_time;
amp_near = amp_out;
amp_near(~near_mask) = 0;
[~, sort_idx] = sort(amp_near, 'descend');
top_idx = sort(sort_idx(1:3));
peak_times = t_out(top_idx);
pks = amp_out(top_idx);

% ---- 主旁瓣比 & 主瓣宽度（基于发射信号线性自相关）----
N_auto = 2 * N_tx - 1;
s_auto_pad = [s_tx; zeros(N_auto - N_tx, 1)];
S_auto = fft(s_auto_pad);
R_tx = fftshift(ifft(S_auto .* conj(S_auto)));
amp_R = abs(R_tx);
t_R = (-(N_auto-1)/2 : (N_auto-1)/2) / Fs;

[main_peak, main_idx] = max(amp_R);

% 主瓣宽度（-4dB）
level_4dB = main_peak * 10^(-4/20);
left_idx = find(amp_R(1:main_idx) < level_4dB, 1, 'last');
right_idx = main_idx + find(amp_R(main_idx:end) < level_4dB, 1, 'first') - 1;
if isempty(left_idx), left_idx = 1; end
if isempty(right_idx), right_idx = N_auto; end
main_width = (right_idx - left_idx) / Fs;

% 旁瓣电平（排除主瓣 1.5倍宽度）
guard_s = round(main_width * Fs * 1.5);
sidelobe_mask = true(N_auto, 1);
sidelobe_mask(main_idx-guard_s : main_idx+guard_s) = false;
sidelobe_mask(t_R <= 0) = false;
sidelobe_level = max(amp_R(sidelobe_mask));
PSLR = 20*log10(sidelobe_level / main_peak);

% ---- 目标距离 ----
delta_t = diff(peak_times);
delta_R = c * delta_t / 2;

%% ---- B3 画图：匹配滤波输出 ----
figure(3);
plot(t_out*1e6, amp_dB, 'LineWidth', 1.5);
hold on;
xline(0, 'r--', 'LineWidth', 1);
xlabel('时间 / \mus'); ylabel('幅度 / dB');
title(sprintf('B3：匹配滤波输出'));
xlim([0, 1.5]); ylim([-60, 5]); grid on;
hold off;

%% ==================== 结果输出 ====================
fprintf('\n==================================================\n');
fprintf('                实验一 B 项 结果\n');
fprintf('==================================================\n');
fprintf('主旁瓣比 PSLR = %.2f dB\n', PSLR);
fprintf('主瓣宽度 (-4dB) = %.2f us (%.1f 个采样点)\n', main_width*1e6, main_width*Fs);
fprintf('\n检测到的目标数量：%d\n', length(peak_times));
for i = 1:length(peak_times)
    fprintf('目标 %d：时间 = %.4f us, 距离 = %.2f m, 幅度 = %.1f (%.1f dB)\n', ...
        i, peak_times(i)*1e6, c*peak_times(i)/2, pks(i), 20*log10(pks(i)/max(amp_out)+eps));
end
fprintf('\n目标间距离：\n');
for i = 1:length(delta_t)
    fprintf('  目标%d -> 目标%d：%.2f m\n', i, i+1, delta_R(i));
end
fprintf('==================================================\n');
