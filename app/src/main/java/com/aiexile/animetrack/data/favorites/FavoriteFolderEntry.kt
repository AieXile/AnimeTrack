package com.aiexile.animetrack.data.favorites

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.aiexile.animetrack.model.Anime

@Entity(
    tableName = "favorite_folder_entry",
    primaryKeys = ["folderId", "animeId"],
    foreignKeys = [
        ForeignKey(
            entity = FavoriteFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Anime::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("animeId")]
)
data class FavoriteFolderEntry(
    val folderId: String,
    val animeId: Int,
    val position: Long
)
