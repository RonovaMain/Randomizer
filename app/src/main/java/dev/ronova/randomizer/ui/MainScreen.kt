@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.*

@Composable
fun MainScreen(vm: MainVm, nav: NavHostController) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val elements by vm.elements.collectAsStateWithLifecycle()
    LaunchedEffect(profiles) { vm.ensureProfile(profiles) }
    val fb = vm.feedback

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Randomizer", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { fb.setVibration(!fb.vibration) }) {
                        Icon(
                            Icons.Default.Vibration, "Вибро",
                            tint = if (fb.vibration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = { fb.setSound(!fb.sound) }) {
                        Icon(
                            if (fb.sound) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Звук",
                            tint = if (fb.sound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
            )
        },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Wheel(List(8) { "" }, { 0f }, Modifier.weight(1f).aspectRatio(1f))
                    Controls(vm, nav, profiles, stages, elements, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Wheel(List(8) { "" }, { 0f }, Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f))
                    Controls(vm, nav, profiles, stages, elements, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Controls(
    vm: MainVm,
    nav: NavHostController,
    profiles: List<ProfileEntity>,
    stages: List<StageEntity>,
    elements: List<ElementEntity>,
    modifier: Modifier,
) {
    val ctx = LocalContext.current
    val profile = profiles.firstOrNull { it.id == vm.selectedProfileId }
    val activeIds = profile?.activeStageIds?.csv()?.toSet() ?: emptySet()
    val activeStages = stages.filter { it.id in activeIds }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (profiles.isEmpty()) {
            Text("Профилей пока нет. Создайте профиль в Редакторе.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Picker("Профиль", profile?.name ?: "", profiles.map { it.id to it.name }) { id ->
                profiles.firstOrNull { it.id == id }?.let { vm.selectProfile(it) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = vm.mode == "CASCADE",
                    onClick = { vm.mode = "CASCADE" },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Каскад") }
                SegmentedButton(
                    selected = vm.mode == "SINGLE",
                    onClick = { vm.mode = "SINGLE" },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Одиночный") }
            }
            if (vm.mode == "SINGLE") {
                val current = stages.firstOrNull { it.id == vm.singleStageId } ?: activeStages.firstOrNull()
                Picker("Этап", current?.name ?: "—", stages.map { it.id to it.name }) { vm.singleStageId = it }
            } else if (activeStages.isEmpty()) {
                Text("В профиле нет активных этапов.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeStages.forEachIndexed { i, st ->
                        AssistChip(onClick = {}, label = { Text("${i + 1}. ${st.name}") })
                    }
                }
            }
            Text(
                "Активных элементов: ${elements.count { it.active }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = {
                val err = vm.startSession()
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show() else nav.navigate("spin")
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Default.Casino, null)
            Spacer(Modifier.width(8.dp))
            Text("Крутить", fontSize = 18.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { nav.navigate("history") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Icon(Icons.Default.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("История", maxLines = 1) }
            OutlinedButton(
                onClick = { nav.navigate("editor") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Редактор", maxLines = 1) }
            OutlinedButton(
                onClick = { nav.navigate("transfer") },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Перенос", maxLines = 1) }
        }
    }
}
