package apps.kr.smsmanager.ui;


import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import apps.kr.smsmanager.R;

public class ConversationListActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_list); // 간단한 텍스트만 있어도 됨

        // 들어온 인텐트 확인(예: ACTION_VIEW, type=vnd.android-dir/mms-sms)
        Intent i = getIntent();
        Uri data = i.getData(); // 필요시 content://mms-sms, content://mms-sms/conversations 등 처리
        // TODO: 나중에 실제 목록 RecyclerView로 교체
    }
}
