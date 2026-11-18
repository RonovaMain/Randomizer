package dev.ronova.randomizer.data

import android.content.SharedPreferences
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repo(val db: AppDb, val photos: PhotoStore) {
    val dao = db.dao()

    val lists = dao.lists()
    val elements = dao.elements()
    val stages = dao.stages()
    val profiles = dao.profiles()
    val rules = dao.rules()
    val history = dao.history()

    suspend fun saveList(l: ListEntity) = dao.putLists(listOf(l))

    suspend fun deleteList(id: String) {
        db.withTransaction {
            dao.elementsOfList(id).forEach { dao.deleteRulesOfElement(it.id); dao.deleteElement(it.id) }
            dao.detachStages(id)
            dao.deleteRulesOfList(id)
            dao.deleteList(id)
        }
        gcPhotos()
    }

    suspend fun saveStage(s: StageEntity) = dao.putStages(listOf(s))
    suspend fun saveStagesOrder(list: List<StageEntity>) =
        dao.putStages(list.mapIndexed { i, s -> s.copy(position = i) })
    suspend fun deleteStage(id: String) = dao.deleteStage(id)

    suspend fun saveElement(e: ElementEntity) {
        dao.putElements(listOf(e))
        gcPhotos()
    }

    suspend fun deleteElement(id: String) {
        db.withTransaction {
            dao.deleteRulesOfElement(id)
            dao.deleteElement(id)
        }
        gcPhotos()
    }

    suspend fun setActive(ids: List<String>, active: Boolean) {
        ids.chunked(500).forEach { dao.setActive(it, active) }
    }

    suspend fun saveRule(r: RuleEntity) = dao.putRules(listOf(r))
    suspend fun deleteRule(id: String) = dao.deleteRule(id)
    suspend fun saveProfile(p: ProfileEntity) = dao.putProfiles(listOf(p))
    suspend fun deleteProfile(id: String) = dao.deleteProfile(id)

    suspend fun saveHistory(h: HistoryEntity) = dao.putHistory(listOf(h))
    suspend fun deleteHistory(id: String) {
        dao.deleteHistory(id)
        gcPhotos()
    }

    suspend fun setHistoryPhoto(id: String, uri: Uri?) {
        val name = if (uri == null) null else photos.importFromUri(uri) ?: return
        dao.setHistoryPhoto(id, name)
        gcPhotos()
    }

    /** Удаляет файлы фото, на которые больше никто не ссылается. */
    suspend fun gcPhotos() = withContext(Dispatchers.IO) {
        val used = HashSet<String>()
        dao.allElements().forEach { e ->
            e.mainPhoto?.let { used.add(it) }
            used.addAll(e.galleryList())
        }
        dao.allHistory().forEach { h -> h.photo?.let { used.add(it) } }
        photos.dir.listFiles()?.forEach { f -> if (f.name !in used) f.delete() }
    }

    /** Демо-данные при первом запуске: показывают подмену списка. */
    suspend fun seedIfNeeded(prefs: SharedPreferences) {
        if (prefs.getBoolean("seeded", false)) return
        if (dao.allLists().isEmpty() && dao.allProfiles().isEmpty()) {
            val kitchens = ListEntity(newId(), "Кухни", 0)
            val dishes = ListEntity(newId(), "Блюда", 1)
            val italian = ListEntity(newId(), "Итальянские блюда", 2)
            val japanese = ListEntity(newId(), "Японские блюда", 3)
            fun el(l: ListEntity, n: String, t: String) = ElementEntity(newId(), l.id, n, text = t)
            val itK = el(kitchens, "Итальянская", "Паста, пицца, ризотто.")
            val jpK = el(kitchens, "Японская", "Рис, рыба, умами.")
            val homeK = el(kitchens, "Домашняя", "Что есть в холодильнике.")
            val elements = listOf(
                itK, jpK, homeK,
                el(dishes, "Омлет", "3 яйца, молоко, соль. Сковорода на среднем огне."),
                el(dishes, "Бутерброды", "Хлеб, сыр, что найдётся."),
                el(italian, "Паста карбонара", "Спагетти, желтки, пекорино, гуанчале, перец."),
                el(italian, "Пицца", "Тесто, томаты, моцарелла, базилик."),
                el(japanese, "Рамен", "Бульон, лапша, яйцо, зелень."),
                el(japanese, "Онигири", "Рис, нори, начинка по вкусу."),
            )
            val s1 = StageEntity(newId(), "Кухня", 0, kitchens.id)
            val s2 = StageEntity(newId(), "Блюдо", 1, dishes.id)
            val rules = listOf(
                RuleEntity(newId(), itK.id, "SUBSTITUTE", targetListId = italian.id),
                RuleEntity(newId(), jpK.id, "SUBSTITUTE", targetListId = japanese.id),
            )
            val profile = ProfileEntity(newId(), "Что приготовить", "${s1.id},${s2.id}", "CASCADE", s2.id)
            db.withTransaction {
                dao.putLists(listOf(kitchens, dishes, italian, japanese))
                dao.putElements(elements)
                dao.putStages(listOf(s1, s2))
                dao.putRules(rules)
                dao.putProfiles(listOf(profile))
            }
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }
}
