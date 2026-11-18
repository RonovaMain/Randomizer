@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.randomizer.MainVm
import dev.ronova.randomizer.data.*
import dev.ronova.randomizer.logic.Cascade

// ---------------------------------------------------------------- Правила

@Composable
fun RulesTab(vm: MainVm) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val elements by vm.elements.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val eName = elements.associate { it.id to it.name }
    val lName = lists.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            Text(
                "Правил нет. Подмена списка: «если выпал А — на следующем этапе список Б». Исключение: «если выпал А — на следующем этапе скрыть В».",
                Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rules, key = { it.id }) { r ->
                val trig = eName[r.triggerElementId] ?: "?"
                val line = if (r.type == Cascade.SUBSTITUTE) {
                    "Если выпал «$trig» → на следующем этапе список «${lName[r.targetListId] ?: "?"}»"
                } else {
                    "Если выпал «$trig» → на следующем этапе скрыть «${eName[r.targetElementId] ?: "?"}»"
                }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (r.type == Cascade.SUBSTITUTE) "Подмена списка" else "Исключение",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { vm.deleteRule(r.id) }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Добавить правило") }
    }

    if (adding) {
        RuleDialog(elements, lists, { adding = false }) {
            vm.saveRule(it)
            adding = false
        }
    }
}

@Composable
private fun RuleDialog(
    elements: List<ElementEntity>,
    lists: List<ListEntity>,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit,
) {
    var type by remember { mutableStateOf(Cascade.SUBSTITUTE) }
    var trigger by remember { mutableStateOf<ElementEntity?>(null) }
    var targetList by remember { mutableStateOf("") }
    var targetEl by remember { mutableStateOf<ElementEntity?>(null) }
    var picking by remember { mutableIntStateOf(0) } // 1 — триггер, 2 — скрываемый элемент

    val ready = trigger != null &&
        ((type == Cascade.SUBSTITUTE && targetList.isNotEmpty()) || (type == Cascade.EXCLUDE && targetEl != null))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое правило") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == Cascade.SUBSTITUTE, { type = Cascade.SUBSTITUTE }, { Text("Подмена списка") })
                    FilterChip(type == Cascade.EXCLUDE, { type = Cascade.EXCLUDE }, { Text("Исключение") })
                }
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.fillMaxWidth()) {
                    Text(trigger?.let { "Если выпал: ${it.name}" } ?: "Выбрать элемент-триггер")
                }
                if (type == Cascade.SUBSTITUTE) {
                    Picker(
                        "Список на следующем этапе",
                        lists.firstOrNull { it.id == targetList }?.name ?: "Выбрать",
                        lists.map { it.id to it.name },
                    ) { targetList = it }
                } else {
                    OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetEl?.let { "Скрыть: ${it.name}" } ?: "Выбрать скрываемый элемент")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    onSave(
                        RuleEntity(
                            newId(), trigger!!.id, type,
                            if (type == Cascade.SUBSTITUTE) targetList else null,
                            if (type == Cascade.EXCLUDE) targetEl!!.id else null,
                        )
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )

    if (picking != 0) {
        ElementPickerDialog(elements, lists, { picking = 0 }) { e ->
            if (picking == 1) trigger = e else targetEl = e
            picking = 0
        }
    }
}

// ---------------------------------------------------------------- Профили

@Composable
fun ProfilesTab(vm: MainVm) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val elements by vm.elements.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleting by remember { mutableStateOf<ProfileEntity?>(null) }
    val stageName = stages.associate { it.id to it.name }

    Box(Modifier.fillMaxSize()) {
        if (profiles.isEmpty()) {
            Text("Профилей нет. Профиль — это пресет: активные этапы, режим по умолчанию, фильтр тегов.", Modifier.align(Alignment.Center).padding(24.dp))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles, key = { it.id }) { p ->
                val active = p.activeStageIds.csv().mapNotNull { stageName[it] }
                ElevatedCard(Modifier.fillMaxWidth().clickable { editing = p }) {
                    Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${if (p.defaultMode == "SINGLE") "Одиночный" else "Каскад"} · этапов: ${active.size}" +
                                    if (p.tagFilter.isNotBlank()) " · теги: ${p.tagFilter}" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Изменить") }
                        IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, "Добавить профиль") }
    }

    if (adding) {
        ProfileDialog(null, stages, elements, { adding = false }) { vm.saveProfile(it); adding = false }
    }
    editing?.let { p ->
        ProfileDialog(p, stages, elements, { editing = null }) { vm.saveProfile(it); editing = null }
    }
    deleting?.let { p ->
        ConfirmDialog("Удалить профиль?", "«${p.name}» будет удалён.", "Удалить", { deleting = null }) {
            vm.deleteProfile(p.id)
            deleting = null
        }
    }
}

@Composable
private fun ProfileDialog(
    initial: ProfileEntity?,
    stages: List<StageEntity>,
    elements: List<ElementEntity>,
    onDismiss: () -> Unit,
    onSave: (ProfileEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var mode by remember { mutableStateOf(initial?.defaultMode ?: "CASCADE") }
    var active by remember { mutableStateOf(initial?.activeStageIds?.csv()?.toSet() ?: stages.map { it.id }.toSet()) }
    var single by remember { mutableStateOf(initial?.defaultSingleStageId ?: "") }
    var tags by remember { mutableStateOf(initial?.tagFilter?.csv()?.toSet() ?: emptySet()) }
    val allTags = remember(elements) { elements.flatMap { it.tagList() }.distinct().sorted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый профиль" else "Профиль") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Название") })

                Text("Режим по умолчанию", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == "CASCADE", { mode = "CASCADE" }, { Text("Каскад") })
                    FilterChip(mode == "SINGLE", { mode = "SINGLE" }, { Text("Одиночный") })
                }

                Text("Активные этапы", style = MaterialTheme.typography.titleSmall)
                if (stages.isEmpty()) Text("Сначала создайте этапы.", style = MaterialTheme.typography.bodySmall)
                stages.forEach { st ->
                    val on = st.id in active
                    Row(
                        Modifier.fillMaxWidth().clickable { active = if (on) active - st.id else active + st.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { active = if (it) active + st.id else active - st.id })
                        Text(st.name)
                    }
                }

                Picker(
                    "Этап для одиночного режима",
                    if (single.isEmpty()) "Первый активный" else stages.firstOrNull { it.id == single }?.name ?: "Первый активный",
                    listOf("" to "Первый активный") + stages.map { it.id to it.name },
                ) { single = it }

                Text("Фильтр по тегам (пусто = без фильтра)", style = MaterialTheme.typography.titleSmall)
                if (allTags.isEmpty()) Text("Тегов пока нет.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allTags.forEach { t ->
                        FilterChip(
                            selected = t in tags,
                            onClick = { tags = if (t in tags) tags - t else tags + t },
                            label = { Text(t) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        ProfileEntity(
                            id = initial?.id ?: newId(),
                            name = name.trim(),
                            activeStageIds = stages.filter { it.id in active }.joinToString(",") { it.id },
                            defaultMode = mode,
                            defaultSingleStageId = single.ifEmpty { null },
                            tagFilter = tags.joinToString(","),
                        )
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
