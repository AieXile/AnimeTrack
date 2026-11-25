package com.aiexile.animetrack.data.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_folder")
data class FavoriteFolder(
    @PrimaryKey val id: String,
    val name: String,
    val position: Long,
    val cover: String? = null
)

data class FavoriteFolderSummary(
    val id: String,
    val name: String,
    val hasCover: Boolean,
    val animeCount: Int
)

data class FavoriteFolderPreview(
    val folderId: String,
    val coverUrl: String?
)
