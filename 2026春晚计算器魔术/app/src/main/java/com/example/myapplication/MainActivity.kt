package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MagicCalculatorApp()
                }
            }
        }
    }
}

@Composable
fun MagicCalculatorApp() {
    // 基础计算器状态
    var displayText by remember { mutableStateOf("0") }
    var firstNumber by remember { mutableStateOf("") }
    var secondNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var isNewOperation by remember { mutableStateOf(true) }

    // 魔术模式状态
    var isMagicMode by remember { mutableStateOf(false) }
    var magicStep by remember { mutableIntStateOf(0) }
    var sumOfTwo by remember { mutableStateOf("") }  // 存储两数之和
    var magicNumber by remember { mutableStateOf("") }  // 存储魔术数字
    var isPhoneFlipped by remember { mutableStateOf(false) }
    var magicNumberShown by remember { mutableStateOf(false) }

    // 显示模式状态
    var showResultMode by remember { mutableStateOf(false) }
    var resultExpression by remember { mutableStateOf("") }
    var cursorVisible by remember { mutableStateOf(true) }

    // 动画标志（仅在等号后触发）
    var useUpperAnimation by remember { mutableStateOf(false) }
    var useResultAnimation by remember { mutableStateOf(false) }

    // 光标闪烁
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    // ========== 辅助函数 ==========
    fun calculateResult(first: String, second: String, op: String): String {
        val firstNum = first.toDoubleOrNull() ?: return "Error"
        val secondNum = second.toDoubleOrNull() ?: return "Error"
        return try {
            val result = when (op) {
                "+" -> firstNum + secondNum
                "-" -> firstNum - secondNum
                "×" -> firstNum * secondNum
                "÷" -> {
                    if (secondNum == 0.0) return "Error"
                    firstNum / secondNum
                }
                else -> firstNum
            }
            if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                val df = DecimalFormat("#.########")
                df.format(result)
            }
        } catch (_: Exception) {
            "Error"
        }
    }

    fun getCurrentTimeString(): String {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return month.toString() + day.toString() + hour.toString() + minute.toString()
    }

    fun calculateMagicNumber(): String {
        val targetStr = getCurrentTimeString()
        val target = try { targetStr.toLong() } catch (_: NumberFormatException) { 0L }
        val sum = try { sumOfTwo.toLong() } catch (_: NumberFormatException) { 0L }
        return (target - sum).toString()
    }

    fun resetMagic() {
        isMagicMode = false
        magicStep = 0
        sumOfTwo = ""
        magicNumber = ""
        isPhoneFlipped = false
        magicNumberShown = false
    }

    // 格式化数字添加千位分隔符
    fun formatNumberWithCommas(number: String): String {
        if (number.isEmpty() || number == "0" || number == "Error") return number

        return try {
            val isNegative = number.startsWith("-")
            val absNumber = if (isNegative) number.substring(1) else number

            val parts = absNumber.split(".")
            val integerPart = parts[0]
            val decimalPart = if (parts.size > 1) "." + parts[1] else ""

            val formattedInteger = if (integerPart.matches(Regex("\\d+"))) {
                integerPart.reversed().chunked(3).joinToString(",").reversed()
            } else {
                integerPart
            }

            (if (isNegative) "-" else "") + formattedInteger + decimalPart
        } catch (_: Exception) {
            number
        }
    }

    // 构建当前表达式字符串（带格式化）
    fun currentFormattedExpression(): String = when {
        operator.isEmpty() -> formatNumberWithCommas(firstNumber)
        secondNumber.isEmpty() -> formatNumberWithCommas(firstNumber) + operator
        else -> formatNumberWithCommas(firstNumber) + operator + formatNumberWithCommas(secondNumber)
    }

    // 判断是否需要显示实时结果
    fun shouldShowLiveResult(): Boolean = operator.isNotEmpty() && secondNumber.isNotEmpty()

    // 字体自适应函数
    fun getAdaptiveFontSize(text: String, isGrayMode: Boolean): Float {
        if (isGrayMode) return 32f

        return when {
            text.length <= 8 -> 64f
            text.length <= 10 -> 58f
            text.length <= 11 -> 52f
            text.length <= 12 -> 46f
            text.length <= 13 -> 40f
            text.length <= 14 -> 32f
            else -> 32f
        }
    }

    // ========== 核心交互函数 ==========
    fun onNumberClick(number: String) {
        if (isMagicMode) {
            if (isPhoneFlipped && !magicNumberShown) {
                magicNumber = calculateMagicNumber()
                displayText = magicNumber
                secondNumber = magicNumber
                magicNumberShown = true
                magicStep = 6
                return
            }
            if (magicNumberShown) return

            when (magicStep) {
                0 -> magicStep = 1
                2 -> magicStep = 3
            }
        }

        if (showResultMode) {
            showResultMode = false
            firstNumber = ""
            secondNumber = ""
            operator = ""
            isNewOperation = true
        }

        if (displayText == "Error") return

        val newDisplay = if (isNewOperation) {
            isNewOperation = false
            number
        } else {
            if (displayText == "0") number else displayText + number
        }
        displayText = newDisplay

        if (operator.isEmpty()) {
            firstNumber = newDisplay
        } else {
            secondNumber = newDisplay
        }
    }

    fun onOperatorClick(op: String) {
        if (isMagicMode) {
            if (isPhoneFlipped && !magicNumberShown) {
                magicNumber = calculateMagicNumber()
                displayText = magicNumber
                secondNumber = magicNumber
                magicNumberShown = true
                magicStep = 6
                return
            }
            if (magicNumberShown) return

            when (magicStep) {
                1 -> magicStep = 2
                4 -> {
                    magicStep = 5
                    isPhoneFlipped = true
                    if (showResultMode) showResultMode = false
                    firstNumber = displayText
                    secondNumber = ""
                    operator = op
                    isNewOperation = true
                    return
                }
            }
        }

        if (showResultMode) {
            showResultMode = false
            firstNumber = displayText
            secondNumber = ""
            operator = op
            isNewOperation = true
            return
        }

        if (displayText == "Error") return

        if (firstNumber.isNotEmpty() && operator.isNotEmpty() && secondNumber.isNotEmpty()) {
            val result = calculateResult(firstNumber, secondNumber, operator)
            displayText = result
            firstNumber = result
            secondNumber = ""
        } else if (firstNumber.isEmpty()) {
            firstNumber = displayText
        }
        operator = op
        isNewOperation = true
    }

    fun onEqualsClick() {
        if (displayText == "Error") return

        if (isMagicMode) {
            when (magicStep) {
                3 -> {
                    if (firstNumber.isNotEmpty() && operator.isNotEmpty() && secondNumber.isNotEmpty()) {
                        val result = calculateResult(firstNumber, secondNumber, operator)
                        sumOfTwo = result
                        displayText = result
                        resultExpression = "${formatNumberWithCommas(firstNumber)}$operator${formatNumberWithCommas(secondNumber)}"
                        firstNumber = result
                        secondNumber = ""
                        operator = ""
                        isNewOperation = true
                        magicStep = 4
                        showResultMode = true
                        // 标记下次字体变化需要动画
                        useUpperAnimation = true
                        useResultAnimation = true
                    }
                    return
                }
                6 -> {
                    val magicNum = try { magicNumber.toLong() } catch (_: Exception) { 0L }
                    val sum = try { sumOfTwo.toLong() } catch (_: Exception) { 0L }
                    val finalResult = (magicNum + sum).toString()

                    displayText = finalResult
                    resultExpression = "${formatNumberWithCommas(sumOfTwo)}+${formatNumberWithCommas(magicNumber)}"
                    firstNumber = finalResult
                    secondNumber = ""
                    operator = ""
                    isNewOperation = true
                    showResultMode = true
                    magicStep = 7
                    // 标记下次字体变化需要动画
                    useUpperAnimation = true
                    useResultAnimation = true
                    return
                }
            }
        }

        if (firstNumber.isNotEmpty() && operator.isNotEmpty()) {
            if (secondNumber.isEmpty()) secondNumber = firstNumber
            val result = calculateResult(firstNumber, secondNumber, operator)
            resultExpression = "${formatNumberWithCommas(firstNumber)}$operator${formatNumberWithCommas(secondNumber)}"
            displayText = result
            if (result != "Error") {
                firstNumber = result
                secondNumber = ""
                operator = ""
                isNewOperation = true
                showResultMode = true
                // 标记下次字体变化需要动画
                useUpperAnimation = true
                useResultAnimation = true
            }
        }
    }

    fun onBackspaceClick() {
        if (isMagicMode) {
            if (isPhoneFlipped && !magicNumberShown) {
                magicNumber = calculateMagicNumber()
                displayText = magicNumber
                secondNumber = magicNumber
                magicNumberShown = true
                magicStep = 6
                return
            }
            if (magicNumberShown) return
        }

        if (showResultMode) {
            showResultMode = false
            firstNumber = ""
            secondNumber = ""
            operator = ""
            isNewOperation = true
            displayText = "0"
            return
        }

        if (displayText == "Error" || displayText == "0") return

        if (displayText.length > 1) {
            displayText = displayText.substring(0, displayText.length - 1)
        } else {
            displayText = "0"
            isNewOperation = true
        }

        if (operator.isEmpty()) {
            firstNumber = displayText
        } else {
            secondNumber = displayText
        }
    }

    fun onDotClick() {
        if (displayText != "Error" && !displayText.contains(".")) {
            if (isMagicMode && isPhoneFlipped && !magicNumberShown) {
                magicNumber = calculateMagicNumber()
                displayText = magicNumber
                secondNumber = magicNumber
                magicNumberShown = true
                magicStep = 6
                return
            }
            if (magicNumberShown) return

            if (showResultMode) {
                showResultMode = false
                firstNumber = ""
                secondNumber = ""
                operator = ""
                isNewOperation = true
            }

            displayText = if (isNewOperation) "0." else "$displayText."
            if (operator.isEmpty()) {
                firstNumber = displayText
            } else {
                secondNumber = displayText
            }
            isNewOperation = false
        }
    }

    // ========== 字体大小目标值（转为可观测状态） ==========
    val targetUpperFontSize by remember {
        derivedStateOf {
            val text = when {
                showResultMode -> resultExpression
                magicStep == 6 && magicNumberShown ->
                    "${formatNumberWithCommas(sumOfTwo)}+${formatNumberWithCommas(magicNumber)}"
                else -> currentFormattedExpression()
            }
            getAdaptiveFontSize(text, showResultMode)
        }
    }

    val targetResultFontSize by remember {
        derivedStateOf {
            if (showResultMode) 64f else 32f
        }
    }

    val resultText by remember {
        derivedStateOf {
            if (showResultMode) {
                try {
                    NumberFormat.getNumberInstance(Locale.US).format(displayText.toDouble())
                } catch (_: Exception) {
                    displayText
                }
            } else if (magicStep == 6 && magicNumberShown) {
                try {
                    val magicNum = magicNumber.toLong()
                    val sum = sumOfTwo.toLong()
                    NumberFormat.getNumberInstance(Locale.US).format((magicNum + sum).toDouble())
                } catch (_: Exception) {
                    ""
                }
            } else {
                if (shouldShowLiveResult()) {
                    try {
                        val res = calculateResult(firstNumber, secondNumber, operator)
                        NumberFormat.getNumberInstance(Locale.US).format(res.toDouble())
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }
            }
        }
    }

    // 创建 Animatable 并监听目标值变化
    val upperFontSizeAnim = remember { Animatable(targetUpperFontSize) }
    val resultFontSizeAnim = remember { Animatable(targetResultFontSize) }

    // 启动协程监听目标值变化并执行动画或快照
    LaunchedEffect(Unit) {
        launch {
            snapshotFlow { targetUpperFontSize }.collectLatest { target ->
                try {
                    if (useUpperAnimation) {
                        upperFontSizeAnim.animateTo(
                            target,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
                    } else {
                        upperFontSizeAnim.snapTo(target)
                    }
                } finally {
                    useUpperAnimation = false
                }
            }
        }
        launch {
            snapshotFlow { targetResultFontSize }.collectLatest { target ->
                try {
                    if (useResultAnimation) {
                        resultFontSizeAnim.animateTo(
                            target,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
                    } else {
                        resultFontSizeAnim.snapTo(target)
                    }
                } finally {
                    useResultAnimation = false
                }
            }
        }
    }

    // ========== UI 布局 ==========
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 顶部图标区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon_history),
                    contentDescription = "历史记录",
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_scientific),
                        contentDescription = "科学计算器",
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { }
                    )

                    // 魔术开关按钮
                    Image(
                        painter = painterResource(id = R.drawable.icon_currency),
                        contentDescription = "魔术开关",
                        modifier = Modifier
                            .size(26.dp)
                            .clickable {
                                isMagicMode = !isMagicMode
                                if (!isMagicMode) resetMagic()
                            }
                    )

                    Image(
                        painter = painterResource(id = R.drawable.icon_menu),
                        contentDescription = "菜单",
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { }
                    )
                }
            }
        }

        // 底部计算器区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 上行表达式 + 光标
            val isUpperGrayMode = showResultMode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isUpperGrayMode) 56.dp else 84.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 0.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = if (isUpperGrayMode) Alignment.CenterVertically else Alignment.Bottom
                ) {
                    // 使用动画值
                    Text(
                        text = when {
                            showResultMode -> resultExpression
                            magicStep == 6 && magicNumberShown ->
                                "${formatNumberWithCommas(sumOfTwo)}+${formatNumberWithCommas(magicNumber)}"
                            else -> currentFormattedExpression()
                        },
                        color = if (isUpperGrayMode) Color(0xFF999999) else Color.White,
                        fontSize = upperFontSizeAnim.value.sp,
                        fontWeight = if (isUpperGrayMode) FontWeight.Normal else FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = if (isUpperGrayMode) 0.dp else 8.dp)
                    )

                    // 光标
                    if (!showResultMode && magicStep != 6 && cursorVisible) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(upperFontSizeAnim.value.dp)
                                .then(
                                    if (!isUpperGrayMode) {
                                        Modifier.align(Alignment.Bottom).offset(y = (-8).dp)
                                    } else {
                                        Modifier.align(Alignment.CenterVertically)
                                    }
                                )
                                .background(Color.Red)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                }
            }

            // 下行结果
            Text(
                text = resultText,
                color = if (showResultMode) Color.White else Color(0xFF999999),
                fontSize = resultFontSizeAnim.value.sp,
                fontWeight = if (showResultMode) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )

            // 按键区域（保持不变）
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 第一行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable {
                        displayText = "0"
                        firstNumber = ""
                        secondNumber = ""
                        operator = ""
                        isNewOperation = true
                        showResultMode = false
                        resetMagic()
                    }, contentAlignment = Alignment.Center) {
                        Text("AC", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable {
                        if (displayText != "Error") {
                            try {
                                val value = displayText.toDouble()
                                displayText = (value / 100).toString()
                                if (operator.isEmpty()) {
                                    firstNumber = displayText
                                } else {
                                    secondNumber = displayText
                                }
                            } catch (_: Exception) {}
                        }
                        showResultMode = false
                    }, contentAlignment = Alignment.Center) {
                        Text("%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onBackspaceClick() }, contentAlignment = Alignment.Center) {
                        Text("⌫", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onOperatorClick("÷") }, contentAlignment = Alignment.Center) {
                        Text("÷", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 第二行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("7") }, contentAlignment = Alignment.Center) {
                        Text("7", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("8") }, contentAlignment = Alignment.Center) {
                        Text("8", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("9") }, contentAlignment = Alignment.Center) {
                        Text("9", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onOperatorClick("×") }, contentAlignment = Alignment.Center) {
                        Text("×", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 第三行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("4") }, contentAlignment = Alignment.Center) {
                        Text("4", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("5") }, contentAlignment = Alignment.Center) {
                        Text("5", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("6") }, contentAlignment = Alignment.Center) {
                        Text("6", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onOperatorClick("-") }, contentAlignment = Alignment.Center) {
                        Text("−", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 第四行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("1") }, contentAlignment = Alignment.Center) {
                        Text("1", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("2") }, contentAlignment = Alignment.Center) {
                        Text("2", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("3") }, contentAlignment = Alignment.Center) {
                        Text("3", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onOperatorClick("+") }, contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 第五行
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("00") }, contentAlignment = Alignment.Center) {
                        Text("00", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onNumberClick("0") }, contentAlignment = Alignment.Center) {
                        Text("0", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFF333333)).clickable { onDotClick() }, contentAlignment = Alignment.Center) {
                        Text(".", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(0xFFFF9500)).clickable { onEqualsClick() }, contentAlignment = Alignment.Center) {
                        Text("＝", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}