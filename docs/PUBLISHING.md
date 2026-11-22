# Publishing checklist

1. **Name** — check it's free: `npm view expo-location-keepalive` (404 = free). Or publish scoped, e.g. `@your-org/expo-location-keepalive` (update `package.json` → `name`).
2. **Owner details** — fill `author`, `repository`, `homepage`, `bugs` in `package.json`, and the copyright holder in `LICENSE`.
3. **Compile check in a fresh app**
   ```bash
   npx create-expo-app keepalive-demo && cd keepalive-demo
   npx expo install expo-location expo-task-manager @react-native-async-storage/async-storage
   npm install ../expo-location-keepalive        # local path
   npx expo prebuild -p android && cd android && ./gradlew assembleDebug
   ```
   Copy `example/tracking.js` + `example/SetupScreen.js` in, run the tests in `docs/TESTING.md`.
4. **Version** — start at `0.1.0`; bump per semver. Record changes in `CHANGELOG.md`.
5. **Publish**
   ```bash
   npm login
   npm publish --access public
   ```
6. **After each Expo SDK upgrade** re-run the swipe test: the package depends on expo-task-manager / expo-location internal names (`TaskManagerPackage.mTaskService`, `TaskService.mContextRef`, `TaskBroadcastReceiver`).

Optional later: TypeScript sources + `expo-module-scripts` build, an `example/` Expo app in the repo, GitHub Actions running `./gradlew assembleDebug`.
