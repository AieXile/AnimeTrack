package com.aiexile.animetrack.data

import android.util.Log
import com.aiexile.animetrack.data.network.BangumiSearchFilter
import com.aiexile.animetrack.data.network.BangumiSearchRequest
import com.aiexile.animetrack.data.network.BangumiSubject
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    
    fun getAllAnimes(): Flow<List<Anime>>
    
    fun getAnimesByStatus(status: AnimeStatus): Flow<List<Anime>>
    
    suspend fun getAnimeById(id: Int): Anime?

    fun observeAnimeById(id: Int): Flow<Anime?>
    
    suspend fun insertAnime(anime: Anime): Long
    
    suspend fun updateAnime(anime: Anime)
    
    suspend fun deleteAnime(anime: Anime)
    
    suspend fun getAnimeByTitle(title: String): Anime?

    suspend fun getAnimeByBangumiId(bangumiId: Int): Anime?
    
    suspend fun insertAnimes(animes: List<Anime>)
    
    suspend fun getAnimesWithoutCover(): List<Anime>
    
    suspend fun searchBangumi(query: String): List<BangumiSubject>

    fun getAiringAnimes(): Flow<List<Anime>>

    suspend fun getAiringAnimesWithBangumiId(): List<Anime>

    suspend fun clearNewUpdate(id: Int)
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
    
    override suspend fun getAnimeById(id: Int): Anime? {
        return animeDao.getAnimeById(id)
    }

    override fun observeAnimeById(id: Int): Flow<Anime?> {
        return animeDao.observeAnimeById(id)
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
    
    override suspend fun getAnimeByTitle(title: String): Anime? {
        return animeDao.getAnimeByTitle(title)
    }

    override suspend fun getAnimeByBangumiId(bangumiId: Int): Anime? {
        return animeDao.getAnimeByBangumiId(bangumiId)
    }
    
    override suspend fun insertAnimes(animes: List<Anime>) {
        animeDao.insertAnimes(animes)
    }
    
    override suspend fun getAnimesWithoutCover(): List<Anime> {
        return animeDao.getAnimesWithoutCover()
    }
    
    override suspend fun searchBangumi(query: String): List<BangumiSubject> {
        val response = RetrofitClient.bangumiApi.searchSubjects(
            BangumiSearchRequest(
                keyword = query,
                sort = "match",
                filter = BangumiSearchFilter(type = listOf(2))
            )
        )
        return response.data
    }

    override fun getAiringAnimes(): Flow<List<Anime>> {
        return animeDao.getAiringAnimes()
    }

    override suspend fun getAiringAnimesWithBangumiId(): List<Anime> {
        return animeDao.getAiringAnimesWithBangumiId()
    }

    override suspend fun clearNewUpdate(id: Int) {
        animeDao.clearNewUpdate(id)
    }
}
