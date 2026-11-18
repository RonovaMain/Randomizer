@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.ElementEntity
import dev.ronova.randomizer.logic.Session
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun SpinScreen(vm: MainVm, nav: NavHostController) {
    val s = vm.session
    if (s == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    SpinContent(vm, nav, s)
}

@Composable
private fun SpinContent(vm: MainVm, nav: NavHostController, s: Session) {
    val elements by vm.elements.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val index = s.index
    val stage = s.stages[index]
    val prev = if (index > 0) s.results[index - 1] else null
    val pool = remember(index, prev, elements, rules) { vm.poolFor(s, index) }
    val last = index == s.stages.lastIndex

    val scope = rememberCoroutineScope()
    val rot = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var winner by remember(index) { mutableStateOf<ElementEntity?>(null) }
    var showResult by remember(index) { mutableStateOf(false) }
    var display by remember(index) { mutableStateOf(buildDisplay(pool, null)) }
    val fb = vm.feedback

    LaunchedEffect(index) { rot.snapTo(0f) }

    // «тики» при проходе секторов под указателем
    LaunchedEffect(spinning) {
        if (spinning) {
            var lastIdx = -1
            snapshotFlow { rot.value }.collect { v ->
                val n = display.size
                if (n > 0) {
                    val sw = 360f / n
                    val idx = ((((270f - v) % 360f) + 360f) % 360f / sw).toInt().coerceIn(0, n - 1)
                    if (idx != lastIdx) {
                        if (lastIdx != -1) fb.tick()
                        lastIdx = idx
                    }
                }
            }
        }
    }

    fun spin() {
        if (spinning || pool.isEmpty()) return
        scope.launch {
            spinning = true
            showResult = false
            val w = pool.random()
            val disp = buildDisplay(pool, w)
            display = disp
            val idx = disp.indexOf(w)
            val sweep = 360f / disp.size
            val jitter = (Random.nextFloat() - 0.5f) * sweep * 0.7f
            val target = 360f * (5 + Random.nextInt(3)) + (270f - (idx + 0.5f) * sweep) + jitter
            rot.snapTo(0f)
            rot.animateTo(target, tween(4600, easing = FastOutSlowInEasing))
            winner = w
            spinning = false
            vm.setResult(index, w)
            fb.done()
            showResult = true
        }
    }

    fun proceed() {
        if (last) nav.navigate("final") { popUpTo("spin") { inclusive = true } } else vm.advance()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${stage.name} · ${index + 1}/${s.stages.size}") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val labels = display.map { it.name }
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wheel(labels, { rot.value }, Modifier.weight(1f).aspectRatio(1f))
                    SpinPanel(
                        s, index, pool.size, spinning,
                        onSpin = { spin() },
                        onSkip = { vm.setResult(index, null); proceed() },
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Wheel(labels, { rot.value }, Modifier.fillMaxWidth().widthIn(max = 420.dp).aspectRatio(1f))
                    SpinPanel(
                        s, index, pool.size, spinning,
                        onSpin = { spin() },
                        onSkip = { vm.setResult(index, null); proceed() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val w = winner
    if (showResult && w != null) {
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
            title = { Text(stage.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { CheatBlock(w) } },
            confirmButton = {
                Button(onClick = { showResult = false; proceed() }) { Text(if (last) "К итогу" else "Далее") }
            },
            dismissButton = {
                TextButton(onClick = { showResult = false; spin() }) { Text("Перекрутить") }
            },
        )
    }
}

@Composable
private fun SpinPanel(
    s: Session,
    index: Int,
    poolSize: Int,
    spinning: Boolean,
    onSpin: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(
            progress = { index.toFloat() / s.stages.size },
            modifier = Modifier.fillMaxWidth(),
        )
        val done = s.results.take(index)
        if (done.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                done.forEachIndexed { i, e ->
                    AssistChip(onClick = {}, label = { Text("${s.stages[i].name}: ${e?.name ?: "—"}") })
                }
            }
        }
        if (poolSize == 0) {
            Text(
                "На этом этапе нет доступных элементов. Проверьте активность элементов, фильтр тегов профиля и правила.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.SkipNext, null)
                Spacer(Modifier.width(8.dp))
                Text("Пропустить этап")
            }
        } else {
            Text(
                "В пуле: $poolSize",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSpin,
                enabled = !spinning,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.Casino, null)
                Spacer(Modifier.width(8.dp))
                Text(if (spinning) "Вращается…" else "Крутить", fontSize = 18.sp)
            }
        }
    }
}
