package dev.ronova.randomizer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import java.util.UUID

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int = 0,
)

@Entity(tableName = "elements", indices = [Index("listId")])
data class ElementEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val mainPhoto: String? = null,
    val text: String = "",
    /** имена файлов галереи, разделитель '|' */
    val gallery: String = "",
    /** теги через запятую */
    val tags: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "stages")
data class StageEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    /** базовый список этапа; null = все списки */
    val listId: String? = null,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** id активных этапов через запятую */
    val activeStageIds: String = "",
    /** CASCADE | SINGLE */
    val defaultMode: String = "CASCADE",
    val defaultSingleStageId: String? = null,
    /** фильтр по тегам через запятую; пусто = без фильтра */
    val tagFilter: String = "",
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val triggerElementId: String,
    /** SUBSTITUTE (подмена списка) | EXCLUDE (исключение элемента) */
    val type: String,
    val targetListId: String? = null,
    val targetElementId: String? = null,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val profileName: String,
    val createdAt: Long,
    val mode: String,
    /** JSON-массив [{stage, elementId, name}] */
    val items: String,
    val photo: String? = null,
    val note: String = "",
)

data class HistoryItem(val stage: String, val elementId: String, val name: String)

fun newId(): String = UUID.randomUUID().toString()

fun String.csv(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun ElementEntity.tagList(): List<String> = tags.csv()

fun ElementEntity.galleryList(): List<String> = gallery.split("|").filter { it.isNotBlank() }

fun parseHistoryItems(json: String): List<HistoryItem> = try {
    val a = JSONArray(json)
    List(a.length()) {
        val o = a.getJSONObject(it)
        HistoryItem(o.optString("stage"), o.optString("elementId"), o.optString("name"))
    }
} catch (e: Exception) {
    emptyList()
}
