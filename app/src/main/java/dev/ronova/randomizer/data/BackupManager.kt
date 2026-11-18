package dev.ronova.randomizer.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Формат .scw = zip: data.json (все таблицы) + photos/<файл>.
 * Полный экспорт включает историю и её фотоотчёты, «Только списки» — списки, элементы, этапы, профили, правила.
 */
class BackupManager(
    private val ctx: Context,
    private val repo: Repo,
    private val photos: PhotoStore,
) {
    private val dao get() = repo.dao

    suspend fun export(full: Boolean): File = withContext(Dispatchers.IO) {
        val lists = dao.allLists()
        val elements = dao.allElements()
        val stages = dao.allStages()
        val profiles = dao.allProfiles()
        val rules = dao.allRules()
        val history = if (full) dao.allHistory() else emptyList()

        val root = JSONObject()
            .put("app", "randomizer")
            .put("version", 1)
            .put("scope", if (full) "FULL" else "LISTS")
            .put("exportedAt", System.currentTimeMillis())
            .put("lists", JSONArray(lists.map { it.toJ() }))
            .put("elements", JSONArray(elements.map { it.toJ() }))
            .put("stages", JSONArray(stages.map { it.toJ() }))
            .put("profiles", JSONArray(profiles.map { it.toJ() }))
            .put("rules", JSONArray(rules.map { it.toJ() }))
            .put("history", JSONArray(history.map { it.toJ() }))

        val used = LinkedHashSet<String>()
        elements.forEach { e ->
            e.mainPhoto?.let { used.add(it) }
            used.addAll(e.galleryList())
        }
        history.forEach { h -> h.photo?.let { used.add(it) } }

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val out = File(dir, "randomizer_$stamp.scw")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            used.forEach { name ->
                val f = photos.file(name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        out
    }

    /** replace = полная замена, иначе слияние (записи с одинаковым id перезаписываются данными из файла). */
    suspend fun import(uri: Uri, replace: Boolean): String = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "import_tmp").apply { deleteRecursively(); mkdirs() }
        var json: String? = null
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val n = e.name
                if (!e.isDirectory) {
                    if (n == "data.json") {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (n.startsWith("photos/")) {
                        val fn = n.substringAfterLast('/')
                        if (fn.isNotEmpty() && !fn.contains("..")) {
                            File(tmp, fn).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        val data = json ?: error("Это не файл Randomizer: нет data.json")
        val root = JSONObject(data)
        if (root.optString("app") != "randomizer") error("Это не файл Randomizer")
        val full = root.optString("scope") == "FULL"

        val lists = root.arr("lists") { ListEntity(it.getString("id"), it.getString("name"), it.optInt("position")) }
        val elements = root.arr("elements") {
            ElementEntity(
                it.getString("id"), it.getString("listId"), it.getString("name"),
                it.optStr("mainPhoto"), it.optString("text"), it.optString("gallery"),
                it.optString("tags"), it.optBoolean("active", true),
            )
        }
        val stages = root.arr("stages") {
            StageEntity(it.getString("id"), it.getString("name"), it.optInt("position"), it.optStr("listId"))
        }
        val profiles = root.arr("profiles") {
            ProfileEntity(
                it.getString("id"), it.getString("name"), it.optString("activeStageIds"),
                it.optString("defaultMode", "CASCADE"), it.optStr("defaultSingleStageId"), it.optString("tagFilter"),
            )
        }
        val rules = root.arr("rules") {
            RuleEntity(
                it.getString("id"), it.getString("triggerElementId"), it.getString("type"),
                it.optStr("targetListId"), it.optStr("targetElementId"),
            )
        }
        val history = root.arr("history") {
            HistoryEntity(
                it.getString("id"), it.optString("profileName"), it.optLong("createdAt"),
                it.optString("mode", "CASCADE"), it.optString("items", "[]"), it.optStr("photo"), it.optString("note"),
            )
        }

        if (replace) photos.dir.listFiles()?.forEach { it.delete() }
        tmp.listFiles()?.forEach { it.copyTo(photos.file(it.name), overwrite = true) }
        tmp.deleteRecursively()

        repo.db.withTransaction {
            if (replace) {
                dao.clearLists(); dao.clearElements(); dao.clearStages(); dao.clearProfiles(); dao.clearRules()
                if (full) dao.clearHistory()
            }
            dao.putLists(lists)
            dao.putElements(elements)
            dao.putStages(stages)
            dao.putProfiles(profiles)
            dao.putRules(rules)
            dao.putHistory(history)
            dao.putStages(dao.allStages().mapIndexed { i, s -> s.copy(position = i) })
        }
        repo.gcPhotos()
        "Готово: списков ${lists.size}, элементов ${elements.size}, этапов ${stages.size}, " +
            "профилей ${profiles.size}, правил ${rules.size}, записей истории ${history.size}"
    }

    private fun <T> JSONObject.arr(key: String, f: (JSONObject) -> T): List<T> {
        val a = optJSONArray(key) ?: return emptyList()
        return List(a.length()) { f(a.getJSONObject(it)) }
    }

    private fun JSONObject.optStr(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun ListEntity.toJ() = JSONObject().put("id", id).put("name", name).put("position", position)
    private fun ElementEntity.toJ() = JSONObject().put("id", id).put("listId", listId).put("name", name)
        .put("mainPhoto", mainPhoto).put("text", text).put("gallery", gallery).put("tags", tags).put("active", active)
    private fun StageEntity.toJ() = JSONObject().put("id", id).put("name", name).put("position", position).put("listId", listId)
    private fun ProfileEntity.toJ() = JSONObject().put("id", id).put("name", name).put("activeStageIds", activeStageIds)
        .put("defaultMode", defaultMode).put("defaultSingleStageId", defaultSingleStageId).put("tagFilter", tagFilter)
    private fun RuleEntity.toJ() = JSONObject().put("id", id).put("triggerElementId", triggerElementId).put("type", type)
        .put("targetListId", targetListId).put("targetElementId", targetElementId)
    private fun HistoryEntity.toJ() = JSONObject().put("id", id).put("profileName", profileName).put("createdAt", createdAt)
        .put("mode", mode).put("items", items).put("photo", photo).put("note", note)
}
