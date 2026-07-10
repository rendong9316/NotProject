/**
 * 实验一：Fisher线性分类器设计 - 完整实验报告生成
 * 基于 exp1_ds_web.m 的实际运行结果
 * 模式识别上机实验 2026
 */
const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, BorderStyle, WidthType, ShadingType,
  ImageRun, PageBreak
} = require('docx');

// ===== 格式常量定义 =====
const FONT_WEST = "Times New Roman";
const FONT_CN = "SimSun";
const SIZE_XIAOSI = 24;       // 小四 12pt
const SIZE_WUHAO = 21;        // 五号 10.5pt (图注)
const SIZE_XIAOWU = 18;       // 小五 9pt (程序代码)
const COLOR_BLACK = "000000";
const LINE_SPACING = { line: 300, lineRule: "auto" };  // 1.25倍行距

// 分离式字体定义 (严禁合并写法: 不能使用 { name: "Times New Roman", eastAsia: "SimSun" })
const FONT_BODY = {
  ascii: FONT_WEST,
  hAnsi: FONT_WEST,
  cs: FONT_WEST,
  eastAsia: FONT_CN
};

// 正文文本运行
function bodyRun(text, opts = {}) {
  return new TextRun({
    text,
    font: FONT_BODY,
    size: SIZE_XIAOSI,
    color: COLOR_BLACK,
    ...opts
  });
}

// 正文段落（首行缩进2汉字）
function bodyPara(children, opts = {}) {
  const runs = Array.isArray(children) ? children : [bodyRun(children)];
  return new Paragraph({
    spacing: LINE_SPACING,
    indent: { firstLine: 480 },  // 首行缩进2个汉字
    children: runs,
    ...opts
  });
}

// 居中段落（无缩进，用于公式）
function centerPara(children) {
  const runs = Array.isArray(children) ? children : [bodyRun(children)];
  return new Paragraph({
    spacing: LINE_SPACING,
    alignment: AlignmentType.CENTER,
    indent: { firstLine: 0 },
    children: runs
  });
}

// 图注段落 (五号居中)
function captionPara(text) {
  return new Paragraph({
    spacing: LINE_SPACING,
    alignment: AlignmentType.CENTER,
    children: [new TextRun({
      text,
      font: FONT_BODY,
      size: SIZE_WUHAO,
      color: COLOR_BLACK
    })]
  });
}

// 表格边框
const tableBorder = { style: BorderStyle.SINGLE, size: 1, color: "333333" };
const cellBorders = {
  top: tableBorder, bottom: tableBorder,
  left: tableBorder, right: tableBorder
};

// 表格单元格
function tableCell(text, opts = {}) {
  const { width, shading, bold, align } = opts;
  const cellOpts = {
    borders: cellBorders,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({
      spacing: { line: 280, lineRule: "auto" },
      alignment: align || AlignmentType.CENTER,
      children: [new TextRun({
        text: String(text),
        font: FONT_BODY,
        size: 20,
        color: COLOR_BLACK,
        bold: bold || false
      })]
    })]
  };
  if (width) cellOpts.width = { size: width, type: WidthType.DXA };
  if (shading) cellOpts.shading = { fill: shading, type: ShadingType.CLEAR };
  return new TableCell(cellOpts);
}

// 标题
function heading1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 300, after: 200, line: 300, lineRule: "auto" },
    children: [new TextRun({
      text,
      font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" },
      size: 32, bold: true, color: "000000"
    })]
  });
}

function heading2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 160, line: 300, lineRule: "auto" },
    children: [new TextRun({
      text,
      font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" },
      size: 28, bold: true, color: "000000"
    })]
  });
}

// ===== 读取生成的图片 =====
const figSmall = fs.readFileSync('figure_small_sample.png');
const figLarge = fs.readFileSync('figure_large_sample.png');
const figProj = fs.readFileSync('figure_projection_distribution.png');

// ===== 构建文档内容 =====
const children = [];

// ===== 封面 =====
children.push(new Paragraph({ spacing: { before: 2000 }, children: [] }));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: LINE_SPACING,
  children: [new TextRun({
    text: '模式识别上机实验报告',
    font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" },
    size: 44, bold: true, color: COLOR_BLACK
  })]
}));
children.push(new Paragraph({ spacing: { before: 400 }, children: [] }));
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: LINE_SPACING,
  children: [new TextRun({
    text: '实验一：Fisher线性分类器的设计',
    font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" },
    size: 36, bold: true, color: COLOR_BLACK
  })]
}));
children.push(new Paragraph({ spacing: { before: 600 }, children: [] }));
children.push(centerPara('课程：模式识别'));
children.push(centerPara('专业班级：____________________'));
children.push(centerPara('姓名：____________________'));
children.push(centerPara('学号：____________________'));
children.push(centerPara('实验日期：2026年6月4日'));
children.push(new Paragraph({ spacing: { before: 1200 }, children: [] }));
children.push(centerPara('仿真环境：MATLAB R2026a'));

// Page break
children.push(new Paragraph({ children: [new PageBreak()] }));

// ===== 一、实验目的 =====
children.push(heading1('一、实验目的'));
children.push(bodyPara('1. 学习采用Matlab程序产生正态分布的二维随机数（matlab：用mvnrnd函数）。'));
children.push(bodyPara('2. 掌握估计类均值向量，协方差矩阵，类间离散度矩阵、类内离散度矩阵的计算方法。'));
children.push(bodyPara('3. 掌握Fisher线性判别方法。'));
children.push(bodyPara('4. 掌握Bayes决策的错误率的计算。'));
children.push(bodyPara('5. 掌握分类器错误率的估算方法。'));

// ===== 二、实验原理 =====
children.push(heading1('二、实验原理'));
children.push(heading2('2.1 Fisher准则基本思想'));
children.push(bodyPara('如果在二维空间中一条直线能将两类样本分开，或者错分类很少，则同一类别样本数据在该直线的单位法向量上的投影的绝大多数都应该超过某一值。而另一类数据的投影都应该小于（或绝大多数都小于）该值，则这条直线就有可能将两类分开。'));
children.push(bodyPara([
  bodyRun('Fisher准则的基本思路：'),
  bodyRun('投影方向w的选择应能使两类样本投影的均值之差尽可能大，同时使类内样本的离散程度尽可能小。')
]));
children.push(heading2('2.2 Fisher准则函数'));
children.push(bodyPara('评价投影方向w的准则函数定义为：'));
children.push(centerPara('J(w) = (wᵀSb w) / (wᵀSw w)'));
children.push(bodyPara('其中Sb为类间离散度矩阵，Sw为类内离散度矩阵。'));
children.push(bodyPara([
  bodyRun('类间离散度矩阵：'), bodyRun('Sb = (m₁ - m₂)(m₁ - m₂)ᵀ')
]));
children.push(bodyPara([
  bodyRun('类内离散度矩阵：'), bodyRun('Sw = Σ(x-m₁)(x-m₁)ᵀ + Σ(x-m₂)(x-m₂)ᵀ')
]));
children.push(bodyPara('使J(w)取极大值时的w*即为Fisher最优投影方向：w* = Sw⁻¹(m₁ - m₂)'));
children.push(heading2('2.3 分类决策规则'));
children.push(bodyPara('将样本投影到Fisher直线上得y = w*ᵀx。分类阈值取两类投影均值的平均：w₀ = (ỹ₁ + ỹ₂) / 2。对于新样本x，若w*ᵀx + w₀ > 0，则判为第一类；否则判为第二类。'));

// ===== 三、实验内容及要求 =====
children.push(heading1('三、实验内容及要求'));
children.push(bodyPara('（1）仿真产生两类二维样本数据，每类样本各10个，画出样本的分布图。两类样本服从正态分布，参数如下：'));
children.push(centerPara('μ₁ = [-2, -2]ᵀ，Σ₁ = [[1, 0], [0, 1]]'));
children.push(centerPara('μ₂ = [2, 2]ᵀ，Σ₂ = [[1, 0], [0, 4]]'));
children.push(bodyPara('（2）编写程序，根据产生的二维样本估计类均值向量和协方差矩阵、类间离散度矩阵、类内离散度矩阵。'));
children.push(bodyPara('（3）考虑Fisher线性判别方法，求解最优投影方向w*：'));
children.push(bodyPara('    1）在样本分布图上画出表示最优投影方向的直线；'));
children.push(bodyPara('    2）计算投影后的阈值权；'));
children.push(bodyPara('    3）统计分类器的各类错误率及总的平均错误率。'));
children.push(bodyPara('（4）各类样本取10000个，重复上述实验。'));

// ===== 四、实验结果 =====
children.push(heading1('四、实验结果'));

children.push(heading2('4.1 小样本实验（每类N=10）'));

children.push(bodyPara('两类样本的真实分布参数为：'));
children.push(bodyPara([
  bodyRun('类1：'), bodyRun('μ₁ = [-2, -2]ᵀ'), bodyRun('，'),
  bodyRun('Σ₁ = [[1, 0], [0, 1]]')
]));
children.push(bodyPara([
  bodyRun('类2：'), bodyRun('μ₂ = [2, 2]ᵀ'), bodyRun('，'),
  bodyRun('Σ₂ = [[1, 0], [0, 4]]')
]));

children.push(bodyPara('利用MATLAB mvnrnd函数生成10个样本后，估计得到：'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【估计的类均值向量】')
]));
children.push(bodyPara('类1均值：m₁ = [-1.7845, -2.4065]ᵀ'));
children.push(bodyPara('类2均值：m₂ = [2.2142, 1.5254]ᵀ'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【估计的协方差矩阵】')
]));
children.push(bodyPara('类1协方差矩阵 S₁：'));
children.push(bodyPara('  [[1.2824, -0.0093]'));
children.push(bodyPara('   [-0.0093, 0.6582]]'));
children.push(bodyPara('类2协方差矩阵 S₂：'));
children.push(bodyPara('  [[1.8524, -0.7329]'));
children.push(bodyPara('   [-0.7329, 4.0047]]'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【类内离散度矩阵 Sw】')
]));
children.push(bodyPara('Sw = [[28.2126, -6.6800]'));
children.push(bodyPara('      [-6.6800, 41.9658]]'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【类间离散度矩阵 Sb】')
]));
children.push(bodyPara('Sb = [[15.9894, 15.7224]'));
children.push(bodyPara('      [15.7224, 15.4599]]'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【Fisher最优投影方向 w*】')
]));
children.push(bodyPara('w* = [0.1703, 0.1208]ᵀ'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【投影后各类均值】')
]));
children.push(bodyPara('类1投影均值：ỹ₁ = -0.5947'));
children.push(bodyPara('类2投影均值：ỹ₂ = 0.5614'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【分类阈值】'), bodyRun(' w₀ = 0.0166')
]));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【分类错误率】')
]));
children.push(bodyPara('类1错误率：0.00%（0/10）'));
children.push(bodyPara('类2错误率：0.00%（0/10）'));
children.push(bodyPara([
  bodyRun('总平均错误率：0.00%'), bodyRun('（10个样本全部正确分类）')
]));

children.push(bodyPara(''));
children.push(bodyPara('小样本下的样本分布及Fisher决策直线如图1所示：'));

// 图1: 小样本
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 200, after: 100 },
  children: [new ImageRun({
    type: 'png',
    data: figSmall,
    transformation: { width: 460, height: 460 },
    altText: { title: '图1', description: '小样本Fisher判别', name: '图1' }
  })]
}));
children.push(captionPara('图1  小样本Fisher判别结果（每类10个样本）'));

children.push(bodyPara('从图1可以看出，两类样本线性可分，Fisher分类器在小样本上取得了100%的分类正确率。红色圆圈为类1样本，蓝色加号为类2样本，黑色直线为决策边界（w*ᵀx = w₀）。'));

// Page break
children.push(new Paragraph({ children: [new PageBreak()] }));

children.push(heading2('4.2 大样本实验（每类N=10000）'));

children.push(bodyPara('每类生成10000个样本，重新估计所有参数并进行分类：'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【估计的类均值向量】')
]));
children.push(bodyPara('类1均值：m₁ = [-1.9943, -1.9759]ᵀ'));
children.push(bodyPara('类2均值：m₂ = [1.9890, 2.0073]ᵀ'));
children.push(bodyPara(''));
children.push(bodyPara('注：大样本下估计的均值非常接近真实值μ₁=[-2,-2]ᵀ和μ₂=[2,2]ᵀ，体现了大数定律。'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【估计的协方差矩阵】')
]));
children.push(bodyPara('类1协方差矩阵 S₁：'));
children.push(bodyPara('  [[1.0084, 0.0022]'));
children.push(bodyPara('   [0.0022, 1.0009]]（接近真实Σ₁）'));
children.push(bodyPara('类2协方差矩阵 S₂：'));
children.push(bodyPara('  [[1.0195, 0.0199]'));
children.push(bodyPara('   [0.0199, 4.0249]]（接近真实Σ₂）'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【类内离散度矩阵 Sw】')
]));
children.push(bodyPara('Sw = [[20276.95, 220.86]'));
children.push(bodyPara('      [220.86, 50253.05]]'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【类间离散度矩阵 Sb】')
]));
children.push(bodyPara('Sb = [[15.8666, 15.8660]'));
children.push(bodyPara('      [15.8660, 15.8654]]'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【Fisher最优投影方向 w*】')
]));
children.push(bodyPara('w* = [1.9559×10⁻⁴, 7.8402×10⁻⁵]ᵀ'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【投影后各类均值】')
]));
children.push(bodyPara('类1投影均值：ỹ₁ = -0.0005'));
children.push(bodyPara('类2投影均值：ỹ₂ = 0.0005'));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【分类阈值】'), bodyRun(' w₀ = 0.0000')
]));
children.push(bodyPara(''));
children.push(bodyPara([
  bodyRun('【分类错误率】')
]));
children.push(bodyPara('类1错误率：0.48%（48/10000）'));
children.push(bodyPara('类2错误率：1.44%（144/10000）'));
children.push(bodyPara([
  bodyRun('总平均错误率：0.96%')
]));

children.push(bodyPara(''));
children.push(bodyPara('大样本下的样本分布如图2所示：'));

// 图2: 大样本
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 200, after: 100 },
  children: [new ImageRun({
    type: 'png',
    data: figLarge,
    transformation: { width: 460, height: 460 },
    altText: { title: '图2', description: '大样本Fisher判别', name: '图2' }
  })]
}));
children.push(captionPara('图2  大样本Fisher判别结果（每类10000个样本，透明度表示点密度）'));

// Page break
children.push(new Paragraph({ children: [new PageBreak()] }));

children.push(heading2('4.3 投影分布直方图'));

children.push(bodyPara('为直观展示Fisher投影的效果，绘制小样本与大样本下两类在Fisher方向上的投影分布：'));

// 图3: 投影分布
children.push(new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { before: 200, after: 100 },
  children: [new ImageRun({
    type: 'png',
    data: figProj,
    transformation: { width: 560, height: 220 },
    altText: { title: '图3', description: '投影分布直方图', name: '图3' }
  })]
}));
children.push(captionPara('图3  Fisher投影分布直方图（左：小样本N=10；右：大样本N=10000）'));

children.push(bodyPara('从图3可以看出，Fisher投影将二维样本映射到一维直线上，使得两类样本尽可能分离。左图小样本中两类投影完全分开（无重叠），对应0%的错误率。右图大样本中存在少量重叠区域，这正是导致约0.96%分类错误的原因——类2的方差（Σ₂的第二个特征值=4）比类1的方差（Σ₁=单位阵）更大，导致类2在垂直于决策方向上有更大散布，部分样本越过决策边界。'));

children.push(heading2('4.4 结果汇总'));

// 结果汇总表格
const summaryColWidths = [2000, 2400, 2400];
const summaryTableWidth = summaryColWidths.reduce((a, b) => a + b, 0);

children.push(bodyPara(''));
children.push(bodyPara('小样本与大样本实验结果汇总如表1所示：'));
children.push(bodyPara(''));

function summaryHeader(text) {
  return tableCell(text, { bold: true, shading: "D5E8F0", width: 0 });
}
function summaryData(text) {
  return tableCell(text, { width: 0 });
}
function summaryRow(...vals) {
  return new TableRow({ children: vals.map(v => summaryData(v)) });
}
function summaryHeaderRow(...vals) {
  return new TableRow({ children: vals.map(v => summaryHeader(v)) });
}

children.push(new Table({
  width: { size: summaryTableWidth, type: WidthType.DXA },
  columnWidths: summaryColWidths,
  rows: [
    summaryHeaderRow('指标', '小样本 (N=10)', '大样本 (N=10000)'),
    summaryRow('投影方向 w*', '[0.1703, 0.1208]ᵀ', '[1.96e-4, 7.84e-5]ᵀ'),
    summaryRow('阈值 w₀', '0.0166', '0.0000'),
    summaryRow('类1错误率', '0.00%', '0.48%'),
    summaryRow('类2错误率', '0.00%', '1.44%'),
    summaryRow('总平均错误率', '0.00%', '0.96%')
  ]
}));
children.push(captionPara('表1  小样本与大样本Fisher分类实验结果汇总'));

// ===== 五、结论 =====
children.push(new Paragraph({ children: [new PageBreak()] }));
children.push(heading1('五、结论'));

children.push(bodyPara('通过本次Fisher线性分类器设计实验，得出以下结论：'));

children.push(bodyPara([
  bodyRun('1. Fisher线性判别的有效性：'),
  bodyRun('实验结果表明，Fisher线性判别方法能够有效地找到使两类样本投影分离的最优方向。在小样本（N=10）条件下，两类样本线性可分，分类正确率达到100%。在大样本（N=10000）条件下，总平均错误率仅为0.96%，说明Fisher分类器具有良好的分类性能。')
]));

children.push(bodyPara([
  bodyRun('2. 样本量对参数估计的影响：'),
  bodyRun('小样本情况下（N=10），估计的均值（类1：[-1.78, -2.41]；类2：[2.21, 1.53]）与真实均值（[-2, -2]和[2, 2]）存在一定偏差，这是由样本量不足导致的采样误差。大样本情况下（N=10000），估计值（类1：[-1.99, -1.98]；类2：[1.99, 2.01]）非常接近真实值，验证了大数定律。协方差矩阵的估计也呈现相同的规律——样本越多，估计越准确。')
]));

children.push(bodyPara([
  bodyRun('3. 方差差异对分类性能的影响：'),
  bodyRun('类2的协方差矩阵Σ₂=[1,0;0,4]在x₂方向上的方差（4）显著大于Σ₁在x₂上的方差（1），这种异方差性使得两类在投影方向上的分布存在一定重叠。大样本中类2的错误率（1.44%）约为类1错误率（0.48%）的3倍，这是因为类2沿x₂方向散布更广，部分类2样本越过了决策边界。')
]));

children.push(bodyPara([
  bodyRun('4. 小样本与大样本错误率差异分析：'),
  bodyRun('小样本下错误率为0%不能完全说明分类器完美——这是因为小样本量下采样恰好未产生错误样本，不代表真实的分类性能。大样本下0.96%的错误率更能反映Fisher分类器在该数据分布下的真实泛化性能。这说明了用大量测试样本估计错误率的重要性。')
]));

children.push(bodyPara([
  bodyRun('5. Fisher判别与Bayes判别的关系：'),
  bodyRun('在本实验中两类协方差不等（Σ₁≠Σ₂），Bayes最优决策面为二次曲面。Fisher判别使用线性决策面是次优的，但实验表明其错误率依然很低（0.96%），说明在实际应用中Fisher线性判别具有很强的鲁棒性和实用价值。')
]));

children.push(bodyPara([
  bodyRun('6. 实验总结：'),
  bodyRun('通过本实验，我深入理解了Fisher线性判别的原理和实现方法，掌握了类均值向量、协方差矩阵、类内/类间离散度矩阵的计算，以及最优投影方向和分类阈值的求解。同时认识到样本量对参数估计和错误率估计的重要影响，为后续模式识别方法的学习打下了良好基础。')
]));

// ===== 六、实验程序 =====
children.push(heading1('六、实验程序'));

children.push(bodyPara('以下给出完整的MATLAB源程序（与exp1_ds_web.m一致）：'));

const matlabCode = `%% 实验一：Fisher线性分类器设计
clear; clc; close all;

%% 真实参数
mu1 = [-2; -2]; Sigma1 = [1, 0; 0, 1];
mu2 = [ 2;  2]; Sigma2 = [1, 0; 0, 4];

%% 小样本：每类10个
N1 = 10; N2 = 10;
rng(2026);  % 固定随机种子，保证可重复
X1 = mvnrnd(mu1, Sigma1, N1)';
X2 = mvnrnd(mu2, Sigma2, N2)';

% --- 计算统计量 ---
m1 = mean(X1, 2);
m2 = mean(X2, 2);
S1 = cov(X1');
S2 = cov(X2');
Sw = (N1-1)*S1 + (N2-1)*S2;
Sb = (m1 - m2)*(m1 - m2)';

% --- Fisher最优投影方向 ---
w = Sw \\ (m1 - m2);

% 计算两类样本在 w 上的投影值
proj1 = w' * X1;
proj2 = w' * X2;
m1_tilde = mean(proj1);
m2_tilde = mean(proj2);

% 确保第一类的投影均值小于第二类
if m1_tilde > m2_tilde
    w = -w;
    proj1 = -proj1;
    proj2 = -proj2;
    m1_tilde = mean(proj1);
    m2_tilde = mean(proj2);
end

% 阈值 w0 = -(m1_tilde+m2_tilde)/2
w0 = -(m1_tilde + m2_tilde) / 2;

% --- 分类与错误率统计 ---
all_data = [X1, X2];
true_labels = [ones(1,N1), 2*ones(1,N2)];
g_vals = w' * all_data - w0;
pred = (g_vals >= 0) + 1;
err1 = sum(pred(1:N1) ~= 1) / N1 * 100;
err2 = sum(pred(N1+1:end) ~= 2) / N2 * 100;
total_err = (sum(pred ~= true_labels)) / (N1+N2) * 100;

% --- 输出结果 ---
fprintf('================== 小样本（每类10个）==================\\n');
fprintf('类1均值: [%.4f, %.4f]\\n', m1(1), m1(2));
fprintf('类2均值: [%.4f, %.4f]\\n', m2(1), m2(2));
fprintf('最优投影方向 w*: [%.4f, %.4f]\\n', w(1), w(2));
fprintf('阈值 w0 = %.4f\\n', w0);
fprintf('类1错误率: %.2f%%\\n', err1);
fprintf('类2错误率: %.2f%%\\n', err2);
fprintf('总平均错误率: %.2f%%\\n', total_err);

% --- 画图（小样本）---
figure('Name','小样本 N=10');
plot(X1(1,:), X1(2,:), 'ro', 'MarkerSize',8,'LineWidth',1.5); hold on;
plot(X2(1,:), X2(2,:), 'b+', 'MarkerSize',8,'LineWidth',1.5);
% 决策直线: w'*x = w0
x_vals = linspace(-5,5,200);
if abs(w(2)) > 1e-6
    y_vals = (w0 - w(1)*x_vals) / w(2);
else
    y_vals = zeros(size(x_vals));
    x_vals = w0/w(1) * ones(size(x_vals));
end
plot(x_vals, y_vals, 'k-', 'LineWidth',1.5);
xlabel('x_1'); ylabel('x_2');
title('Fisher判别（每类10个样本）');
legend('类1','类2','决策直线');
axis equal; grid on;

%% ================== 大样本（每类10000个）==================
N1_big = 10000; N2_big = 10000;
X1_big = mvnrnd(mu1, Sigma1, N1_big)';
X2_big = mvnrnd(mu2, Sigma2, N2_big)';

% 统计量
m1_big = mean(X1_big,2);
m2_big = mean(X2_big,2);
S1_big = cov(X1_big');
S2_big = cov(X2_big');
Sw_big = (N1_big-1)*S1_big + (N2_big-1)*S2_big;
Sb_big = (m1_big - m2_big)*(m1_big - m2_big)';

% Fisher
w_big = Sw_big \\ (m1_big - m2_big);
proj1_big = w_big' * X1_big;
proj2_big = w_big' * X2_big;
m1_tilde_big = mean(proj1_big);
m2_tilde_big = mean(proj2_big);
if m1_tilde_big > m2_tilde_big
    w_big = -w_big;
    proj1_big = -proj1_big;
    proj2_big = -proj2_big;
    m1_tilde_big = mean(proj1_big);
    m2_tilde_big = mean(proj2_big);
end
w0_big = -(m1_tilde_big + m2_tilde_big) / 2;

% 分类
all_big = [X1_big, X2_big];
true_big = [ones(1,N1_big), 2*ones(1,N2_big)];
g_vals_big = w_big' * all_big - w0_big;
pred_big = (g_vals_big >= 0) + 1;
err1_big = sum(pred_big(1:N1_big) ~= 1) / N1_big * 100;
err2_big = sum(pred_big(N1_big+1:end) ~= 2) / N2_big * 100;
total_err_big = (sum(pred_big ~= true_big)) / (N1_big+N2_big) * 100;

% 输出
fprintf('\\n================== 大样本（每类10000个）==================\\n');
fprintf('类1均值: [%.4f, %.4f]\\n', m1_big(1), m1_big(2));
fprintf('类2均值: [%.4f, %.4f]\\n', m2_big(1), m2_big(2));
fprintf('最优投影方向 w*: [%.6f, %.6f]\\n', w_big(1), w_big(2));
fprintf('阈值 w0 = %.4f\\n', w0_big);
fprintf('类1错误率: %.2f%% (%d/%d)\\n', err1_big, ...
    sum(pred_big(1:N1_big)~=1), N1_big);
fprintf('类2错误率: %.2f%% (%d/%d)\\n', err2_big, ...
    sum(pred_big(N1_big+1:end)~=2), N2_big);
fprintf('总平均错误率: %.2f%%\\n', total_err_big);

% --- 画图（大样本）---
figure('Name','大样本 N=10000');
scatter(X1_big(1,:), X1_big(2,:), 3, 'r', ...
    'filled', 'MarkerFaceAlpha',0.2); hold on;
scatter(X2_big(1,:), X2_big(2,:), 3, 'b', ...
    'filled', 'MarkerFaceAlpha',0.2);
x_vals = linspace(-5,7,200);
if abs(w_big(2)) > 1e-6
    y_vals = (w0_big - w_big(1)*x_vals) / w_big(2);
else
    y_vals = zeros(size(x_vals));
    x_vals = w0_big/w_big(1) * ones(size(x_vals));
end
plot(x_vals, y_vals, 'k-', 'LineWidth',1.5);
xlabel('x_1'); ylabel('x_2');
title('Fisher判别（每类10000个样本）');
legend('类1','类2','决策直线');
axis equal; grid on;`;

// 用等宽字体显示代码（小五号）
const codeLines = matlabCode.split('\n');
codeLines.forEach(line => {
  children.push(new Paragraph({
    spacing: { line: 260, lineRule: "auto" },
    children: [new TextRun({
      text: line || ' ',
      font: { ascii: "Courier New", hAnsi: "Courier New", cs: "Courier New", eastAsia: FONT_CN },
      size: SIZE_XIAOWU,
      color: "333333"
    })]
  }));
});

// ===== 构建文档 =====
const doc = new Document({
  styles: {
    default: {
      document: {
        run: { font: FONT_BODY, size: SIZE_XIAOSI, color: COLOR_BLACK }
      }
    },
    paragraphStyles: [
      {
        id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 32, bold: true, font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" }, color: COLOR_BLACK },
        paragraph: { spacing: { before: 300, after: 200 }, outlineLevel: 0 }
      },
      {
        id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: { ascii: "Arial", hAnsi: "Arial", cs: "Arial", eastAsia: "SimHei" }, color: COLOR_BLACK },
        paragraph: { spacing: { before: 240, after: 160 }, outlineLevel: 1 }
      }
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    children: children
  }]
});

Packer.toBuffer(doc).then(buffer => {
  const outName = '实验一_Fisher线性分类器实验报告.docx';
  const outPath = '实验一_Fisher线性分类器实验报告_v2.docx';
  fs.writeFileSync(outPath, buffer);
  console.log('报告已生成: ' + outPath);
  console.log('（如原文件未被占用，请手动重命名为 ' + outName + '）');
  console.log('文件大小: ' + (buffer.length / 1024).toFixed(1) + ' KB');
}).catch(err => {
  console.error('生成失败:', err);
});
