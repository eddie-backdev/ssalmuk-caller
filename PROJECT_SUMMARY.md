# 쌀먹통화 (Call Automation) 프로젝트 개발 명세서

이 문서는 프로젝트의 유지보수 및 기능 확장을 위해 AI 또는 개발자가 코드 구조를 신속하게 파악하도록 작성되었습니다.

---

## 1. 프로젝트 개요
- **앱 이름**: 쌀먹통화 (구 DutyCaller)
- **최종 패키지**: `com.example.dutycaller`
- **핵심 아키텍처**: `AccessibilityService` (접근성 서비스) 기반 자동화
- **작동 방식**: 시스템 다이얼러(삼성 전화 등) 위에 오버레이되거나 백그라운드에서 동작하며, 화면 요소를 분석해 자동으로 클릭/제스처를 수행.

---

## 2. 주요 파일 및 역할

### 📂 코틀린 코드 (`app/src/main/java/...`)
| 파일명 | 역할 |
| :--- | :--- |
| **MainActivity.kt** | 메인 설정 화면. 권한 요청(접근성, 배터리, 오버레이), 설정값(간격, 목표, 시간) UI 연동 및 저장. |
| **ManageNumbersActivity.kt** | 전화번호 목록 관리 화면. RecyclerView를 통한 추가/삭제 및 텍스트 일괄 편집 기능. |
| **AutoClickService.kt** | **프로젝트의 심장.** 자동 발신 타이머 관리, 화면 텍스트("종료", "받기") 감지, 클릭 및 스와이프 제스처 실행. |
| **CallStateReceiver.kt** | 시스템 전화 상태(`OFFHOOK`, `IDLE`, `RINGING`)를 수신하여 `AutoClickService`에 발신/끊기 명령 전달. |
| **Prefs.kt** | `SharedPreferences` 래퍼 클래스. 모든 설정값 저장 및 JSON 내보내기/가져오기(백업) 담당. |
| **Utils.kt** | 랜덤 간격 계산, 시간 포맷팅, 요일 및 일시중지 시간대 판별 로직 포함. |

### 📂 리소스 (`app/src/main/res/...`)
| 파일명 | 역할 |
| :--- | :--- |
| **activity_main.xml** | 메인 설정 UI. `SwitchCompat`, `AppCompatCheckBox` 등 사용 (다크모드 지원). |
| **accessibility_service_config.xml** | 접근성 서비스 설정. `canRetrieveWindowContent="true"` 필수 설정. |
| **menu_main.xml** | 상단 툴바의 백업/복원 메뉴 정의. |
| **ic_ssal_launcher.xml** | '쌀먹' 컨셉의 벡터 앱 아이콘. |

---

## 3. 핵심 비즈니스 로직

### ① 자동 끊기 (2-Stage Hangup)
1.  **Stage 1 (미수신)**: 발신 후 `no_answer_timeout`(기본 30초) 동안 화면에 통화 타이머(`00:01`)가 안 보이면 강제 종료.
2.  **Stage 2 (통화 중)**: 화면에 타이머가 나타나면 연결로 간주, 사용자가 설정한 `hangup_interval`(분) 후에 종료 예약.
3.  **시간 집계**: `connectedStartTime`을 사용하여 실제 통화 타이머가 작동한 시간만 합산.

### ② 자동 받기 (Auto Answer)
- `TelecomManager.acceptRingingCall()` 시도 후, 실패 시 접근성 클릭(`받기`, `Answer`) 수행.
- 클릭도 실패할 경우를 대비해 **화면 밀기(Swipe Up/Right)** 제스처를 실행하여 '밀어서 받기' 대응.

### ③ 스케줄링 및 일시중지
- `scheduleNextCall` 시점에 `Utils.isTodayAllowed`와 `isCurrentTimeInPauseRange`를 체크.
- 조건 불일치 시 `-1L`(요일 아님), `-2L`(일시중지) 코드를 UI에 보내어 카운트다운 대신 상태 메시지 표시.

---

## 4. 통신 액션 (Intent Actions)
서비스와 리시버 간의 명령 전달을 위해 다음 상수를 사용합니다.
- `ACTION_START_AUTO`: 자동화 루프 시작.
- `ACTION_STOP_AUTO`: 모든 작업 중지 및 핸들러 초기화.
- `ACTION_UPDATE_CONFIG`: 설정 변경 시 즉시 재스케줄링.
- `ACTION_CANCEL_SCHEDULE`: 통화 중 발신 타이머 일시 정지.
- `ACTION_CALL_ENDED`: 통화 종료 후 다음 콜 예약 트리거.
- `ACTION_NEXT_CALL_SCHEDULED`: UI에 남은 시간/상태 전달.

---

## 5. 주의 사항 (Pitfalls)
1.  **XML 위젯**: `AppCompatActivity` 사용 시 `Switch` 대신 `SwitchCompat`, `CheckBox` 대신 `AppCompatCheckBox`를 사용해야 `ClassCastException`을 피할 수 있음.
2.  **Tag 속성**: 레이아웃 XML에서 `android:tag`를 빼먹으면 `view.tag`가 null이 되어 NPE 발생 가능.
3.  **권한**: Android 13+ 알림 권한(`POST_NOTIFICATIONS`)과 Android 14 백그라운드 실행 제약을 반드시 확인해야 함.
4.  **배터리**: '배터리 최적화 제외'가 되어 있지 않으면 화면이 꺼졌을 때 타이머가 멈춤.

---
**최종 수정일**: 2026-01-30
**작성자**: Gemini AI Assistant
