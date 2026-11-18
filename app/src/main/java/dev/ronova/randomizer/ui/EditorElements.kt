@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.*
import kotlinx.coroutines.launch

@Composable
fun ElementsTab(vm: MainVm) {
    val elements by vm.elements.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var query by remember { mutableStateOf("") }
    var listFilter by remember { mutableStateOf("") }
    var tagFilter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ElementEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val listName = lists.associate { it.id to it.name }
    val allTags = remember(elements) { elements.flatMap { it.tagList() }.distinct().sorted() }
    val shown = elements.filter {
        (listFilter.isEmpty() || it.listId == listFilter) &&
            (tagFilter.isEmpty() || tagFilter in it.tagList()) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query, { query = it },
                    singleLine = true,
                    label = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker(
                        "Список",
                        if (listFilter.isEmpty()) "Все" else listName[listFilter] ?: "—",
                        listOf("" to "Все") + lists.map { it.id to it.name },
                        Modifier.weight(1f),
                    ) { listFilter = it }
                    Picker(
                        "Тег",
                        if (tagFilter.isEmpty()) "Все" else tagFilter,
                        listOf("" to "Все") + allTags.map { it to it },
                        Modifier.weight(1f),
                    ) { tagFilter = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(onClick = { vm.setActive(shown.map { it.id }, true) }, label = { Text("Вкл. показанные") })
                    AssistChip(onClick = { vm.setActive(shown.map { it.id }, false) }, label = { Text("Выкл. показанные") })
                    Text("${shown.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth().clickable { editing = e }) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Thumb(e.mainPhoto, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                val tags = e.tagList()
                                Text(
                                    (listName[e.listId] ?: "—") + if (tags.isNotEmpty()) " · " + tags.joinToString(" ") { "#$it" } else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            Checkbox(checked = e.active, onCheckedChange = { vm.setActive(listOf(e.id), it) })
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                if (lists.isEmpty()) Toast.makeText(ctx, "Сначала создайте список", Toast.LENGTH_SHORT).show()
                else creating = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Добавить элемент") }
    }

    if (creating) {
        ElementEditorDialog(vm, null, lists, listFilter.ifEmpty { lists.first().id }) { creating = false }
    }
    editing?.let { e -> ElementEditorDialog(vm, e, lists, e.listId) { editing = null } }
}

@Composable
private fun ElementEditorDialog(
    vm: MainVm,
    initial: ElementEntity?,
    lists: List<ListEntity>,
    defaultListId: String,
    onClose: () -> Unit,
) {
    val photos = LocalPhotos.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var tags by remember { mutableStateOf(initial?.tags ?: "") }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var listId by remember { mutableStateOf(defaultListId) }
    var main by remember { mutableStateOf(initial?.mainPhoto) }
    var gallery by remember { mutableStateOf(initial?.galleryList() ?: emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickMain = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            photos.importFromUri(uri)?.let { main = it }
            busy = false
        }
    }
    val pickGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            uris.forEach { u -> photos.importFromUri(u)?.let { n -> gallery = gallery + n } }
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (initial == null) "Новый элемент" else "Элемент") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") } },
                    actions = {
                        if (initial != null) {
                            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                        }
                        TextButton(
                            enabled = name.isNotBlank() && !busy,
                            onClick = {
                                vm.saveElement(
                                    ElementEntity(
                                        id = initial?.id ?: newId(),
                                        listId = listId,
                                        name = name.trim(),
                                        mainPhoto = main,
                                        text = text,
                                        gallery = gallery.joinToString("|"),
                                        tags = tags.csv().distinct().joinToString(","),
                                        active = active,
                                    )
                                )
                                onClose()
                            },
                        ) { Text("Сохранить") }
                    },
                )
            },
        ) { pad ->
            Column(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Picker("Список", lists.firstOrNull { it.id == listId }?.name ?: "—", lists.map { it.id to it.name }) { listId = it }

                Text("Главное фото", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Thumb(main, 96.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { pickMain.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (main == null) "Выбрать" else "Заменить")
                        }
                        if (main != null) TextButton(onClick = { main = null }) { Text("Убрать") }
                    }
                }

                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Текст шпаргалки") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Галерея референсов", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(gallery, key = { it }) { g ->
                        Box {
                            Thumb(g, 84.dp)
                            Icon(
                                Icons.Default.Close, "Убрать",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .clickable { gallery = gallery - g }
                                    .padding(3.dp),
                            )
                        }
                    }
                    item {
                        OutlinedButton(onClick = { pickGallery.launch("image/*") }, modifier = Modifier.height(84.dp)) {
                            Icon(Icons.Default.AddPhotoAlternate, null)
                        }
                    }
                }

                OutlinedTextField(
                    tags, { tags = it },
                    label = { Text("Теги через запятую") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(12.dp))
                    Text("Участвует в рулетке")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDelete && initial != null) {
        ConfirmDialog("Удалить элемент?", "«${initial.name}» и правила с ним будут удалены.", "Удалить", { confirmDelete = false }) {
            confirmDelete = false
            vm.deleteElement(initial.id)
            onClose()
        }
    }
}
