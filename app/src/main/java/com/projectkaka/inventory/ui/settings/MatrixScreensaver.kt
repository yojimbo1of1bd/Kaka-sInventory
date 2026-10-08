package com.projectkaka.inventory.ui.settings

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.random.Random

/**
 * MatrixScreensaver: Authentic terminal digital rain screensaver (cmatrix).
 *
 * Streams falling columns of Chinese characters, Japanese Katakana, English hacker words,
 * and numbers/symbols on a deep black canvas with glowing white/neon heads and fading tails.
 * Dismisses smoothly on any user tap.
 */
@Composable
fun MatrixScreensaver(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fontSizePx = with(density) { 16.sp.toPx() }
    val colSpacingPx = with(density) { 20.dp.toPx() }

    // Curated Chinese and English words + Katakana & binary glyphs
    val wordPool = remember {
        listOf(
            "KAKA", "ROOT", "CYBER", "MATRIX", "SHA256", "HASH", "ZERO", "BYTE",
            "VOID", "EXEC", "LOCK", "DATA", "FLOW", "DEBIT", "CREDIT", "SYSTEM",
            "密码", "密钥", "终端", "数据", "黑客", "矩阵", "极客", "乾坤",
            "神龙", "天网", "源码", "智能", "元神", "虚无", "混沌", "天地", "日月"
        )
    }

    val glyphPool = remember {
        val katakana = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ"
        val symbols = "01010189$৳#*+-%:<=>{}[]~!?^&/\\"
        (katakana + symbols).toCharArray()
    }

    // Function to generate a column sequence containing both words and glyphs
    fun generateColumnChars(length: Int): MutableList<Char> {
        val result = mutableListOf<Char>()
        while (result.size < length) {
            if (Random.nextInt(3) == 0) {
                // Insert a Chinese or English word
                val word = wordPool[Random.nextInt(wordPool.size)]
                for (ch in word) {
                    if (result.size < length) result.add(ch)
                }
            } else {
                // Insert a random glyph
                result.add(glyphPool[Random.nextInt(glyphPool.size)])
            }
        }
        return result
    }

    class RainColumn(
        val x: Float,
        var y: Float,
        var speed: Float,
        var length: Int,
        val chars: MutableList<Char>
    )

    var frameTick by remember { mutableLongStateOf(0L) }
    val columns = remember { mutableListOf<RainColumn>() }

    val headPaint = remember {
        AndroidPaint().apply {
            color = AndroidColor.WHITE
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(14f, 0f, 0f, AndroidColor.parseColor("#39D353"))
        }
    }

    val streamPaint = remember {
        AndroidPaint().apply {
            textSize = fontSizePx
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
    }

    // High performance frame loop (60 / 120 FPS)
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { time ->
                frameTick = time
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    onDismiss()
                }
            }
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            // CRITICAL: Reading frameTick inside the Canvas draw scope forces Compose
            // to redraw on EVERY frame!
            @Suppress("UNUSED_VARIABLE")
            val currentFrame = frameTick

            val width = size.width
            val height = size.height

            // Initialize columns once canvas dimensions are available
            if (columns.isEmpty() && width > 0f && height > 0f) {
                val count = (width / colSpacingPx).toInt() + 1
                for (colIndex in 0 until count) {
                    val len = Random.nextInt(16, 36)
                    columns.add(
                        RainColumn(
                            x = colIndex * colSpacingPx,
                            // Distribute across screen from frame 0 so it rains immediately!
                            y = Random.nextFloat() * height,
                            speed = Random.nextFloat() * 7f + 9f,
                            length = len,
                            chars = generateColumnChars(len)
                        )
                    )
                }
            }

            // Draw and update columns
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val charSpacing = fontSizePx * 1.35f

                columns.forEach { col ->
                    // Mutate characters occasionally for authentic flickering terminal effect
                    if (Random.nextInt(6) == 0 && col.chars.isNotEmpty()) {
                        val mutateIdx = Random.nextInt(col.chars.size)
                        col.chars[mutateIdx] = if (Random.nextBoolean()) {
                            val word = wordPool[Random.nextInt(wordPool.size)]
                            word[Random.nextInt(word.length)]
                        } else {
                            glyphPool[Random.nextInt(glyphPool.size)]
                        }
                    }

                    // Advance column downwards
                    col.y += col.speed
                    val tailY = col.y - (col.length * charSpacing)

                    // Reset column to top when it falls completely off bottom
                    if (tailY > height) {
                        col.y = Random.nextFloat() * -100f
                        col.speed = Random.nextFloat() * 7f + 9f
                        col.length = Random.nextInt(16, 36)
                        col.chars.clear()
                        col.chars.addAll(generateColumnChars(col.length))
                    }

                    // Draw each character in the column
                    col.chars.forEachIndexed { index, char ->
                        val charY = col.y - (index * charSpacing)
                        if (charY in -fontSizePx..(height + fontSizePx)) {
                            if (index == 0) {
                                // Head glyph: bright glowing white
                                native.drawText(char.toString(), col.x, charY, headPaint)
                            } else {
                                // Body gradient: bright neon green fading to dark matrix green
                                val alphaFraction = 1f - (index.toFloat() / col.length.toFloat())
                                val alphaInt = (alphaFraction * 255).coerceIn(25f, 255f).toInt()
                                val greenVal = (210 * alphaFraction + 45).coerceIn(40f, 255f).toInt()

                                streamPaint.color = AndroidColor.argb(alphaInt, 0, greenVal, 35)
                                native.drawText(char.toString(), col.x, charY, streamPaint)
                            }
                        }
                    }
                }
            }
        }

        // Floating HUD pill at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .background(Color(0xCC001408), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "[ CMATRIX DIGITAL RAIN // TAP ANYWHERE TO EXIT ]",
                color = Color(0xFF39D353),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
