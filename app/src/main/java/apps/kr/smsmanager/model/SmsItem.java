package apps.kr.smsmanager.model;


public class SmsItem {
    public long id;
    public String address;
    public String body;
    public long date;
    public int type; // 1=inbox, 2=sent ...

    public SmsItem(long id, String address, String body, long date, int type) {
        this.id = id;
        this.address = address;
        this.body = body;
        this.date = date;
        this.type = type;
    }
}