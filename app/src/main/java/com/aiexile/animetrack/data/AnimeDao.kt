package com.aiexile.animetrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    
    @Query("SELECT * FROM anime ORDER BY id DESC")
    fun getAllAnimes(): Flow<List<Anime>>
    
    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun getAnimeById(id: Int): Anime?

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observeAnimeById(id: Int): Flow<Anime?>
    
    @Query("SELECT * FROM anime WHERE status = :status ORDER BY id DESC")
    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnime(anime: Anime): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimes(animes: List<Anime>)
    
    @Update
    suspend fun updateAnime(anime: Anime)
    
    @Delete
    suspend fun deleteAnime(anime: Anime)
    
    @Query("SELECT * FROM anime WHERE title = :title LIMIT 1")
    suspend fun getAnimeByTitle(title: String): Anime?
    
    @Query("SELECT * FROM anime WHERE coverUrl IS NULL OR coverUrl = ''")
    suspend fun getAnimesWithoutCover(): List<Anime>

    @Query("SELECT * FROM anime WHERE isFinished = 0 AND status IN ('WATCHING', 'PLANNED') ORDER BY airWeekday ASC, title ASC")
    fun getAiringAnimes(): Flow<List<Anime>>
}
