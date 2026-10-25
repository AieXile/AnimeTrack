package com.aiexile.animetrack.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 收藏页 ViewModel：只读订阅已收藏番剧列表。
 * 收藏/取消收藏统一在主界面长按卡片中操作，列表随数据库变更实时刷新。
 */
class FavoritesViewModel(
    repository: AnimeRepository
) : ViewModel() {

    val favorites: StateFlow<List<Anime>> = repository.getFavoriteAnimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(AppContainer.getAnimeRepository()) as T
        }
    }
}
