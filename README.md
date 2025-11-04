# SMS Manager (Android)

안드로이드 단말의 **SMS/MMS를 로컬 DB에 저장하고, 주기적으로 서버로 업로드**하는 전용 문자 관리 앱입니다.  
기본 문자앱(ROLE_SMS)로 동작하며, 간단한 **문자 조회/발신 UI + 백그라운드 동기화 서비스**를 제공합니다.

---

## 주요 기능

### 1. 문자 목록 & 상세 보기
- 시스템 SMS/MMS Provider에서 데이터를 읽어와 **Room DB(LocalMessage)** 에 저장
- 메인 화면에서 최신 메시지(수신/발신) 목록 조회
  - SMS/MMS 구분 표시
  - 수신/발신 방향 표시 (inbox / sent)
- 항목 클릭 시 **상세 화면(MessageDetailActivity)** 으로 이동
  - 발신 번호, 수신 내용, 날짜/시간 등 확인
  - 해당 상대에게 바로 답장 가능

### 2. 새 메시지 발송
- 메인 상단의 `+` 버튼 → **새 메시지 화면(NewMessageActivity)**:
  - 받는 사람 번호 직접 입력
  - 내용 입력 후 전송
  - 길이 제한(예: 최대 3분할까지 허용) 검증
- SMS 발송 시:
  - `SmsManager.sendMultipartTextMessage(...)` 사용
  - **시스템 Provider(Sent box)** 에 직접 insert
  - 동시에 Room DB(LocalMessage, box=2) 에 저장 → 메인 리스트에 즉시 반영

### 3. 수신 SMS 처리
- `SmsDeliverReceiver` 가 `SMS_DELIVER` 브로드캐스트 수신
- 기본 문자앱(ROLE_SMS)인 경우:
  - `Telephony.Sms.Inbox`(또는 `Sms.CONTENT_URI`) 에 직접 insert
  - Room DB(LocalMessage, box=1) 에 저장
  - 내부 브로드캐스트(`apps.kr.smsmanager.SMS_RECEIVED_INTERNAL`) 전송 → UI와 연동 가능
- 기본앱이 아닌 경우에는 시스템 insert 를 스킵하고 안전하게 로그만 남김

### 4. MMS 감지
- `MmsObserver`(ContentObserver) 를 등록하여 **MMS Inbox 변경 감지**
- MMS 수신 시:
  - `content://mms/inbox` 조회
  - 별도 유틸(MmsUtils)로 주소/본문 추출
  - Room DB에 `isMms=true` 로 저장

### 5. 서버 동기화 (SyncService)
- 포그라운드 서비스 `SyncService` 가 **백그라운드에서 주기적으로 동작**
  - Notification 채널: `sms_sync_channel`
  - 알림: “문자 동기화 실행 중”
- 10초 간격으로 `SmsSyncManager.syncNow()` 호출 (실제 실행은 최소 간격 가드 포함)
- 동기화 방식:
  1. SharedPreferences(`sms_prefs`)에서 서버 설정 로드
     - `server_url` (예: `https://192.168.0.10/sms/upload`)
     - `server_on` (ON/OFF 스위치 값)
     - `last_sync_ts` (마지막 업로드한 메시지 timestamp)
  2. Room DB에서 **최신 30건(LocalMessage)** 조회
  3. `last_sync_ts` 이후의 메시지만 필터링
  4. JSON 배열로 서버에 POST
