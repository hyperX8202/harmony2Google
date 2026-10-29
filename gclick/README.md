# gclick – the Gclick app

Android app (`com.ab.gclick`, minSdk 26) that runs in the Android container of HarmonyOS. See the
[top-level README](../README.md) for what it does and how to install it.

## Layout

| Path | Content |
|------|---------|
| `src/com/ab/oneclick/` | Java sources. The Java package is still `com.ab.oneclick` (the project's former name); the application id is `com.ab.gclick`. |
| `proj/` | apktool project: `AndroidManifest.xml`, resources, `apktool.yml`. |
| `release/Gclick_1.0.apk` | The release build (modified Aurora embedded as `assets/astore.apk`). |
| `build.sh` | Builds the APK from source without Android SDK or Gradle. |

Main classes:

- `MainActivity`: the wizard (steps, state, refresh logic).
- `Tr`: all UI strings in 11 languages (`en, de, fr, es, it, pt, nl, pl, tr, ru, zh`).
- `Perm`: permission checks and the settings pages that are opened for them.
- `Seq`, `SeqService`, `AlarmReceiver`: the foreground service that installs the Google apps one
  after another through EasyAbroad and brings the wizard back to the front. The container freezes
  background apps after a few seconds, so the service keeps an alarm clock and a 1×1 overlay alive.
- `TestActivity`: hidden diagnostics page (long-press the "Gclick" title). It is not needed for
  normal use. Its V1 button needs an optional `gclick/assets/calc.apk` (any Google Calculator APK),
  which is not part of this repo.

## Build

```sh
aurora-mod/build.sh      # modified Aurora first, see ../aurora-mod/README.md
gclick/build.sh          # -> gclick/out/Gclick_1.0.apk
```

`build.sh` compiles with `javac -source 8` against a public `android.jar` stub, dexes with R8's D8,
packs with apktool and signs with a key in `~/.gclick-keys/` (created on first use, or your own via
`KEYSTORE`, `KEYSTORE_ALIAS`, `KEYSTORE_PASS`). Keep the key: updates must be signed with the same
one.

The release APK in `release/` was built with this script, without the optional `calc.apk`, and is
signed with the author's release key.
