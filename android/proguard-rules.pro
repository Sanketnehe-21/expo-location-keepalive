# TaskServiceContextFix reaches these expo-task-manager fields by name
-keepclassmembers class expo.modules.taskManager.TaskManagerPackage { static ** mTaskService; }
-keepclassmembers class expo.modules.taskManager.TaskService { ** mContextRef; }
