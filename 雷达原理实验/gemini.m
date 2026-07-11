%% SAR成像仿真实验 —— 正侧视RD成像 (基础与优化对比版)
% 实验四：微波成像仿真与处理实验
% 雷达原理课程
% 功能：仿真点目标回波，完成距离/方位脉压。包含未加窗的基础成像与加窗优化的对比分析。

clear; close all; clc;

%% ==================== 1. 系统参数设置 ====================
c = 3e8;                      % 光速 (m/s)
fc = 8850e6;                  % 中心频率 8850 MHz
lambda = c / fc;              % 波长 (m)

R0 = 6300;                    % 场景中心斜距 6.3 km
Tp = 10e-6;                   % 脉冲宽度 10 us
B = 50e6;                     % 发射带宽 50 MHz
D_az = 2;                     % 方位向天线孔径 2 m
D_el = 0.5;                   % 俯仰向天线孔径 0.5 m (备用)
v = 115;                      % 平台运动速度 115 m/s
PRF = 500;                    % 脉冲重复频率 500 Hz
Fs = 64e6;                    % 采样频率 64 MHz

%% ==================== 2. 推导参数计算 ====================
gamma_chirp = B / Tp;          % 线性调频率 (Hz/s)
rho_r = c / (2 * B);           % 距离向理论分辨率 (m)
rho_a = D_az / 2;              % 方位向理论分辨率 (m)

theta_az = lambda / D_az;      % 方位向波束宽度 (rad)
Ls = theta_az * R0;            % 合成孔径长度 (m)
Ta = Ls / v;                   % 合成孔径时间 (s)
Bd = 2 * v / D_az;             % 多普勒带宽 (Hz)
kd = -2 * v^2 / (lambda * R0); % 多普勒调频率 (Hz/s)
fdc = 0;                       % 正侧视多普勒中心频率

fprintf('========== 系统参数 ==========\n');
fprintf('波长 lambda = %.4f m\n', lambda);
fprintf('距离向理论分辨率 rho_r = %.2f m\n', rho_r);
fprintf('方位向理论分辨率 rho_a = %.2f m\n', rho_a);
fprintf('合成孔径长度 Ls = %.2f m\n', Ls);
fprintf('合成孔径时间 Ta = %.4f s\n', Ta);

%% ==================== 3. 采样参数与坐标轴 ====================
t_center = 2 * R0 / c;         
range_swath = 800;             
t_swath = 2 * range_swath / c; 
Nr_raw = round(t_swath * Fs);  
Nr = 2^nextpow2(Nr_raw);       
Nr = max(Nr, 1024);
dt = 1 / Fs;                   
tr = t_center + ((0:Nr-1).' - floor(Nr/2)) * dt;  

Na = 2^nextpow2(Ta * PRF * 3); 
Na = max(Na, 1024);
dta = 1 / PRF;                 
ta = (-Na/2 : Na/2-1) * dta;   

%% ==================== 4. 点目标设置 ====================
targets = struct();
targets.ta_center = [0, 0, -0.4, 0.4];    
targets.delta_R   = [0, 30, 0, 0];        
targets.amp       = [1, 1, 1, 1];         
targets.label     = {'T1(中心)', 'T2(远距)', 'T3(前向)', 'T4(后向)'};
num_targets = length(targets.ta_center);

%% ==================== 5. 回波信号生成 ====================
fprintf('\n正在生成回波数据...\n');
s_echo = zeros(Nr, Na);

for m = 1:Na                          
    tm = ta(m);
    for k = 1:num_targets             
        ta_k = targets.ta_center(k);
        R0_k = R0 + targets.delta_R(k);
        A_k = targets.amp(k);

        R_inst = sqrt(R0_k^2 + (v * (tm - ta_k))^2);
        arg = pi * D_az * v * (tm - ta_k) / (lambda * R0_k);
        if abs(arg) < 1e-9
            a_az = 1;
        else
            a_az = (sin(arg) / arg)^2;  
        end

        tau = 2 * R_inst / c;
        envelope_mask = abs(tr - tau) <= Tp / 2;

        if any(envelope_mask)
            t_hat = tr(envelope_mask) - tau;  
            echo_contrib = (A_k * a_az * exp(-1j * 4 * pi / lambda * R_inst)) .* ...
                           exp(1j * pi * gamma_chirp * t_hat(:).^2);
            s_echo(envelope_mask, m) = s_echo(envelope_mask, m) + echo_contrib;
        end
    end
end
fprintf('回波数据生成完成。矩阵大小: %d × %d\n', size(s_echo));

%% ==================== 6. 距离向脉冲压缩 (双支路) ====================
fprintf('正在进行距离向脉冲压缩...\n');
tau_ref = 2 * R0 / c;
t_hat_ref = tr - tau_ref;
ref_envelope = abs(t_hat_ref) <= Tp / 2;

% 支路1：无窗（矩形窗）参考信号
s_ref_range_rect = zeros(Nr, 1);
s_ref_range_rect(ref_envelope) = exp(1j * pi * gamma_chirp * t_hat_ref(ref_envelope).^2);

% 支路2：加汉明窗参考信号
N_pulse = sum(ref_envelope);
win_pulse = hamming(N_pulse); 
s_ref_range_win = zeros(Nr, 1);
s_ref_range_win(ref_envelope) = win_pulse .* exp(1j * pi * gamma_chirp * t_hat_ref(ref_envelope).^2);

% 频域匹配滤波
S_echo_f = fft(s_echo, [], 1);          
S_ref_rect_f = fft(s_ref_range_rect, [], 1);     
S_ref_win_f  = fft(s_ref_range_win, [], 1); 

S_rc_rect = S_echo_f .* conj(S_ref_rect_f); 
S_rc_win  = S_echo_f .* conj(S_ref_win_f); 

s_rc_rect = fftshift(ifft(S_rc_rect, [], 1), 1);  % 基础结果
s_rc_win  = fftshift(ifft(S_rc_win, [], 1), 1);   % 优化结果

[~, idx_az0] = min(abs(ta));            
range_profile_rect = s_rc_rect(:, idx_az0);

%% ==================== 7. 距离徙动校正 (RCMC) ====================
% 本参数下徙动极小，直接使用脉压后数据
s_rcmc_rect = s_rc_rect;  
s_rcmc_win  = s_rc_win;

%% ==================== 8. 方位向脉冲压缩 (双支路) ====================
fprintf('正在进行方位向脉冲压缩...\n');
s_az_comp_rect = zeros(Nr, Na);
s_az_comp_win  = zeros(Nr, Na);
win_az = hamming(Na).'; % 方位向汉明窗

for r_idx = 1:Nr
    R_cur = c * tr(r_idx) / 2;  
    kd_cur = -2 * v^2 / (lambda * R_cur);

    % 支路1：无窗纯相位参考信号 (已修复散焦)
    s_ref_az_rect = exp(1j * pi * kd_cur * ta.^2);  
    % 支路2：加窗纯相位参考信号
    s_ref_az_win  = win_az .* exp(1j * pi * kd_cur * ta.^2);  

    S_rcm_rect_f = fft(s_rcmc_rect(r_idx, :));          
    S_rcm_win_f  = fft(s_rcmc_win(r_idx, :));

    s_az_comp_rect(r_idx, :) = fftshift(ifft(S_rcm_rect_f .* conj(fft(s_ref_az_rect))));  
    s_az_comp_win(r_idx, :)  = fftshift(ifft(S_rcm_win_f .* conj(fft(s_ref_az_win))));  
end
fprintf('方位向脉冲压缩完成。\n');

sar_image_rect = abs(s_az_comp_rect);
sar_image_win  = abs(s_az_comp_win);

%% ==================== 9. 基础结果可视化 (未加窗) ====================
% --- 图1: 发射信号时域与频域 ---
figure('Name', '图1: 发射信号分析', 'Position', [100, 100, 1000, 450]);
t_tx = (-Tp/2 : 1/Fs : Tp/2);
s_tx = exp(1j * pi * gamma_chirp * t_tx.^2);  
subplot(1,2,1); plot(t_tx * 1e6, real(s_tx), 'b', 'LineWidth', 1);
xlabel('时间 (\mus)'); ylabel('幅度'); title('发射信号实部 (LFM)'); grid on;
subplot(1,2,2); N_fft_tx = 4096;
f_tx = (-N_fft_tx/2:N_fft_tx/2-1) * Fs / N_fft_tx / 1e6;
plot(f_tx, 20*log10(abs(fftshift(fft(s_tx, N_fft_tx)))/max(abs(fftshift(fft(s_tx, N_fft_tx))))), 'b');
xlabel('频率 (MHz)'); ylabel('归一化幅度 (dB)'); title('发射频谱'); grid on; ylim([-40, 5]);

% --- 图2: 距离向处理结果 (未加窗基础版) ---
figure('Name', '图2: 距离向处理结果(无窗)', 'Position', [100, 150, 1200, 450]);
subplot(1,3,1); imagesc(ta, c*tr/2, real(s_echo));
xlabel('慢时间 (s)'); ylabel('斜距 (m)'); title('回波数据实部'); colorbar; set(gca, 'YDir', 'normal');
subplot(1,3,2); imagesc(ta, c*tr/2, abs(s_rc_rect));
xlabel('慢时间 (s)'); ylabel('斜距 (m)'); title('距离压缩后 (未加窗)'); colorbar; set(gca, 'YDir', 'normal');
subplot(1,3,3); plot(c * tr / 2, 20 * log10(abs(range_profile_rect) / max(abs(range_profile_rect)) + eps));
xlabel('斜距 (m)'); ylabel('归一化幅度 (dB)'); title('一维距离像 (未加窗)'); grid on;
xlim([R0 - 60, R0 + 60]); ylim([-50, 5]);

% --- 图3: 二维SAR成像结果 (未加窗基础版) ---
figure('Name', '图3: 二维RD成像结果(无窗)', 'Position', [200, 200, 900, 500]);
subplot(1,2,1); imagesc(ta, c*tr/2, 20*log10(sar_image_rect / max(sar_image_rect(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('二维RD成像结果 (未加窗, dB)'); colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);
range_win = (c*tr/2 > R0-80) & (c*tr/2 < R0+80); az_win = (ta > -1.5) & (ta < 1.5);
sar_crop = sar_image_rect(range_win, az_win);
subplot(1,2,2); imagesc(ta(az_win), c*tr(range_win)/2, 20*log10(sar_crop / max(sar_crop(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('目标区域放大 (未加窗明显可见十字旁瓣)'); colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);

%% ==================== 10. 分辨率分析 (基于未加窗数据) ====================
% 距离向分辨率
r_axis = c * tr / 2;
interp_factor = 16;
r_axis_fine = linspace(r_axis(1), r_axis(end), Nr * interp_factor);
r_profile_fine = interp1(r_axis, abs(range_profile_rect), r_axis_fine, 'spline');
[~, peak_r_idx] = max(r_profile_fine); half_power = max(r_profile_fine) / sqrt(2);
left_idx = find(r_profile_fine(1:peak_r_idx) < half_power, 1, 'last'); if isempty(left_idx), left_idx = 1; end
right_idx = peak_r_idx + find(r_profile_fine(peak_r_idx:end) < half_power, 1, 'first') - 1;
delta_R_meas = r_axis_fine(right_idx) - r_axis_fine(left_idx);

% 方位向分辨率
[~, idx_t1_range] = min(abs(c*tr/2 - (R0 + targets.delta_R(1))));
az_profile_rect = sar_image_rect(idx_t1_range, :);
ta_fine = linspace(ta(1), ta(end), Na * interp_factor);
az_profile_fine = interp1(ta, az_profile_rect, ta_fine, 'spline');
[~, peak_a_idx] = max(az_profile_fine); half_power_a = max(az_profile_fine) / sqrt(2);
left_idx_a = find(az_profile_fine(1:peak_a_idx) < half_power_a, 1, 'last'); if isempty(left_idx_a), left_idx_a = 1; end
right_idx_a = peak_a_idx + find(az_profile_fine(peak_a_idx:end) < half_power_a, 1, 'first') - 1;
delta_Az_meas = v * abs(ta_fine(right_idx_a) - ta_fine(left_idx_a));

%% ==================== 11. 加窗优化对比分析 ====================
fprintf('\n========== 正在生成加窗对比图 ==========\n');
figure('Name', '图4: 加窗优化对比分析', 'Position', [150, 250, 1200, 700]);

% 2D 目标放大对比
subplot(2,2,1);
imagesc(ta(az_win), c*tr(range_win)/2, 20*log10(sar_crop / max(sar_crop(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('【未加窗】二维成像 (十字旁瓣高)');
colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);

sar_crop_win = sar_image_win(range_win, az_win);
subplot(2,2,2);
imagesc(ta(az_win), c*tr(range_win)/2, 20*log10(sar_crop_win / max(sar_crop_win(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('【二维加窗优化后】二维成像 (旁瓣抑制显著)');
colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);

% 1D 距离向剖面图对比
subplot(2,2,3);
prof_r_rect = abs(range_profile_rect) / max(abs(range_profile_rect));
prof_r_win  = abs(s_rc_win(:, idx_az0)) / max(abs(s_rc_win(:, idx_az0)));
plot(c*tr/2, 20*log10(prof_r_rect + eps), 'b', 'LineWidth', 1.2); hold on;
plot(c*tr/2, 20*log10(prof_r_win + eps), 'r--', 'LineWidth', 1.5);
xlabel('斜距 (m)'); ylabel('幅度 (dB)'); title('距离向主剖面对比');
legend('未加窗 (矩形窗)', '加窗优化 (Hamming)', 'Location', 'best');
grid on; xlim([R0 - 40, R0 + 40]); ylim([-50, 5]);

% 1D 方位向剖面图对比
subplot(2,2,4);
prof_a_rect = az_profile_rect / max(az_profile_rect);
prof_a_win  = sar_image_win(idx_t1_range, :) / max(sar_image_win(idx_t1_range, :));
plot(ta, 20*log10(prof_a_rect + eps), 'b', 'LineWidth', 1.2); hold on;
plot(ta, 20*log10(prof_a_win + eps), 'r--', 'LineWidth', 1.5);
xlabel('方位时间 (s)'); ylabel('幅度 (dB)'); title('方位向主剖面对比');
legend('未加窗 (矩形窗)', '加窗优化 (Hamming)', 'Location', 'best');
grid on; xlim([targets.ta_center(1) - 0.5, targets.ta_center(1) + 0.5]); ylim([-50, 5]);

%% ==================== 12. 实验结果输出 ====================
fprintf('\n========== 实验完成 ==========\n');
fprintf('1. 距离/方位脉压完成，基础结果（未加窗）图1-图3已生成。\n');
fprintf('2. 基础成像分辨率分析 (未加窗):\n');
fprintf('   - 距离向分辨率: 理论=%.2fm, 实测≈%.2fm\n', rho_r, delta_R_meas);
fprintf('   - 方位向分辨率: 理论=%.2fm, 实测≈%.2fm\n', rho_a, delta_Az_meas);
fprintf('3. 加窗优化处理完成，对比结果图4已生成，可见旁瓣得到显著抑制（至-40dB量级）。\n');