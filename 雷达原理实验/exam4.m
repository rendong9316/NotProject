%% SAR成像仿真实验 —— 正侧视RD成像（单点目标）
% 功能：仿真点目标回波，完成距离/方位脉压，求解距离向和方位向分辨率

clear; close all; clc;

%% ==================== 1. 系统参数设置 ====================
c = 3e8;                      % 光速 (m/s)
fc = 8850e6;                  % 中心频率 8850 MHz
lambda = c / fc;              % 波长 (m)

R0 = 6300;                    % 场景中心斜距 6.3 km
Tp = 10e-6;                   % 脉冲宽度 10 us
B = 50e6;                     % 发射带宽 50 MHz
D_az = 2;                     % 方位向天线孔径 2 m
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

%% ==================== 4. 单点目标设置 ====================
ta_center = 0;          % 方位向中心时间 (s)
delta_R = 0;            % 距离向偏移 (m)
amp = 1;                % 幅度

fprintf('\n========== 目标设置 ==========\n');
fprintf('目标位置: 方位中心时间 = %.2f s, 斜距 = %.2f m\n', ta_center, R0 + delta_R);

%% ==================== 5. 回波信号生成 ====================
fprintf('\n正在生成回波数据...\n');
s_echo = zeros(Nr, Na);

for m = 1:Na                          
    tm = ta(m);
    R_inst = sqrt(R0^2 + (v * (tm - ta_center))^2);
    arg = pi * D_az * v * (tm - ta_center) / (lambda * R0);
    if abs(arg) < 1e-9
        a_az = 1;
    else
        a_az = (sin(arg) / arg)^2;  
    end

    tau = 2 * R_inst / c;
    envelope_mask = abs(tr - tau) <= Tp / 2;

    if any(envelope_mask)
        t_hat = tr(envelope_mask) - tau;  
        echo_contrib = (amp * a_az * exp(-1j * 4 * pi / lambda * R_inst)) .* ...
                       exp(1j * pi * gamma_chirp * t_hat(:).^2);
        s_echo(envelope_mask, m) = s_echo(envelope_mask, m) + echo_contrib;
    end
end
fprintf('回波数据生成完成。矩阵大小: %d × %d\n', size(s_echo));

%% ==================== 6. 距离向脉冲压缩 ====================
fprintf('正在进行距离向脉冲压缩...\n');
tau_ref = 2 * R0 / c;
t_hat_ref = tr - tau_ref;
ref_envelope = abs(t_hat_ref) <= Tp / 2;

% 参考信号
s_ref_range = zeros(Nr, 1);
s_ref_range(ref_envelope) = exp(1j * pi * gamma_chirp * t_hat_ref(ref_envelope).^2);

% 频域匹配滤波
S_echo_f = fft(s_echo, [], 1);          
S_ref_f = fft(s_ref_range, [], 1);     
S_rc = S_echo_f .* conj(S_ref_f); 
s_rc = fftshift(ifft(S_rc, [], 1), 1);  % 距离压缩后数据

% 提取一维距离像（方位中心时刻）
[~, idx_az0] = min(abs(ta));            
range_profile = s_rc(:, idx_az0);

%% ==================== 7. 距离徙动校正 (RCMC) ====================
% 本参数下徙动极小，直接使用脉压后数据
s_rcmc = s_rc;  

%% ==================== 8. 方位向脉冲压缩 ====================
fprintf('正在进行方位向脉冲压缩...\n');
s_az_comp = zeros(Nr, Na);

for r_idx = 1:Nr
    R_cur = c * tr(r_idx) / 2;  
    kd_cur = -2 * v^2 / (lambda * R_cur);

    % 方位参考信号
    s_ref_az = exp(1j * pi * kd_cur * ta.^2);  

    S_rcm_f = fft(s_rcmc(r_idx, :));          
    s_az_comp(r_idx, :) = fftshift(ifft(S_rcm_f .* conj(fft(s_ref_az))));  
end
fprintf('方位向脉冲压缩完成。\n');

sar_image = abs(s_az_comp);

%% ==================== 9. 一维距离像 ====================
figure('Name', '一维距离像', 'Position', [100, 100, 800, 400]);
plot(c * tr / 2, 20 * log10(abs(range_profile) / max(abs(range_profile)) + eps));
xlabel('斜距 (m)'); ylabel('归一化幅度 (dB)'); title('一维距离像');
grid on; xlim([R0 - 60, R0 + 60]); ylim([-50, 5]);

%% ==================== 10. 二维RD成像结果 ====================
figure('Name', '二维RD成像结果', 'Position', [200, 200, 900, 500]);
imagesc(ta, c*tr/2, 20*log10(sar_image / max(sar_image(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('二维RD成像结果 (dB)');
colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);

% 目标区域放大显示
range_win = (c*tr/2 > R0-80) & (c*tr/2 < R0+80);
az_win = (ta > -1.5) & (ta < 1.5);
sar_crop = sar_image(range_win, az_win);

figure('Name', '目标区域放大', 'Position', [300, 300, 800, 500]);
imagesc(ta(az_win), c*tr(range_win)/2, 20*log10(sar_crop / max(sar_crop(:)) + eps));
xlabel('方位时间 (s)'); ylabel('斜距 (m)'); title('目标区域放大');
colorbar; set(gca, 'YDir', 'normal'); clim([-40, 0]);

%% ==================== 11. 分辨率求解 ====================
% 距离向分辨率
r_axis = c * tr / 2;
interp_factor = 16;
r_axis_fine = linspace(r_axis(1), r_axis(end), Nr * interp_factor);
r_profile_fine = interp1(r_axis, abs(range_profile), r_axis_fine, 'spline');
[~, peak_r_idx] = max(r_profile_fine);
half_power = max(r_profile_fine) / sqrt(2);

% 找到-3dB点
left_idx = find(r_profile_fine(1:peak_r_idx) < half_power, 1, 'last'); 
if isempty(left_idx), left_idx = 1; end
right_idx = peak_r_idx + find(r_profile_fine(peak_r_idx:end) < half_power, 1, 'first') - 1;
delta_R_meas = r_axis_fine(right_idx) - r_axis_fine(left_idx);

% 方位向分辨率
[~, idx_t1_range] = min(abs(c*tr/2 - R0));
az_profile = sar_image(idx_t1_range, :);
ta_fine = linspace(ta(1), ta(end), Na * interp_factor);
az_profile_fine = interp1(ta, az_profile, ta_fine, 'spline');
[~, peak_a_idx] = max(az_profile_fine);
half_power_a = max(az_profile_fine) / sqrt(2);

left_idx_a = find(az_profile_fine(1:peak_a_idx) < half_power_a, 1, 'last'); 
if isempty(left_idx_a), left_idx_a = 1; end
right_idx_a = peak_a_idx + find(az_profile_fine(peak_a_idx:end) < half_power_a, 1, 'first') - 1;
delta_Az_meas = v * abs(ta_fine(right_idx_a) - ta_fine(left_idx_a));

%% ==================== 12. 方位向剖面图 ====================
figure('Name', '方位向剖面', 'Position', [400, 400, 800, 400]);
plot(ta, 20*log10(abs(az_profile) / max(abs(az_profile)) + eps), 'b', 'LineWidth', 1.2);
xlabel('方位时间 (s)'); ylabel('归一化幅度 (dB)'); title('方位向剖面');
grid on; xlim([ta_center - 0.5, ta_center + 0.5]); ylim([-50, 5]);

%% ==================== 13. 实验结果输出 ====================
fprintf('\n========== 分辨率分析结果 ==========\n');
fprintf('距离向分辨率:\n');
fprintf('  理论值: %.2f m\n', rho_r);
fprintf('  实测值: %.2f m\n', delta_R_meas);
fprintf('  误差: %.2f %%\n', abs(delta_R_meas - rho_r) / rho_r * 100);

fprintf('\n方位向分辨率:\n');
fprintf('  理论值: %.2f m\n', rho_a);
fprintf('  实测值: %.2f m\n', delta_Az_meas);
fprintf('  误差: %.2f %%\n', abs(delta_Az_meas - rho_a) / rho_a * 100);

fprintf('\n========== 实验完成 ==========\n');
fprintf('单目标SAR回波仿真完成，距离/方位脉压完成。\n');
fprintf('点目标图像清晰可见，位于场景中心位置。\n');