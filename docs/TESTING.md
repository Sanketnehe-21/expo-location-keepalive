# Testing on a real phone

Emulators don't have OEM battery managers — test on a real Xiaomi / Oppo / Vivo / TECNO / Samsung.

## 1. Connect

```bash
adb devices -l                 # accept "Allow USB debugging" on the phone
adb logcat -c -b all           # clear old logs
adb logcat -v time | grep --line-buffered -E "LocKeepAlive|has died|removeTask|FATAL|LocationTaskConsumer"
```

## 2. Swipe test

1. Open the app, finish the setup gate (battery Unrestricted, auto-start ON).
2. Start tracking (5 s interval). Wait 20 s — your server should receive pings.
3. Swipe the app from recent apps. **Do not reopen it.**
4. Expected log:

```
ActivityManager: Process <your.package> (pid A) has died      ← OEM kill
LocKeepAlive: guard-created                                    ← Android restarted the guard
LocKeepAlive: wake reason=guard-created
LocKeepAlive: guard-start sticky=false fg=true
LocKeepAlive: guard-foreground                                 ← location foreground service ON
LocKeepAlive: taskservice-context-fixed
```

5. Count server pings for 10 minutes. Pass = ~12/min at a 5 s interval, no gap > ~15 s, no `FATAL`.

## 3. Offline test

1. With tracking running, turn on airplane mode for 3 min.
2. Turn it off; don't open the app.
3. The server should receive the 3 minutes of pings (batch endpoint) within about a minute, with their original timestamps.

## 4. Logout test

Logout → `guard-destroyed`, tracking notification gone, no more pings.

## Reading failures

| Log | Meaning |
|---|---|
| `task-removed` but no `guard-created` afterwards | OEM blocked the restart → battery / auto-start not set. |
| `guard-created` but no `guard-foreground` | Battery not unrestricted (`needsForeground` false) or the screen was visible. |
| `guard-foreground-failed: … not a subset of foregroundServiceType` | Manifest merge lost `foregroundServiceType="location"`. |
| Fixes logged but no successful POST | You are using `fetch` in the task — use `postJson`. |
| `SharedPreferences.getAll() on a null object` | `fixTaskService()` not called / module not linked. |
| `ForegroundServiceDidNotStartInTimeException` | Report it — the guard should stop itself instead. |
