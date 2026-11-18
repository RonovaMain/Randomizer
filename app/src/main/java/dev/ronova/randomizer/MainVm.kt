package dev.ronova.randomizer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ronova.randomizer.data.*
import dev.ronova.randomizer.logic.Cascade
import dev.ronova.randomizer.logic.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainVm(app: Application) : AndroidViewModel(app) {
    private val a = app as RandomizerApp
    val repo = a.repo
    val feedback = a.feedback
    val photos = a.photos
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val lists = repo.lists.state(emptyList())
    val elements = repo.elements.state(emptyList())
    val stages = repo.stages.state(emptyList())
    val profiles = repo.profiles.state(emptyList())
    val rules = repo.rules.state(emptyList())
    val history = repo.history.state(emptyList())

    // ---------- выбор на главном экране ----------
    var selectedProfileId by mutableStateOf(prefs.getString("profile", null))
    var mode by mutableStateOf("CASCADE")
    var singleStageId by mutableStateOf<String?>(null)
    private var inited = false

    fun selectProfile(p: ProfileEntity) {
        selectedProfileId = p.id
        mode = p.defaultMode
        singleStageId = p.defaultSingleStageId
        inited = true
        prefs.edit().putString("profile", p.id).apply()
    }

    fun ensureProfile(list: List<ProfileEntity>) {
        val cur = list.firstOrNull { it.id == selectedProfileId }
        if (cur == null) {
            list.firstOrNull()?.let { selectProfile(it) }
        } else if (!inited) {
            mode = cur.defaultMode
            singleStageId = cur.defaultSingleStageId
            inited = true
        }
    }

    // ---------- сессия вращения ----------
    var session by mutableStateOf<Session?>(null)
        private set
    var savedToHistory by mutableStateOf(false)
        private set

    /** Возвращает текст ошибки или null, если сессия создана. */
    fun startSession(): String? {
        val p = profiles.value.firstOrNull { it.id == selectedProfileId }
            ?: return "Нет профиля — создайте его в Редакторе"
        val all = stages.value
        val activeIds = p.activeStageIds.csv().toSet()
        val chosen = if (mode == "SINGLE") {
            listOfNotNull(
                all.firstOrNull { it.id == singleStageId }
                    ?: all.firstOrNull { it.id in activeIds }
                    ?: all.firstOrNull()
            )
        } else {
            all.filter { it.id in activeIds }
        }
        if (chosen.isEmpty()) return "В профиле нет активных этапов"
        session = Session(
            profileName = p.name,
            mode = mode,
            stages = chosen,
            tagFilter = p.tagFilter.csv().map { it.lowercase() }.toSet(),
            results = List(chosen.size) { null },
            locks = emptySet(),
            index = 0,
        )
        savedToHistory = false
        return null
    }

    fun poolFor(s: Session, i: Int): List<ElementEntity> =
        Cascade.pool(s.stages[i], if (i > 0) s.results[i - 1] else null, elements.value, rules.value, s.tagFilter)

    fun setResult(i: Int, e: ElementEntity?) {
        val s = session ?: return
        session = s.copy(results = s.results.toMutableList().also { it[i] = e })
        savedToHistory = false
    }

    fun advance() {
        val s = session ?: return
        session = s.copy(index = (s.index + 1).coerceAtMost(s.stages.lastIndex))
    }

    fun toggleLock(i: Int) {
        val s = session ?: return
        session = s.copy(locks = if (i in s.locks) s.locks - i else s.locks + i)
    }

    /** Перекручивает все этапы, кроме зафиксированных замочком. */
    fun rerollUnlocked() {
        val s = session ?: return
        val res = s.results.toMutableList()
        for (i in s.stages.indices) {
            if (i in s.locks && res[i] != null) continue
            val prev = if (i > 0) res[i - 1] else null
            res[i] = Cascade.pick(Cascade.pool(s.stages[i], prev, elements.value, rules.value, s.tagFilter))
        }
        session = s.copy(results = res)
        savedToHistory = false
    }

    fun saveHistory() {
        val s = session ?: return
        val arr = JSONArray()
        s.stages.forEachIndexed { i, st ->
            val e = s.results[i]
            arr.put(JSONObject().put("stage", st.name).put("elementId", e?.id ?: "").put("name", e?.name ?: "—"))
        }
        viewModelScope.launch {
            repo.saveHistory(HistoryEntity(newId(), s.profileName, System.currentTimeMillis(), s.mode, arr.toString()))
            savedToHistory = true
        }
    }

    // ---------- редактор ----------
    fun saveList(l: ListEntity) = viewModelScope.launch { repo.saveList(l) }
    fun deleteList(id: String) = viewModelScope.launch { repo.deleteList(id) }
    fun saveStage(s: StageEntity) = viewModelScope.launch { repo.saveStage(s) }
    fun deleteStage(id: String) = viewModelScope.launch { repo.deleteStage(id) }
    fun reorderStages(list: List<StageEntity>) = viewModelScope.launch { repo.saveStagesOrder(list) }
    fun saveElement(e: ElementEntity) = viewModelScope.launch { repo.saveElement(e) }
    fun deleteElement(id: String) = viewModelScope.launch { repo.deleteElement(id) }
    fun setActive(ids: List<String>, active: Boolean) = viewModelScope.launch { repo.setActive(ids, active) }
    fun saveRule(r: RuleEntity) = viewModelScope.launch { repo.saveRule(r) }
    fun deleteRule(id: String) = viewModelScope.launch { repo.deleteRule(id) }
    fun saveProfile(p: ProfileEntity) = viewModelScope.launch { repo.saveProfile(p) }
    fun deleteProfile(id: String) = viewModelScope.launch { repo.deleteProfile(id) }

    // ---------- история ----------
    fun deleteHistory(id: String) = viewModelScope.launch { repo.deleteHistory(id) }
    fun setHistoryPhoto(id: String, uri: Uri?) = viewModelScope.launch { repo.setHistoryPhoto(id, uri) }

    // ---------- перенос ----------
    suspend fun export(full: Boolean): File = BackupManager(getApplication(), repo, photos).export(full)
    suspend fun import(uri: Uri, replace: Boolean): String =
        withContext(Dispatchers.IO) { BackupManager(getApplication(), repo, photos).import(uri, replace) }
}
