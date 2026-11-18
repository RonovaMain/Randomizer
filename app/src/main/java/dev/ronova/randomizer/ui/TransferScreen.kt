@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.ronova.randomizer.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import dev.ronova.randomizer.MainVm
import kotlinx.coroutines.launch

@Composable
fun TransferScreen(vm: MainVm, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var full by remember { mutableStateOf(true) }
    var replace by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    fun doImport(uri: Uri) {
        scope.launch {
            busy = true
            val msg = try {
                vm.import(uri, replace)
            } catch (e: Exception) {
                "Ошибка импорта: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
            snack.showSnackbar(msg)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (replace) pending = uri else doImport(uri)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("Перенос данных") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Экспорт", style = MaterialTheme.typography.titleMedium)
                    RadioRow("Полный (всё + история и фотоотчёты)", full) { full = true }
                    RadioRow("Только списки (без истории)", !full) { full = false }
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val f = vm.export(full)
                                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ctx.startActivity(Intent.createChooser(send, "Отправить резервную копию"))
                                } catch (e: Exception) {
                                    snack.showSnackbar("Ошибка экспорта: ${e.message ?: e.javaClass.simpleName}")
                                }
                                busy = false
                            }
                        },
                    ) {
                        Icon(Icons.Default.FileUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Экспортировать и отправить")
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Импорт", style = MaterialTheme.typography.titleMedium)
                    RadioRow("Слияние (добавить к текущим данным)", !replace) { replace = false }
                    RadioRow("Полная замена", replace) { replace = true }
                    Text(
                        "Файлы .scw и .zip. Если в файле нет истории («Только списки»), история на устройстве сохраняется.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { picker.launch("*/*") },
                    ) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выбрать файл")
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    pending?.let { uri ->
        ConfirmDialog(
            "Заменить все данные?",
            "Текущие списки, элементы, этапы, профили и правила будут удалены и заменены содержимым файла.",
            "Заменить",
            { pending = null },
        ) {
            pending = null
            doImport(uri)
        }
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
