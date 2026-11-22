# Changelog

## 0.1.0

First version, extracted from a production field-tracking app and tested on TECNO KN8 (HiOS):

- Guard service: restores expo-location tracking after swipe / OEM kill and becomes the location foreground service in the background.
- Native HTTP (`postJson`, `httpPost`) for background location tasks.
- expo-task-manager TaskService context workaround (`fixTaskService`).
- Write-ahead offline ping queue (`createOfflineQueue`).
- Battery / brand auto-start helpers and setup example.
