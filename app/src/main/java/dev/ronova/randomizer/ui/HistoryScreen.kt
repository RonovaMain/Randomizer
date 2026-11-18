@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.*
import java.io.File

@Composable
fun HistoryScreen(vm: MainVm, nav: NavHostController) {
    val history by vm.history.collectAsStateWithLifecycle()
    var openId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        if (history.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Пока пусто. Сохраните результат на итоговом экране.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(340.dp),
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { it.id }) { h -> HistoryCard(h) { openId = h.id } }
            }
        }
    }

    history.firstOrNull { it.id == openId }?.let { HistoryDialog(vm, it) { openId = null } }
}

@Composable
private fun HistoryCard(h: HistoryEntity, onClick: () -> Unit) {
    val photos = LocalPhotos.current
    val items = parseHistoryItems(h.items)
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatDate(h.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "${h.profileName} · ${if (h.mode == "SINGLE") "одиночный" else "каскад"}",
                style = MaterialTheme.typography.titleMedium,
            )
            items.take(6).forEach { Text("${it.stage}: ${it.name}", style = MaterialTheme.typography.bodyMedium) }
            if (items.size > 6) Text("…ещё ${items.size - 6}", style = MaterialTheme.typography.bodySmall)
            if (h.photo != null) {
                AsyncImage(
                    model = File(photos.dir, h.photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@Composable
private fun HistoryDialog(vm: MainVm, h: HistoryEntity, onClose: () -> Unit) {
    val photos = LocalPhotos.current
    val elements by vm.elements.collectAsStateWithLifecycle()
    val byId = remember(elements) { elements.associateBy { it.id } }
    val entries = parseHistoryItems(h.items)
    var viewer by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.setHistoryPhoto(h.id, uri)
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(formatDate(h.createdAt)) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") } },
                    actions = {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                    },
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.padding(pad).fillMaxSize().widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(h.profileName, style = MaterialTheme.typography.titleLarge) }
                item {
                    Text("Фотоотчёт", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (h.photo != null) {
                        AsyncImage(
                            model = File(photos.dir, h.photo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewer = true },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (h.photo == null) "Добавить фото" else "Заменить фото")
                        }
                        if (h.photo != null) {
                            TextButton(onClick = { vm.setHistoryPhoto(h.id, null) }) { Text("Убрать") }
                        }
                    }
                }
                item { Text("Шпаргалка", style = MaterialTheme.typography.titleMedium) }
                listItems(entries) { row ->
                    val e = byId[row.elementId]
                    if (e != null) {
                        CheatBlock(e, title = row.stage)
                    } else {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.stage, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(row.name, style = MaterialTheme.typography.titleMedium)
                                if (row.elementId.isNotEmpty()) {
                                    Text("Элемент удалён из базы", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (viewer && h.photo != null) ImageViewer(listOf(h.photo), 0) { viewer = false }
    if (confirmDelete) {
        ConfirmDialog("Удалить запись?", "Запись и её фотоотчёт будут удалены.", "Удалить",
            { confirmDelete = false }) {
            confirmDelete = false
            vm.deleteHistory(h.id)
            onClose()
        }
    }
}
