package apps.kr.smsmanager.model;

// apps/kr/smsmanager/model/MsgItem.java

public class MsgItem {
    public final long id;
    public final long threadId;
    public final String address;
    public final String body;
    public final long date;
    public final boolean isMms;
    public final int box;   // ✅ 1=inbox(수신), 2=sent(발신)

    public MsgItem(long id,
                   long threadId,
                   String address,
                   String body,
                   long date,
                   boolean isMms,
                   int box) {
        this.id = id;
        this.threadId = threadId;
        this.address = address;
        this.body = body;
        this.date = date;
        this.isMms = isMms;
        this.box = box;
    }
}
