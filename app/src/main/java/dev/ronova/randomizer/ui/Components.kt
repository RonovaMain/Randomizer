@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.ronova.randomizer.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val LocalPhotos = staticCompositionLocalOf<PhotoStore> { error("PhotoStore not provided") }

val WheelPalette = listOf(
    Color(0xFFE8457C), Color(0xFFF2A100), Color(0xFF7C4DFF), Color(0xFF00B386),
    Color(0xFFEF6C4D), Color(0xFF2E9BEA), Color(0xFF8BC34A), Color(0xFFB04FD6),
)

fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")).format(Date(ms))

/** Колесо. Указатель сверху (270°), вращение читается в фазе рисования — без перекомпозиции. */
@Composable
fun Wheel(labels: List<String>, rotation: () -> Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f * 0.88f
        val n = labels.size
        drawCircle(Color(0xFF1E1637), radius = r + 6.dp.toPx(), center = c)
        rotate(rotation(), pivot = c) {
            if (n == 0) {
                drawCircle(Color(0xFF8A8499), radius = r, center = c)
            } else {
                val sweep = 360f / n
                labels.forEachIndexed { i, label ->
                    drawArc(
                        color = WheelPalette[i % WheelPalette.size],
                        startAngle = i * sweep,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(2 * r, 2 * r),
                    )
                    if (label.isNotBlank()) {
                        rotate(i * sweep + sweep / 2f, pivot = c) {
                            val layout = measurer.measure(
                                AnnotatedString(label),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = (if (n > 10) 11 else 14).sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                constraints = Constraints(maxWidth = (r * 0.62f).toInt().coerceAtLeast(1)),
                            )
                            drawText(layout, topLeft = Offset(c.x + r * 0.30f, c.y - layout.size.height / 2f))
                        }
                    }
                }
                if (n > 1) {
                    for (i in 0 until n) {
                        val a = Math.toRadians((i * sweep).toDouble())
                        drawLine(
                            Color.White.copy(alpha = 0.6f), c,
                            Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                            strokeWidth = 2f,
                        )
                    }
                }
            }
        }
        drawCircle(Color(0xFF1E1637), radius = r * 0.14f, center = c)
        drawCircle(Color.White, radius = r * 0.08f, center = c)
        val pointer = Path().apply {
            moveTo(c.x - r * 0.08f, c.y - r - r * 0.07f)
            lineTo(c.x + r * 0.08f, c.y - r - r * 0.07f)
            lineTo(c.x, c.y - r + r * 0.10f)
            close()
        }
        drawPath(pointer, Color(0xFFFFC857))
        drawPath(pointer, Color.White, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Для больших пулов показываем на колесе не больше 16 секторов (победитель всегда среди них). */
fun buildDisplay(pool: List<ElementEntity>, winner: ElementEntity?): List<ElementEntity> {
    if (pool.size <= 16) return pool
    val others = pool.filter { it != winner }.shuffled()
    return if (winner != null) (others.take(15) + winner).shuffled() else others.take(16)
}

@Composable
fun Thumb(name: String?, size: Dp, modifier: Modifier = Modifier) {
    val photos = LocalPhotos.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            AsyncImage(
                model = File(photos.dir, name),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Выпадающий список без экспериментальных API. */
@Composable
fun <T> Picker(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Название") })
        },
        confirmButton = {
            TextButton(onClick = { onOk(text.trim()) }, enabled = text.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmText: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onOk) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ElementPickerDialog(
    elements: List<ElementEntity>,
    lists: List<ListEntity>,
    onDismiss: () -> Unit,
    onPick: (ElementEntity) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val listName = lists.associate { it.id to it.name }
    val shown = elements.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор элемента") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("Поиск") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { e ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(e) }.padding(vertical = 8.dp)) {
                            Text(e.name, style = MaterialTheme.typography.bodyLarge)
                            Text(listName[e.listId] ?: "—", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
fun ImageViewer(names: List<String>, start: Int, onDismiss: () -> Unit) {
    val photos = LocalPhotos.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start.coerceIn(0, names.lastIndex)) { names.size }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                AsyncImage(
                    model = File(photos.dir, names[i]),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
            if (names.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${names.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                )
            }
        }
    }
}

/** Блок шпаргалки: главное фото, название, текст, галерея референсов (тап — полноэкранный просмотр). */
@Composable
fun CheatBlock(e: ElementEntity, modifier: Modifier = Modifier, title: String? = null) {
    val photos = LocalPhotos.current
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val gallery = e.galleryList()
    val all = listOfNotNull(e.mainPhoto) + gallery
    val offset = if (e.mainPhoto != null) 1 else 0
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(e.name, style = MaterialTheme.typography.titleLarge)
            if (e.mainPhoto != null) {
                AsyncImage(
                    model = File(photos.dir, e.mainPhoto),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewer = all to 0 },
                )
            }
            if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodyLarge)
            if (gallery.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(gallery) { i, name ->
                        Thumb(name, 84.dp, Modifier.clickable { viewer = all to (i + offset) })
                    }
                }
            }
            val tags = e.tagList()
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    viewer?.let { (names, start) -> ImageViewer(names, start) { viewer = null } }
}
