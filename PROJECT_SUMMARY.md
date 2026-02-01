# 쌀먹통화 (Call & Data Automation) 프로젝트 개발 명세서

이 문서는 프로젝트의 유지보수 및 기능 확장을 위해 AI 또는 개발자가 코드 구조를 신속하게 파악하도록 작성되었습니다.

---

## 1. 프로젝트 개요
- **앱 이름**: 쌀먹통화 (구 DutyCaller)
- **최종 패키지**: `com.example.dutycaller`
- **핵심 기능**: 통화 자동화(발신/수신/종료) 및 의무 데이터 사용량 자동 소모
- **작동 방식**: 
  - **통화**: `AccessibilityService`와 `AlarmManager`를 기반으로 시스템 다이얼러를 제어하여 백그라운드에서도 안정적으로 동작.
  - **데이터**: `Foreground Service`를 통해 백그라운드에서 웹 리소스를 반복 다운로드하여 데이터 소모.

---

## 2. 주요 파일 및 역할

### 📂 코틀린 코드 (`app/src/main/java/...`)
| 파일명 | 역할 |
| :--- | :--- |
| **MainActivity.kt** | 메인 화면. 권한 관리, 통화/데이터 목표 설정 UI, 실시간 사용 현황(ProgressBar) 업데이트 및 설정 백업/복원 기능 제공. |
| **AutomationService.kt** | **데이터 소모 엔진.** 모바일 데이터 상태 확인 및 일반/터보 모드 기반 백그라운드 다운로드 수행. |
| **AutoClickService.kt** | **통화 자동화 엔진.** `AlarmManager`를 통해 다음 통화 스케줄링. 통화 연결 감지 및 제스처 실행. |
| **CallAlarmReceiver.kt** | `AlarmManager`로부터 신호를 받아 `AutoClickService`를 깨워 통화를 실행시키는 `BroadcastReceiver`. |
| **CallStateReceiver.kt** | 시스템 전화 상태(IDLE, OFFHOOK)를 수신하고, '최소 통화 인정 시간'을 체크하여 통계 업데이트 트리거 전달. |
| **Prefs.kt** | 모든 설정값, 통계(콜수, 시간, MB), 다음 통화 예약 시간(`Timestamp`) 저장 및 JSON 백업/복원. |
| **Utils.kt** | 모바일 데이터 연결 체크, 시간 포맷팅, 랜덤 지연 시간 계산 등 유틸리티. |

### 📂 리소스 (`app/src/main/res/...`)
| 파일명 | 역할 |
| :--- | :--- |
| **activity_main.xml** | 메인 UI. 목표 달성률을 보여주는 ProgressBar 및 각종 설정 항목과 '설정 백업/복원' 버튼 포함. |
| **accessibility_service_config.xml** | 접근성 서비스 권한 및 설정 정의. |
| **ic_settings_backup_restore.xml** | 설정 백업/복원 기능의 명확성을 위한 아이콘. |

---

## 3. 주요 변경 및 개선 사항 (v1.1)

### ① 백그라운드 통화 안정성 강화 ("화면 꺼짐 시 앱 미작동" 문제 해결)
- **핵심 변경**: 기존 `Handler` 기반의 스케줄링을 `AlarmManager.setExactAndAllowWhileIdle`로 교체.
- **결과**: 안드로이드 Doze 모드 등 절전 상태에서도 예약된 시간에 정확히 통화가 시작되도록 하여 백그라운드 동작의 신뢰성을 대폭 향상.
- **권한 추가**: `SCHEDULE_EXACT_ALARM` 권한을 추가하고, 사용자가 직접 권한을 부여할 수 있도록 메인 화면에 안내 버튼을 구현.

### ② 정확한 통화 통계 집계 ("통화 미연결 시에도 통계 증가" 문제 해결)
- **로직 개선**: 사용자가 설정한 '최소 통화 인정 시간' 이상 통화가 지속된 경우에만 통화 횟수와 시간이 집계되도록 `CallStateReceiver` 로직을 수정.
- **UI 추가**: '최소 통화 인정 시간'을 사용자가 직접 설정할 수 있도록 메인 화면에 `EditText`를 추가하여 편의성 증대.

### ③ UI 동기화 문제 해결 ("화면 꺼진 상태에서 통화 후 타이머 미 재시작" 문제 해결)
- **상태 저장**: `AutoClickService`가 다음 통화를 스케줄링할 때, 실행될 정확한 `Timestamp`를 `SharedPreferences`에 저장.
- **UI 복원**: `MainActivity`가 다시 활성화될 때(`onResume`) 저장된 `Timestamp`를 읽어와 남은 시간을 계산하고 카운트다운 타이머를 정확하게 복원.

### ④ UI/UX 개선
- **앱 이름 오류 수정**: `AndroidManifest.xml`의 `android:label` 속성 오타를 수정하여 앱 이름이 "쌀먹통화"로 정상 표시되도록 함.
- **설정 백업/복원 버튼 통합**: 기존의 툴바 메뉴 대신, 메인 화면에 '설정 백업/복원' 버튼을 추가. 클릭 시 `AlertDialog`를 통해 백업 또는 복원 기능을 명확하게 선택하도록 하여 사용자 경험을 개선.

---

## 4. 통신 액션 (Intent Actions)
- `ACTION_START_AUTO` / `ACTION_STOP_AUTO`: 통화 자동화 시작/중지.
- `ACTION_MAKE_CALL`: `AlarmManager`에 의해 트리거되어 실제 통화를 실행.
- `ACTION_CALL_ENDED`: 통화 종료 후 다음 통화 스케줄링을 트리거.
- `ACTION_NEXT_CALL_SCHEDULED`: 다음 통화가 예약되었음을 UI에 알려 카운트다운을 시작.
- `ACTION_START` / `ACTION_STOP`: 데이터 소모 서비스 시작/중지.
- `ACTION_DATA_UPDATE` / `ACTION_STATS_UPDATED`: 데이터 및 통화 통계 갱신 시 UI 알림.

---

## 5. 주의 사항 (Pitfalls)
1.  **필수 권한**: `SCHEDULE_EXACT_ALARM`(Android 12+), `INTERNET`, `FOREGROUND_SERVICE_DATA_SYNC` 등의 권한이 매니페스트에 반드시 포함되어야 함.
2.  **다크모드**: UI 배경색 지정 시 하드코딩된 색상 대신 `?android:attr/selectableItemBackground` 등을 사용하여 테마 호환성 유지.
3.  **네트워크**: Wi-Fi 상태에서는 의무 데이터 소모가 발생하지 않으므로 사용 시 Wi-Fi 해제 확인 필요.

---
**최종 수정일**: 2026-01-31
**작성자**: Gemini AI Assistant