# Changelog

This document summarizes the changes made to the DutyCaller application.

## Enhancements and Bug Fixes

### 1. Robust Background Calling (Fix for "App not working when screen off")
To ensure the auto-calling feature works reliably even when the device is in Doze mode or the screen is off, the scheduling mechanism was upgraded.

-   **`CallAlarmReceiver.kt`**: A new `BroadcastReceiver` was created to receive alarms from `AlarmManager` and trigger `AutoClickService` to make calls.
-   **`AutoClickService.kt`**:
    -   The call scheduling logic was migrated from `Handler.postDelayed` to `AlarmManager.setExactAndAllowWhileIdle`. This API allows alarms to fire even when the device is in low-power states, guaranteeing timely call initiation.
    -   A new action `ACTION_MAKE_CALL` was introduced to differentiate alarm-triggered call requests.
    -   Helper functions `getCallAlarmPendingIntent()` and `cancelScheduledCall()` were added to manage the `AlarmManager` effectively.
    -   `onStartCommand` was updated to incorporate the new `AlarmManager`-based scheduling and cancellation logic.
-   **`AndroidManifest.xml`**:
    -   The newly created `CallAlarmReceiver` was registered to receive broadcast intents.
    -   The `android.permission.SCHEDULE_EXACT_ALARM` permission was added, which is required for using `setExactAndAllowWhileIdle` on Android 12 (API 31) and higher.
-   **`activity_main.xml`**: A new button (`btnSetAlarm`) was added to the main activity layout, prompting users to grant the "Exact Alarm" permission if needed.
-   **`MainActivity.kt`**:
    -   Logic was added to declare and initialize `btnSetAlarm`.
    -   The `checkAlarmPermission()` function was implemented to check the status of the `SCHEDULE_EXACT_ALARM` permission and dynamically show/hide `btnSetAlarm`.
    -   A click listener was added to `btnSetAlarm` to direct users to the relevant system settings page to grant the permission.

### 2. Accurate Call Statistics (Fix for "Call count and duration increase even if call is not connected")
To prevent calls that do not connect or are extremely short from being counted as successful, a minimum call duration check was implemented.

-   **`CallStateReceiver.kt`**: The logic within the `TelephonyManager.EXTRA_STATE_IDLE` block was modified. Before incrementing call count and duration statistics, it now checks if the detected call duration (`durationSec`) is greater than or equal to the value retrieved from `Prefs.getMinSuccessDuration()`. Calls shorter than this configurable minimum are now explicitly logged as skipped and do not affect statistics.
-   **`activity_main.xml`**: An `EditText` (`etMinSuccessDuration`) was added to the main layout, allowing users to specify their desired minimum successful call duration in seconds.
-   **`MainActivity.kt`**:
    -   `etMinSuccessDuration` was declared and initialized.
    -   Logic was added to `loadPrefs()` and `savePrefs()` to persist and retrieve the user-defined minimum success duration.
    -   A `TextWatcher` was attached to `etMinSuccessDuration` in `setupListeners()` to automatically save changes.

### 3. Correct App Name Display (Fix for "App name is com.example.dutycaller.DutyCallerApp")
The issue where the app name was incorrectly displayed as the application class name was resolved.

-   **`AndroidManifest.xml`**: A typo in the `<application>` tag was corrected: `android.label="@string/app_name"` was changed to `android:label="@string/app_name"`. The missing colon in the attribute name caused the system to ignore the intended app name resource.

### 4. Consolidated Backup/Restore UI (User Request)
To provide a more intuitive and unified interface for managing application settings backup and restoration, a single button now presents these options.

-   **`ic_settings_backup_restore.xml`**: A new vector drawable icon (`ic_settings_backup_restore`) was created to visually represent the backup and restore functionality.
-   **`activity_main.xml`**: A new button (`btnBackupRestore`) with the `ic_settings_backup_restore` icon and the text "설정 백업/복원" was added to the main layout, serving as the single entry point for these actions.
-   **`MainActivity.kt`**:
    -   `btnBackupRestore` was declared and initialized.
    -   The `showBackupRestoreDialog()` function was implemented to display an `AlertDialog` offering "설정 내보내기 (백업)" and "설정 가져오기 (복원)" options when `btnBackupRestore` is clicked.
    -   The click listener for `btnBackupRestore` was set to call `showBackupRestoreDialog()`.
    -   The `onCreateOptionsMenu` and `onOptionsItemSelected` methods, which previously handled toolbar menu items for export/import, were removed as they are no longer needed.
-   **Deleted Files**: The following unused files were removed: `app/src/main/res/menu/menu_main.xml`, `app/src/main/res/drawable/ic_more_vert.xml`, `app/src/main/res/drawable/ic_save.xml`, and `app/src/main/res/drawable/ic_file_upload.xml`.

### 5. UI Countdown Synchronization (Fix for "UI timer doesn't restart after screen off call")
To ensure the app's UI accurately reflects the state of the next scheduled call, especially after the app has been in the background or killed, a synchronization mechanism was implemented.

-   **`Prefs.kt`**:
    -   A new preference key `KEY_NEXT_CALL_TIMESTAMP` was added.
    -   `setNextCallTimestamp(context, timestamp: Long)` and `getNextCallTimestamp(context): Long` functions were implemented to save and retrieve the exact timestamp of the next scheduled call.
-   **`AutoClickService.kt`**:
    -   In `scheduleNextCall()`, the `triggerAtMillis` timestamp (when the next alarm is set) is now saved to `Prefs` using `setNextCallTimestamp()`.
    -   In `cancelScheduledCall()`, the next call timestamp is cleared (`0L`) from `Prefs`.
    -   In `makeCall()`, the timestamp is also cleared from `Prefs` as the call is now being initiated.
-   **`MainActivity.kt`**:
    -   The `updateCountdownFromPrefs()` function was implemented and called from `onResume()`. This function retrieves the next scheduled call timestamp from `Prefs` and, if it's in the future, calculates the remaining delay to update the UI's countdown timer.
    -   The `switchAutoCall.setOnCheckedChangeListener` was modified to clear the next call timestamp from `Prefs` when the auto-call feature is disabled, preventing a stale countdown.
