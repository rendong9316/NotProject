%% ==================== 实验一 A部分：构造LFM + 双边频谱 + 匹配滤波 ====================
clear; clc; close all;

%% ========== A1：构造LFM信号，画 时域 + 双边频谱（dB归一化） ==========
B = 10e6;          % 带宽 10MHz
T = 10e-6;         % 时宽 10us
Fs = 40e6;         % 采样率 40MHz
mu = B/T;          % 调频率
%N = round(T*Fs);   % 采样点数
N = 1024;   % 采样点数
t = linspace(-T/2, T/2, N);

% 生成LFM复信号
s = exp(1j*pi*mu*t.^2);

% 时域图
figure(1);
subplot(2,1,1);
plot(t*1e6, real(s), 'LineWidth',1);
xlabel('时间 / \mus');
ylabel('实部');
title('A1：LFM信号 时域波形');
grid on;

% 双边频谱 + dB归一化
NFFT = N;
f = Fs/2 * linspace(-1, 1, NFFT);
S = fftshift(fft(s, NFFT));
S_amp = abs(S)/max(abs(S));       % 幅度归一化
S_dB = 20*log10(S_amp);           % 转 dB

subplot(2,1,2);
plot(f/1e6, S_dB, 'LineWidth',1);
xlabel('频率 / MHz');
ylabel('幅度 / dB');
title('A1：LFM信号 双边频谱（dB归一化）');
xlim([-20, 20]);
ylim([-60, 0]);  % 让频谱图更标准
grid on;

%% ========== A2：构造参考函数，画 参考函数双边频谱（dB归一化） ==========
H = conj(fft(s, NFFT));   % 匹配滤波：频域共轭
H_shift = fftshift(H);
H_amp = abs(H_shift)/max(abs(H_shift));
H_dB = 20*log10(H_amp);   % dB归一化

figure(2);
plot(f/1e6, H_dB, 'LineWidth',1);
xlabel('频率 / MHz');
ylabel('幅度 / dB');
title('A2：匹配参考函数 双边频谱（dB归一化）');
xlim([-20, 20]);
ylim([-60, 0]);
grid on;

%% ========== A3：匹配滤波 + 与sinc对比 + 主旁瓣比 ==========
s_out = ifft(fft(s,NFFT) .* H);
s_out = fftshift(s_out);
t_out = linspace(-T/2, T/2, length(s_out));

% sinc参考
sinc_ref = sinc( B * t_out ); 

% dB归一化
amp_out = 20*log10(abs(s_out)/max(abs(s_out)));
amp_sinc = 20*log10(abs(sinc_ref)/max(abs(sinc_ref)));

figure(3);
plot(t_out*1e6, amp_out, 'b', 'LineWidth',1.5);
hold on;
plot(t_out*1e6, amp_sinc, 'r--', 'LineWidth',1.5);
xlim([-1, 1]);
xlabel('时间 / \mus');
ylabel('幅度 / dB');
title('A3：匹配滤波输出（与sinc对比）');
legend('脉压输出','sinc函数');
grid on;

% 计算主旁瓣比
peak = max(abs(s_out));
sidelobe = max(abs(s_out(abs(t_out) > 0.1e-6)));
PSLR = 20*log10(sidelobe/peak);

fprintf('====================\n');
fprintf('A部分结果\n');
fprintf('主旁瓣比 = %.2f dB\n', PSLR);
fprintf('主瓣宽度 ≈ %.2f us\n', 1/(B)/1e6);
fprintf('====================\n');

