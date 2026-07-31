# Future Conflicts

A turn-based tactics game in the spirit of **Advance Wars** — grid battles between
the **Blue Army** and the **Red Army**, built with **Kotlin Multiplatform +
Compose Multiplatform** so it runs on **Android now and iOS later**.

The game rules are pure Kotlin (`composeApp/src/commonMain/.../game/`) with no
Compose or platform imports, so they're portable and unit-testable. Rendering and
touch input live in a single Compose `Canvas` screen (`ui/GameScreen.kt`).

## Play (vertical slice)

- Tap one of your **blue** units to see its movement range (blue tiles).
- Tap a highlighted tile to move there. If enemies are in range, they light up
  **red** — tap one to attack, or tap again to wait.
- **Artillery** hits at range 2–3 but can't move and fire in the same turn.
- Press **End Turn**; the Red Army takes its turn automatically.
- Wipe out the enemy army to win. **Restart** resets the map.

## Build & run

Gradle needs a JDK; use the Android Studio bundled JBR:

```bash
export JAVA_HOME="/home/kalieki/Downloads/android-studio-quail3-linux/android-studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :androidApp:assembleDebug     # build the Android debug APK
./gradlew :androidApp:installDebug      # build + install on a device/emulator
```

iOS is wired up (`iosApp/`, shared framework `ComposeApp`) but must be built on a
Mac with Xcode.

See [DESIGN.md](DESIGN.md) for the game design and [ROADMAP.md](ROADMAP.md) for
what's done and what's next.
