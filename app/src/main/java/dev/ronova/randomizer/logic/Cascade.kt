package dev.ronova.randomizer.logic

import dev.ronova.randomizer.data.ElementEntity
import dev.ronova.randomizer.data.RuleEntity
import dev.ronova.randomizer.data.StageEntity
import dev.ronova.randomizer.data.tagList

data class Session(
    val profileName: String,
    val mode: String,
    val stages: List<StageEntity>,
    val tagFilter: Set<String>,
    val results: List<ElementEntity?>,
    val locks: Set<Int>,
    val index: Int,
)

object Cascade {
    const val SUBSTITUTE = "SUBSTITUTE"
    const val EXCLUDE = "EXCLUDE"

    /**
     * Пул этапа. Правила срабатывают от элемента, выпавшего на предыдущем этапе:
     * SUBSTITUTE — берём другой список, EXCLUDE — скрываем указанный элемент.
     */
    fun pool(
        stage: StageEntity,
        prev: ElementEntity?,
        elements: List<ElementEntity>,
        rules: List<RuleEntity>,
        tagFilter: Set<String>,
    ): List<ElementEntity> {
        var listId = stage.listId
        val excluded = HashSet<String>()
        if (prev != null) {
            for (r in rules) {
                if (r.triggerElementId != prev.id) continue
                when (r.type) {
                    SUBSTITUTE -> if (r.targetListId != null) listId = r.targetListId
                    EXCLUDE -> r.targetElementId?.let { excluded.add(it) }
                }
            }
        }
        return elements.filter { e ->
            e.active &&
                (listId == null || e.listId == listId) &&
                e.id !in excluded &&
                (tagFilter.isEmpty() || e.tagList().any { it.lowercase() in tagFilter })
        }
    }

    fun pick(pool: List<ElementEntity>): ElementEntity? = if (pool.isEmpty()) null else pool.random()
}
