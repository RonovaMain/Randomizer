package dev.ronova.randomizer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM lists ORDER BY position, name COLLATE NOCASE") fun lists(): Flow<List<ListEntity>>
    @Query("SELECT * FROM elements ORDER BY name COLLATE NOCASE") fun elements(): Flow<List<ElementEntity>>
    @Query("SELECT * FROM stages ORDER BY position") fun stages(): Flow<List<StageEntity>>
    @Query("SELECT * FROM profiles ORDER BY name COLLATE NOCASE") fun profiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM rules") fun rules(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM history ORDER BY createdAt DESC") fun history(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM lists") suspend fun allLists(): List<ListEntity>
    @Query("SELECT * FROM elements") suspend fun allElements(): List<ElementEntity>
    @Query("SELECT * FROM stages ORDER BY position") suspend fun allStages(): List<StageEntity>
    @Query("SELECT * FROM profiles") suspend fun allProfiles(): List<ProfileEntity>
    @Query("SELECT * FROM rules") suspend fun allRules(): List<RuleEntity>
    @Query("SELECT * FROM history") suspend fun allHistory(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLists(items: List<ListEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putElements(items: List<ElementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStages(items: List<StageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProfiles(items: List<ProfileEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRules(items: List<RuleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHistory(items: List<HistoryEntity>)

    @Query("DELETE FROM lists WHERE id = :id") suspend fun deleteList(id: String)
    @Query("DELETE FROM elements WHERE id = :id") suspend fun deleteElement(id: String)
    @Query("DELETE FROM stages WHERE id = :id") suspend fun deleteStage(id: String)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: String)
    @Query("DELETE FROM rules WHERE id = :id") suspend fun deleteRule(id: String)
    @Query("DELETE FROM history WHERE id = :id") suspend fun deleteHistory(id: String)

    @Query("DELETE FROM rules WHERE triggerElementId = :id OR targetElementId = :id") suspend fun deleteRulesOfElement(id: String)
    @Query("DELETE FROM rules WHERE targetListId = :id") suspend fun deleteRulesOfList(id: String)
    @Query("SELECT * FROM elements WHERE listId = :id") suspend fun elementsOfList(id: String): List<ElementEntity>
    @Query("UPDATE stages SET listId = NULL WHERE listId = :id") suspend fun detachStages(id: String)
    @Query("UPDATE elements SET active = :active WHERE id IN (:ids)") suspend fun setActive(ids: List<String>, active: Boolean)
    @Query("UPDATE history SET photo = :photo WHERE id = :id") suspend fun setHistoryPhoto(id: String, photo: String?)

    @Query("DELETE FROM lists") suspend fun clearLists()
    @Query("DELETE FROM elements") suspend fun clearElements()
    @Query("DELETE FROM stages") suspend fun clearStages()
    @Query("DELETE FROM profiles") suspend fun clearProfiles()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM history") suspend fun clearHistory()
}
