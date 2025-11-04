package apps.kr.smsmanager.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import androidx.room.Upsert;

@Dao
public interface MessageDao {


    // 업로드 대기
    @Query("SELECT * FROM messages WHERE uploaded = 0 ORDER BY date ASC")
    List<LocalMessage> getNotUploaded();

    @Query("UPDATE messages SET uploaded = 1 WHERE localId IN (:ids)")
    void markUploaded(List<Long> ids);

    @Query("SELECT * FROM messages ORDER BY date DESC LIMIT 1")
    LocalMessage getLatestOne();

    // ✅ MMS 텍스트 갱신 등 부분 업데이트 (키 기반)
    @Query("UPDATE messages SET address=:address, body=:body, date=:date WHERE sysId=:sysId AND isMms=1")
    void updateMms(long sysId, String address, String body, long date);

    // ✅ 진짜 핵심: 유니크 인덱스 기반 Upsert
    @Upsert
    void upsert(LocalMessage msg);

    @Upsert
    void upsertAll(List<LocalMessage> list);

    @Query("SELECT * FROM messages ORDER BY date DESC, localId DESC LIMIT :limit")
    LiveData<List<LocalMessage>> observeLatest(int limit);

    @Query("SELECT * FROM messages ORDER BY date DESC, localId DESC LIMIT :limit")
    List<LocalMessage> getLatest(int limit);


    @Query("SELECT * FROM messages WHERE sysId=:sysId AND isMms=:isMms LIMIT 1")
    LocalMessage getByKey(long sysId, boolean isMms);

    @Query("DELETE FROM messages WHERE sysId=:sysId AND isMms=:isMms")
    int deleteByKey(long sysId, boolean isMms);


}

