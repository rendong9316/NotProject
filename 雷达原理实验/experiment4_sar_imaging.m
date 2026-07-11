% ============================================================
% 实验四：微波成像仿真与处理（RD算法）- 单点目标简化版
% 基于《雷达原理实验指导书》实验四
% ============================================================
% 公式来源：指导书"二、实验原理"
%
% 公式(1) 发射信号:
%   s(t^) = rect(t^/Tp) * exp(j*2π*fc*t) * exp(jπ*gamma*t^2)
%
% 公式(2) 回波信号:
%   s(t^) = A * rect((t^ - 2R/c)/Tp) * exp(-j*4π*R/λ) * exp(jπ*gamma*(t^ - 2R/c)^2)
%
% 公式(4) 参考信号:
%   s_ref(t^) = A * rect((t^ - 2R_ref/c)/Tp) * exp(jπ*gamma*(t^ - 2R_ref/c)^2)
%
% 公式(5) 距离压缩:
%   s_r(t) = IFFT[ FFT(s(t)) .* FFT*(s_ref(t)) ]
%
% 公式(6) 方位压缩后信号:
%   s(t^, t_m) = sinc_r分量 * exp(-j*4π*R0/λ + jπ*k_d*t_m^2)
%
% 公式(7) 方位匹配滤波:
%   sref_a(t_m) = exp(jπ*k_d*t_m^2)
%
% 公式(8) 方位压缩:
%   s(t^, t_m) = IFFT{ FFT[s(t^, t_m)] .* FFT*[sref_a(t_m)] }
% ============================================================

clear; close all; clc;

%% ================================================================
%% 步骤0：参数设置（表1）
%% ================================================================
c = 3e8;                    % 光速 (m/s)
fc = 8850e6;               % 载波频率 (Hz)，公式(1)中的 fc
Tp = 10e-6;                % 脉冲宽度 (s)，公式(1)(2)中的 Tp
B = 50e6;                  % 带宽 (Hz)
gamma = B / Tp;            % 调频率 (Hz/s)，公式(1)(2)中的 gamma
lambda_c = c / fc;         % 波长 (m)，公式(2)中的 λ

Fs = 64e6;                 % 采样频率 (Hz)
Ts = 1 / Fs;              % 采样间隔 (s)

R0 = 6.3e3;               % 中心斜距 (m)
v = 115;                   % 平台速度 (m/s)
PRF = 500;                 % PRF (Hz)
PRI = 1 / PRF;             % PRI (s)
D_az = 2;                  % 方位向天线孔径 (m)

fprintf('========== 参数（表1）==========\n');
fprintf('fc=%.2fGHz, Tp=%.1fus, B=%.1fMHz, gamma=%.2eHz/s\n', fc/1e9, Tp*1e6, B/1e6, gamma);
fprintf('R0=%.0fm, v=%.0fm/s, PRF=%.0fHz, D_az=%.1fm\n', R0, v, PRF, D_az);
fprintf('距离分辨率=%.2fm, 方位分辨率=%.2fm\n', c/(2*B), D_az/2);

%% ================================================================
%% 步骤1：快时间轴与距离轴
%% ================================================================
% 关键：将 R0 放在观察窗口中心附近，避免 FFT 缠绕问题
% 观察窗口范围 [R0 - N_obs/2*dR, R0 + N_obs/2*dR]
% 无模糊条件：c*Tp/2 = 1500m > N_obs/2*dR = 1200m
N_obs = 1024;              % 距离向采样点数
dR = c * Ts / 2;          % 每个距离门对应的距离 (m) ≈ 2.34m

% 快时间轴：从 2*(R0-R_margin)/c 开始，使 R0 落在观察窗口中心
R_margin = 1200;            % 观察窗口半宽 (m)
t_start = 2 * (R0 - R_margin) / c;  % ≈ 34μs，使 R0 对应 t_fast ≈ 窗口中心
t_fast = t_start + (0 : N_obs-1) * Ts;  % 快时间轴 (s)

% 距离轴：R = c*t/2，直接从 t_start 开始映射
% R_axis(n) = c/2 * t_fast(n) = R0 - R_margin + (n-1)*dR
% R_axis(1) = R0 - R_margin = 5100m
% R_axis(N/2+1) = R0 = 6300m（窗口中心）
R_axis = c * t_fast / 2;

fprintf('\n观察窗口: %.0f ~ %.0f m（中心=%.0f m）\n', R_axis(1), R_axis(end), R_axis(N_obs/2+1));

%% ================================================================
%% 步骤2：发射信号（公式1的基带形式）
%% ================================================================
% 公式(1): s(t^) = rect(t^/Tp) * exp(jπ*gamma*t^2)
% 省略载频 exp(j*2π*fc*t)，匹配滤波中共轭相消
rect_signal = ones(1, N_obs);
s_tx = rect_signal .* exp(1j * pi * gamma * t_fast.^2);

fprintf('\n公式(1): s(t^) = rect(t^/Tp) * exp(jπ*gamma*t^2)\n');

%% ================================================================
%% 步骤3：单点目标
%% ================================================================
target = [R0, 0, 1.0];  % [R_x, R_y, 幅度]
fprintf('单点目标: R=%.0fm, Y=%.0fm\n', target(1), target(2));

%% ================================================================
%% 步骤4：构造回波数据（公式2）
%% ================================================================
% 公式(2): s(t^) = A*rect((t^-2R/c)/Tp)*exp(-j*4π*R/λ)*exp(jπ*gamma*(t^-2R/c)^2)
% 方位向慢时间采样
Na = 512;                              % 方位脉冲数
t_slow = (0:Na-1)*PRI;              % 慢时间轴 (s)
s_az = (t_slow - t_slow(end)/2)*v;  % 方位位置 (m)

echo_data = zeros(Na, N_obs);

for ia = 1:Na
    y_ia = s_az(ia);

    % 斜距 R = sqrt(R_x^2 + (R_y - y)^2)
    R_ia = sqrt(target(1)^2 + (target(2) - y_ia)^2);

    % 双程时延 tau = 2*R/c
    tau = 2 * R_ia / c;

    % 在快时间轴上的索引（1-based）
    % t_fast(n) = (n-1)*Ts, 令 t_fast(idx) = tau, 得 idx = tau/Ts + 1
    idx = round(tau / Ts) + 1;  % tau 对应的索引
    idx = max(1, min(N_obs, idx));  % 边界裁剪

    % 方位向多普勒相位: exp(-j*4π*R/λ)
    phi_az = -4 * pi * R_ia / lambda_c;

    % chirp相位: exp(jπ*gamma*(t^ - 2R/c)^2)
    t_delayed = t_fast - tau;
    valid = (t_delayed >= 0) & (t_delayed <= Tp);
    echo_line = target(3) * exp(1j*phi_az) .* exp(1j*pi*gamma*t_delayed.^2) .* valid;

    % 映射到回波矩阵：echo_line 的采样点与快时间采样点一一对应
    % echo_line(n) 对应 echo_data(ia, n)，但 echo_line 的有效部分可能从中间开始
    % 由于 idx 是起点，有效范围是 idx : min(N_obs, idx+N_obs-1)
    i_start = idx;
    i_end = min(idx + N_obs - 1, N_obs);
    n_copy = i_end - i_start + 1;

    if n_copy > 0
        echo_data(ia, i_start:i_end) = echo_line(1:n_copy);
    end
end

fprintf('\n公式(2): s(t^) = A*rect((t^-2R/c)/Tp)*exp(-j*4π*R/λ)*exp(jπ*gamma*(t^-2R/c)^2)\n');
fprintf('回波矩阵: %d x %d\n', Na, N_obs);
fprintf('方位=0处tau=%.2fus\n', 2*R0/c*1e6);

%% ================================================================
%% 步骤5：距离维脉冲压缩（公式4 + 公式5）
%% ================================================================
% 公式(4): s_ref(t^) = rect((t^-2R_ref/c)/Tp) * exp(jπ*gamma*(t^-2R_ref/c)^2)
% 用 R_ref = R0 使目标位于观察窗口中心，f_b = 0 时目标在 R_axis(N/2+1) = 6300m
% 但为避免 FFT 缠绕（f_b=0 可能分裂到两端），改为 R_ref = R0 + 300m，
% 使 f_b ≠ 0 且目标落在观察窗口内
R_ref = R0 + 300;  % 参考距离 (m)

h_ref = rect_signal .* exp(1j*pi*gamma*(t_fast - 2*R_ref/c).^2);
H_ref = fft(h_ref, N_obs*2);  % 补零FFT

fprintf('\n公式(4): s_ref(t^) = rect((t^-2R_ref/c)/Tp)*exp(jπ*gamma*(t^-2R_ref/c)^2)\n');

echo_rc = zeros(Na, N_obs*2);
for ia = 1:Na
    E = fft(echo_data(ia, :), N_obs*2);
    E_rc = E .* conj(H_ref);      % 公式(5): FFT(s) .* FFT*(s_ref)
    echo_rc(ia, :) = ifft(E_rc);
end
% 截取主值区间 [N_obs/2+1 : N_obs/2+N_obs] = [513 : 1536]
% 此时第 k 个输出点对应 R = R_ref + (k - N_obs/2 - 1) * dR
echo_rc = echo_rc(:, N_obs/2+1 : N_obs/2+N_obs);  % (512 x 1024)

fprintf('公式(5): s_r(t) = IFFT[ FFT(s(t)) .* FFT*(s_ref(t)) ]\n');
fprintf('距离压缩完成（不做RCMC），截取主值区间 %d 点\n', N_obs);

%% ================================================================
%% 步骤6：方位维脉冲压缩（公式6 + 公式7 + 公式8）
%% ================================================================
% 方位调频率: k_d = 2*v^2/(lambda*R0)
k_d = 2 * v^2 / (lambda_c * R0);

% 公式(8): FFT(s) .* FFT*(sref_a) 在频域做方位压缩
% sref_a(t_m) = exp(jπ*k_d*t_m^2)，匹配滤波：乘 FFT*(sref_a) = FFT(sref_a 的共轭)
% = conj(FFT(sref_a))，由于 sref_a 是实偶函数，FFT(sref_a) 为实数
% 共轭匹配：H_az = conj(FFT(sref_a)) = conj(fft(exp(1j*pi*k_d*t_slow.^2)))
H_az_fft = conj(fft(exp(1j * pi * k_d * t_slow.^2))).';  % (512x1) 列向量

fprintf('\n公式(6): sinc_r分量 * exp(-j*4π*R0/λ + jπ*k_d*t_m^2)\n');
fprintf('  k_d = 2*v^2/(lambda*R0) = %.4f Hz/s\n', k_d);
fprintf('公式(7): sref_a(t_m) = exp(jπ*k_d*t_m^2)\n');

sar_image = zeros(Na, N_obs);
for ir = 1:N_obs
    col = echo_rc(:, ir);
    E_az = fft(col);                % FFT in azimuth (512-point)
    E_az_rc = E_az .* H_az_fft;     % 公式(8): FFT(s) .* FFT*(sref_a)
    sar_image(:, ir) = ifft(E_az_rc);
end
sar_image = fftshift(sar_image, 1);  % 方位谱中心化

fprintf('公式(8): s(t^,t_m) = IFFT{ FFT[s(t^,t_m)] .* FFT*[sref_a(t_m)] }\n');
fprintf('方位压缩完成\n');

%% ================================================================
%% 步骤7：成像结果显示
%% ================================================================
sar_db = 20*log10(abs(sar_image) + 1e-10);
sar_db = sar_db - max(sar_db(:));

figure('Position', [100, 100, 1200, 800]);

% 子图1: 发射信号
subplot(2, 3, 1);
plot(t_fast*1e6, abs(s_tx), 'b-', 'LineWidth', 1);
xlabel('Fast time (us)'); ylabel('|s|');
title('Transmitted signal (Eq.1)'); grid on;

% 子图2: 方位=0处原始回波
[~, ia0] = min(abs(s_az));
subplot(2, 3, 2);
plot(R_axis, abs(echo_data(ia0,:)), 'b-', 'LineWidth', 0.5);
xlabel('Range (m)'); ylabel('|s|');
title('Raw echo at azimuth=0 (Eq.2)'); grid on;

% 子图3: 距离压缩后
subplot(2, 3, 3);
imagesc(R_axis, s_az, 20*log10(abs(echo_rc)+1e-10)); colorbar;
xlabel('Range (m)'); ylabel('Azimuth (m)');
title('After range compression (Eq.5)'); axis xy;
caxis([max(sar_db(:))-40, max(sar_db(:))]); colormap jet;
hold on; plot(target(1), target(2), 'w+', 'MarkerSize', 12, 'LineWidth', 2);

% 子图4: SAR图像
subplot(2, 3, 4);
imagesc(R_axis, s_az, sar_db); colorbar;
xlabel('Range (m)'); ylabel('Azimuth (m)');
title('RD algorithm SAR image'); axis xy;
caxis([-40, 0]); colormap jet;
hold on; plot(target(1), target(2), 'w+', 'MarkerSize', 12, 'LineWidth', 2);

% 子图5: 一维距离像（方位=0处）
subplot(2, 3, 5);
rp = abs(sar_image(ia0, :)); rp = rp / max(rp);
plot(R_axis, rp, 'b-', 'LineWidth', 1.2);
xlabel('Range (m)'); ylabel('Normalized amplitude');
title('Range profile at azimuth=0'); grid on;
xlim([target(1)-100, target(1)+100]);

% 子图6: 一维方位像（距离=R0处）
[~, ir0] = min(abs(R_axis - target(1)));
subplot(2, 3, 6);
rp_az = abs(sar_image(:, ir0)); rp_az = rp_az / max(rp_az);
plot(s_az, rp_az, 'r-', 'LineWidth', 1.2);
xlabel('Azimuth (m)'); ylabel('Normalized amplitude');
title('Azimuth profile at range~R0'); grid on;
xlim([target(2)-50, target(2)+50]);

%% ================================================================
%% 步骤8：分辨率分析
%% ================================================================
fprintf('\n========== 分辨率分析 ==========\n');
fprintf('理论距离分辨率: c/(2B) = %.2f m\n', c/(2*B));
fprintf('理论方位分辨率: D_az/2 = %.2f m\n', D_az/2);

% 距离向-3dB宽度
rp = abs(sar_image(ia0, :)) / max(abs(sar_image(ia0, :)));
[pk, pk_idx] = max(rp);
half_val = pk / sqrt(2);
left = find(rp(1:pk_idx) <= half_val, 1, 'last');
right = find(rp(pk_idx:end) <= half_val, 1, 'first') + pk_idx - 1;
if ~isempty(left) && ~isempty(right)
    fwhm_r = (right - left) * dR;
    fprintf('实测距离向-3dB宽度: %.2f m\n', fwhm_r);
end

% 方位向-3dB宽度
rp_az = abs(sar_image(:, ir0)) / max(abs(sar_image(:, ir0)));
[pk_az, pk_az_idx] = max(rp_az);
half_az = pk_az / sqrt(2);
az_l = find(rp_az(1:pk_az_idx) <= half_az, 1, 'last');
az_r = find(rp_az(pk_az_idx:end) <= half_az, 1, 'first') + pk_az_idx - 1;
if ~isempty(az_l) && ~isempty(az_r)
    fwhm_az = (az_r - az_l) * (s_az(2) - s_az(1));
    fprintf('实测方位向-3dB宽度: %.2f m\n', fwhm_az);
end

% 峰值位置误差
[sar_pk, lin_idx] = max(abs(sar_image(:)));
[pk_az, pk_rng] = ind2sub([Na, N_obs], lin_idx);
peak_R = R_axis(pk_rng); peak_az = s_az(pk_az);
fprintf('峰值位置: (%.1f, %.1f)m vs 真值: (%.0f, %.0f)m\n', peak_R, peak_az, target(1), target(2));
fprintf('定位误差: (%.2f, %.2f)m\n', peak_R-target(1), peak_az-target(2));

fprintf('\n完成！\n');
