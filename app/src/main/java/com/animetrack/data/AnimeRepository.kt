package com.animetrack.data

import android.util.Log
import com.animetrack.model.Anime
import com.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    
    fun getAllAnimes(): Flow<List<Anime>>
    
    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>
    
    fun getHighRatedAnimes(minRating: Float = 4.5f): Flow<List<Anime>>
    
    suspend fun getAnimeById(id: Int): Anime?
    
    suspend fun insertAnime(anime: Anime): Long
    
    suspend fun updateAnime(anime: Anime)
    
    suspend fun deleteAnime(anime: Anime)
}

class AnimeRepositoryImpl(
    private val animeDao: AnimeDao
) : AnimeRepository {
    
    companion object {
        private const val TAG = "AnimeTrack"
    }
    
    override fun getAllAnimes(): Flow<List<Anime>> {
        Log.d(TAG, "getAllAnimes: Getting all animes from DAO")
        return animeDao.getAllAnimes()
    }
    
    override fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>> {
        return animeDao.getAnimesByStatus(status)
    }
    
    override fun getHighRatedAnimes(minRating: Float): Flow<List<Anime>> {
        return animeDao.getHighRatedAnimes(minRating)
    }
    
    override suspend fun getAnimeById(id: Int): Anime? {
        return animeDao.getAnimeById(id)
    }
    
    override suspend fun insertAnime(anime: Anime): Long {
        Log.d(TAG, "insertAnime: Inserting anime - $anime")
        val id = animeDao.insertAnime(anime)
        Log.d(TAG, "insertAnime: Inserted with id=$id")
        return id
    }
    
    override suspend fun updateAnime(anime: Anime) {
        animeDao.updateAnime(anime)
    }
    
    override suspend fun deleteAnime(anime: Anime) {
        animeDao.deleteAnime(anime)
    }
}
