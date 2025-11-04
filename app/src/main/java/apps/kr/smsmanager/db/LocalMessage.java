package apps.kr.smsmanager.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// LocalMessage.java
@Entity(
        tableName = "messages",
        indices = {
                @Index(value = {"sysId", "isMms"}, unique = true) // ← 고유키
        }
)
public class LocalMessage {
    @PrimaryKey(autoGenerate = true) public long localId;

    public long sysId;      // 시스템 메시지 ID(SMS: _id, MMS: _id)
    public boolean isMms;   // true=MMS, false=SMS

    public long threadId;
    public String address;
    public String body;
    public long date;
    public int box;
    public boolean uploaded;
}
