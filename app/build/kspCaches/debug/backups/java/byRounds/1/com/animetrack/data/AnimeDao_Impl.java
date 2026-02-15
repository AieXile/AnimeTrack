package com.animetrack.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.animetrack.model.Anime;
import com.animetrack.model.AnimeStatus;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AnimeDao_Impl implements AnimeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Anime> __insertionAdapterOfAnime;

  private final AnimeTypeConverters __animeTypeConverters = new AnimeTypeConverters();

  private final EntityDeletionOrUpdateAdapter<Anime> __deletionAdapterOfAnime;

  private final EntityDeletionOrUpdateAdapter<Anime> __updateAdapterOfAnime;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAnimeById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllAnimes;

  public AnimeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAnime = new EntityInsertionAdapter<Anime>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `anime` (`id`,`title`,`totalEpisodes`,`watchedEpisodes`,`status`,`rating`,`notes`,`startDate`,`finishDate`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Anime entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getTotalEpisodes());
        statement.bindLong(4, entity.getWatchedEpisodes());
        final String _tmp = __animeTypeConverters.fromAnimeStatus(entity.getStatus());
        statement.bindString(5, _tmp);
        if (entity.getRating() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getRating());
        }
        statement.bindString(7, entity.getNotes());
        if (entity.getStartDate() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getStartDate());
        }
        if (entity.getFinishDate() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getFinishDate());
        }
      }
    };
    this.__deletionAdapterOfAnime = new EntityDeletionOrUpdateAdapter<Anime>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `anime` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Anime entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfAnime = new EntityDeletionOrUpdateAdapter<Anime>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `anime` SET `id` = ?,`title` = ?,`totalEpisodes` = ?,`watchedEpisodes` = ?,`status` = ?,`rating` = ?,`notes` = ?,`startDate` = ?,`finishDate` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Anime entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindLong(3, entity.getTotalEpisodes());
        statement.bindLong(4, entity.getWatchedEpisodes());
        final String _tmp = __animeTypeConverters.fromAnimeStatus(entity.getStatus());
        statement.bindString(5, _tmp);
        if (entity.getRating() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getRating());
        }
        statement.bindString(7, entity.getNotes());
        if (entity.getStartDate() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getStartDate());
        }
        if (entity.getFinishDate() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getFinishDate());
        }
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteAnimeById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM anime WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllAnimes = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM anime";
        return _query;
      }
    };
  }

  @Override
  public Object insertAnime(final Anime anime, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAnime.insertAndReturnId(anime);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAnimes(final List<Anime> animes,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAnime.insert(animes);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAnime(final Anime anime, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfAnime.handle(anime);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateAnime(final Anime anime, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAnime.handle(anime);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAnimeById(final int id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAnimeById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAnimeById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllAnimes(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllAnimes.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllAnimes.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Anime>> getAllAnimes() {
    final String _sql = "SELECT * FROM anime ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"anime"}, new Callable<List<Anime>>() {
      @Override
      @NonNull
      public List<Anime> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTotalEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalEpisodes");
          final int _cursorIndexOfWatchedEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "watchedEpisodes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfFinishDate = CursorUtil.getColumnIndexOrThrow(_cursor, "finishDate");
          final List<Anime> _result = new ArrayList<Anime>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Anime _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpTotalEpisodes;
            _tmpTotalEpisodes = _cursor.getInt(_cursorIndexOfTotalEpisodes);
            final int _tmpWatchedEpisodes;
            _tmpWatchedEpisodes = _cursor.getInt(_cursorIndexOfWatchedEpisodes);
            final AnimeStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __animeTypeConverters.toAnimeStatus(_tmp);
            final Float _tmpRating;
            if (_cursor.isNull(_cursorIndexOfRating)) {
              _tmpRating = null;
            } else {
              _tmpRating = _cursor.getFloat(_cursorIndexOfRating);
            }
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final Long _tmpStartDate;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmpStartDate = null;
            } else {
              _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            }
            final Long _tmpFinishDate;
            if (_cursor.isNull(_cursorIndexOfFinishDate)) {
              _tmpFinishDate = null;
            } else {
              _tmpFinishDate = _cursor.getLong(_cursorIndexOfFinishDate);
            }
            _item = new Anime(_tmpId,_tmpTitle,_tmpTotalEpisodes,_tmpWatchedEpisodes,_tmpStatus,_tmpRating,_tmpNotes,_tmpStartDate,_tmpFinishDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAnimeById(final int id, final Continuation<? super Anime> $completion) {
    final String _sql = "SELECT * FROM anime WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Anime>() {
      @Override
      @Nullable
      public Anime call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTotalEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalEpisodes");
          final int _cursorIndexOfWatchedEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "watchedEpisodes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfFinishDate = CursorUtil.getColumnIndexOrThrow(_cursor, "finishDate");
          final Anime _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpTotalEpisodes;
            _tmpTotalEpisodes = _cursor.getInt(_cursorIndexOfTotalEpisodes);
            final int _tmpWatchedEpisodes;
            _tmpWatchedEpisodes = _cursor.getInt(_cursorIndexOfWatchedEpisodes);
            final AnimeStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __animeTypeConverters.toAnimeStatus(_tmp);
            final Float _tmpRating;
            if (_cursor.isNull(_cursorIndexOfRating)) {
              _tmpRating = null;
            } else {
              _tmpRating = _cursor.getFloat(_cursorIndexOfRating);
            }
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final Long _tmpStartDate;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmpStartDate = null;
            } else {
              _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            }
            final Long _tmpFinishDate;
            if (_cursor.isNull(_cursorIndexOfFinishDate)) {
              _tmpFinishDate = null;
            } else {
              _tmpFinishDate = _cursor.getLong(_cursorIndexOfFinishDate);
            }
            _result = new Anime(_tmpId,_tmpTitle,_tmpTotalEpisodes,_tmpWatchedEpisodes,_tmpStatus,_tmpRating,_tmpNotes,_tmpStartDate,_tmpFinishDate);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Anime>> getAnimesByStatus(final AnimeStatus status) {
    final String _sql = "SELECT * FROM anime WHERE status = ? ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    final String _tmp = __animeTypeConverters.fromAnimeStatus(status);
    _statement.bindString(_argIndex, _tmp);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"anime"}, new Callable<List<Anime>>() {
      @Override
      @NonNull
      public List<Anime> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTotalEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalEpisodes");
          final int _cursorIndexOfWatchedEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "watchedEpisodes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfFinishDate = CursorUtil.getColumnIndexOrThrow(_cursor, "finishDate");
          final List<Anime> _result = new ArrayList<Anime>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Anime _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpTotalEpisodes;
            _tmpTotalEpisodes = _cursor.getInt(_cursorIndexOfTotalEpisodes);
            final int _tmpWatchedEpisodes;
            _tmpWatchedEpisodes = _cursor.getInt(_cursorIndexOfWatchedEpisodes);
            final AnimeStatus _tmpStatus;
            final String _tmp_1;
            _tmp_1 = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __animeTypeConverters.toAnimeStatus(_tmp_1);
            final Float _tmpRating;
            if (_cursor.isNull(_cursorIndexOfRating)) {
              _tmpRating = null;
            } else {
              _tmpRating = _cursor.getFloat(_cursorIndexOfRating);
            }
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final Long _tmpStartDate;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmpStartDate = null;
            } else {
              _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            }
            final Long _tmpFinishDate;
            if (_cursor.isNull(_cursorIndexOfFinishDate)) {
              _tmpFinishDate = null;
            } else {
              _tmpFinishDate = _cursor.getLong(_cursorIndexOfFinishDate);
            }
            _item = new Anime(_tmpId,_tmpTitle,_tmpTotalEpisodes,_tmpWatchedEpisodes,_tmpStatus,_tmpRating,_tmpNotes,_tmpStartDate,_tmpFinishDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<Anime>> getHighRatedAnimes(final float minRating) {
    final String _sql = "SELECT * FROM anime WHERE rating >= ? ORDER BY rating DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindDouble(_argIndex, minRating);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"anime"}, new Callable<List<Anime>>() {
      @Override
      @NonNull
      public List<Anime> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfTotalEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalEpisodes");
          final int _cursorIndexOfWatchedEpisodes = CursorUtil.getColumnIndexOrThrow(_cursor, "watchedEpisodes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfFinishDate = CursorUtil.getColumnIndexOrThrow(_cursor, "finishDate");
          final List<Anime> _result = new ArrayList<Anime>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Anime _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpTotalEpisodes;
            _tmpTotalEpisodes = _cursor.getInt(_cursorIndexOfTotalEpisodes);
            final int _tmpWatchedEpisodes;
            _tmpWatchedEpisodes = _cursor.getInt(_cursorIndexOfWatchedEpisodes);
            final AnimeStatus _tmpStatus;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfStatus);
            _tmpStatus = __animeTypeConverters.toAnimeStatus(_tmp);
            final Float _tmpRating;
            if (_cursor.isNull(_cursorIndexOfRating)) {
              _tmpRating = null;
            } else {
              _tmpRating = _cursor.getFloat(_cursorIndexOfRating);
            }
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final Long _tmpStartDate;
            if (_cursor.isNull(_cursorIndexOfStartDate)) {
              _tmpStartDate = null;
            } else {
              _tmpStartDate = _cursor.getLong(_cursorIndexOfStartDate);
            }
            final Long _tmpFinishDate;
            if (_cursor.isNull(_cursorIndexOfFinishDate)) {
              _tmpFinishDate = null;
            } else {
              _tmpFinishDate = _cursor.getLong(_cursorIndexOfFinishDate);
            }
            _item = new Anime(_tmpId,_tmpTitle,_tmpTotalEpisodes,_tmpWatchedEpisodes,_tmpStatus,_tmpRating,_tmpNotes,_tmpStartDate,_tmpFinishDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
