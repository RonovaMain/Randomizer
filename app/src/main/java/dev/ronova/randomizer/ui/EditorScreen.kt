@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun EditorScreen(vm: MainVm, nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Этапы", "Списки", "Элементы", "Правила", "Профили")
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Редактор") },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    },
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    titles.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> StagesTab(vm)
                1 -> ListsTab(vm)
                2 -> ElementsTab(vm)
                3 -> RulesTab(vm)
                else -> ProfilesTab(vm)
            }
        }
    }
}

// ---------------------------------------------------------------- Этапы

@Composable
private fun StagesTab(vm: MainVm) {
    val stages by vm.stages.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val listName = lists.associate { it.id to it.name }

    var local by remember { mutableStateOf(stages) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(stages) { if (!dragging) local = stages }

    val lazy = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(lazy) { from, to ->
        local = local.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    var editing by remember { mutableStateOf<StageEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<StageEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (local.isEmpty()) {
            Text(
                "Этапов нет. Добавьте первый — это шаг каскада.",
                Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        LazyColumn(
            state = lazy,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(local, key = { it.id }) { st ->
                ReorderableItem(reorder, key = st.id) { _ ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DragHandle, "Перетащить",
                                Modifier
                                    .draggableHandle(
                                        onDragStarted = { dragging = true },
                                        onDragStopped = { dragging = false; vm.reorderStages(local) },
                                    )
                                    .padding(12.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(st.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Список: " + (st.listId?.let { listName[it] } ?: "все"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { editing = st }) { Icon(Icons.Default.Edit, "Изменить") }
                            IconButton(onClick = { deleting = st }) { Icon(Icons.Default.Delete, "Удалить") }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Добавить этап") }
    }

    if (adding) {
        StageDialog(null, lists, { adding = false }) { name, listId ->
            vm.saveStage(StageEntity(newId(), name, stages.size, listId))
            adding = false
        }
    }
    editing?.let { st ->
        StageDialog(st, lists, { editing = null }) { name, listId ->
            vm.saveStage(st.copy(name = name, listId = listId))
            editing = null
        }
    }
    deleting?.let { st ->
        ConfirmDialog("Удалить этап?", "«${st.name}» будет удалён.", "Удалить", { deleting = null }) {
            vm.deleteStage(st.id)
            deleting = null
        }
    }
}

@Composable
private fun StageDialog(
    initial: StageEntity?,
    lists: List<ListEntity>,
    onDismiss: () -> Unit,
    onOk: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var listId by remember { mutableStateOf(initial?.listId ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый этап" else "Этап") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Название") })
                Picker(
                    "Базовый список",
                    if (listId.isEmpty()) "Все списки" else lists.firstOrNull { it.id == listId }?.name ?: "—",
                    listOf("" to "Все списки") + lists.map { it.id to it.name },
                ) { listId = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(name.trim(), listId.ifEmpty { null }) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---------------------------------------------------------------- Списки

@Composable
private fun ListsTab(vm: MainVm) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val elements by vm.elements.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ListEntity?>(null) }
    var deleting by remember { mutableStateOf<ListEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (lists.isEmpty()) {
            Text("Списков нет. Список — это группа элементов для этапа.", Modifier.align(Alignment.Center).padding(24.dp))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lists, key = { it.id }) { l ->
                val count = elements.count { it.listId == l.id }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text("Элементов: $count", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = l }) { Icon(Icons.Default.Edit, "Переименовать") }
                        IconButton(onClick = { deleting = l }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Добавить список") }
    }

    if (adding) {
        NameDialog("Новый список", "", { adding = false }) {
            vm.saveList(ListEntity(newId(), it, lists.size))
            adding = false
        }
    }
    editing?.let { l ->
        NameDialog("Переименовать список", l.name, { editing = null }) {
            vm.saveList(l.copy(name = it))
            editing = null
        }
    }
    deleting?.let { l ->
        val count = elements.count { it.listId == l.id }
        ConfirmDialog(
            "Удалить список?",
            "«${l.name}» и все его элементы ($count) будут удалены вместе с их фото и правилами.",
            "Удалить",
            { deleting = null },
        ) {
            vm.deleteList(l.id)
            deleting = null
        }
    }
}
