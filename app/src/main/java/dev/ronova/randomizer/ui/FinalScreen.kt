@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.logic.Session

@Composable
fun FinalScreen(vm: MainVm, nav: NavHostController) {
    val s = vm.session
    if (s == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    FinalContent(vm, nav, s)
}

@Composable
private fun FinalContent(vm: MainVm, nav: NavHostController, s: Session) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.savedToHistory) {
        if (vm.savedToHistory) snack.showSnackbar("Сохранено в историю")
    }
    val cheat = s.stages.mapIndexedNotNull { i, st -> s.results[i]?.let { st.name to it } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Итог · ${s.profileName}") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack("main", false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "На главный")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.rerollUnlocked() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Перекрутить", maxLines = 1)
                    }
                    Button(
                        onClick = { vm.saveHistory() },
                        enabled = !vm.savedToHistory,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (vm.savedToHistory) "Сохранено" else "В историю", maxLines = 1)
                    }
                }
            }
        },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                    ) { item { Summary(vm, s) } }
                    LazyColumn(
                        Modifier.weight(1.2f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Text("Шпаргалка", style = MaterialTheme.typography.titleMedium) }
                        items(cheat) { (title, e) -> CheatBlock(e, title = title) }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Summary(vm, s) }
                    item { Text("Шпаргалка", style = MaterialTheme.typography.titleMedium) }
                    items(cheat) { (title, e) -> CheatBlock(e, title = title) }
                }
            }
        }
    }
}

@Composable
private fun Summary(vm: MainVm, s: Session) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Выпало", style = MaterialTheme.typography.titleMedium)
            s.stages.forEachIndexed { i, st ->
                val e = s.results[i]
                val locked = i in s.locks
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumb(e?.mainPhoto, 44.dp)
                    Column(Modifier.weight(1f)) {
                        Text(st.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(e?.name ?: "— нет доступных —", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { vm.toggleLock(i) }) {
                        Icon(
                            if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
                            if (locked) "Зафиксировано" else "Зафиксировать",
                            tint = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Text(
                "Замочек фиксирует пункт: «Перекрутить» заменит только незафиксированные.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
