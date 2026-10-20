# AGENTS.md – Aurora Store on HarmonyOS, agent runbook

You are an AI agent helping a user put **Aurora Store** (open-source Google Play client, GPL-3.0) on a
**HarmonyOS NEXT / 5+ device** (verified: Huawei Mate XT 2, HarmonyOS 7.0, API 26). The user has
only turned on **developer options + USB debugging** and connected the device to this computer.

Everything goes through one script with sub-commands. Run them one at a time, read the output, and
react. Entry points:

| OS            | Command                                            |
|---------------|----------------------------------------------------|
| macOS / Linux | `./setup.sh <step> [options]`                      |
| Windows       | `.\setup.ps1 <step> [options]` (or `setup.cmd`)    |

Talk to the user in their language and keep messages short. Tell them before you take over the
screen, and say what they have to do themselves.

## How it works (background)

- Android apps run in a system container ("ANCO"). There is no public API to install APKs into it.
- **卓易通 (`com.droi.tong`)**, an AppGallery app, accepts `.apk` files via "open with" and hands
  them to the Android package installer in the container. A check inside the container then allows
  or refuses the app. For 卓易通 the rule is a block-list by package name, and `com.aurora.store`
  is on it.
- So the script downloads the official APK, **verifies its signing certificate**, renames the
  package (default `com.ab.astore`), signs it with a **key that stays on this computer**, and
  hands the result to 卓易通.
- Nothing modified is ever published: the user builds their own copy locally. That keeps it clean
  under the GPL, and the user knows exactly what runs on their device.

## Ground rules

- **The user does these steps, never you:** unlocking the device (PIN), Huawei-ID / Google logins,
  accepting licence or privacy terms (e.g. 卓易通's first-start agreement), installing 卓易通 from
  AppGallery, and switching on Android special permissions. You open the right screen and explain
  what to tap.
- **Stay on the official path.** Do not spoof the installer origin, impersonate 卓易通 or
  EasyAbroad, tamper with the container, or work around a refusal by the container's check. If an
  install is refused, report the exact message.
- Only build from an APK whose certificate the script has verified. Never commit or upload the
  built APK or the keystore.
- The device may be shared with other work (other apps, another agent). Check what is on screen
  (`ui dump` / `ui shot`) before longer UI sequences, and leave other apps alone.
- **Keystore = Aurora's identity.** Every later update must be signed with the same key, or it can
  only be installed after uninstalling Aurora (its data is lost). Tell the user to back up
  `~/.aurora-setup/keystore/`.

## Prerequisites (check with `doctor`)

1. **Python 3.8+** (standard library only).
2. **Java 17+ JDK**, including `keytool`. macOS: `brew install openjdk@17`. Linux:
   `apt install openjdk-17-jdk-headless`. Windows: `winget install EclipseAdoptium.Temurin.17.JDK`.
3. **hdc**, the HarmonyOS device bridge. Any of these works:
   - DevEco Studio or the *HarmonyOS Command Line Tools* from developer.huawei.com (Huawei-ID
     login: the user does that). Put `.../sdk/default/openharmony/toolchains` on `PATH`.
   - `./setup.sh get-hdc`: streams the public OpenHarmony 7.0 SDK from Huawei Cloud's mirror
     (about 1.3 GB on macOS, 3.2 GB on Windows/Linux), checks the archive's SHA-256 and keeps only
     `hdc`, in `~/.aurora-setup/tools/hdc/`. No login needed. On Linux this only works on x86_64. The macOS build is
     x86_64 too: on Apple Silicon it needs Rosetta (`softwareupdate --install-rosetta`; the user
     agrees to the licence). Otherwise prefer the arm64 hdc from DevEco Studio or the Command Line Tools.
   - Or pass `--hdc /path/to/hdc`.
4. Device: developer options on (Settings → About → tap the build number 7×), **USB debugging**
   on, the "Allow USB debugging?" prompt accepted, and the USB mode set to file transfer.
   Linux may need a udev rule for the device's USB ID.

## Steps

### 1. `doctor`
Checks Java, tools, hdc, the device (model, HarmonyOS version, API level), whether 卓易通 is
installed, and whether the screen is locked. Prints `READY` or what is missing; it exits with code 2
when something is missing.

- **卓易通 missing:** run `zhuoyitong`. It opens 卓易通's AppGallery page through the deep link
  `store://appgallery.huawei.com/app/detail?id=com.droi.tong`. The user taps Install (Huawei ID
  needed), opens 卓易通 once and accepts its privacy agreement. `hdc install` cannot install it
  (release builds only come from AppGallery).
- **Screen locked:** ask the user to unlock it. Commands that touch the UI refuse to run while it
  is locked.

### 2. `tools`
Downloads `apktool` 3.0.3 and `uber-apk-signer` 1.3.0 into `~/.aurora-setup/tools/` and checks
them against pinned SHA-256 hashes.

### 3. `download [--version X] [--variant hw|standard]`
Reads the latest release from the GitLab API (project 6922885, AuroraOSS/AuroraStore). It prefers
the Huawei build `AuroraStore-hw-<ver>.apk` and falls back to the standard build. It then verifies
the **signing certificate** (v1/v2/v3, via apksigner) against the official SHA-256
`4C:62:61:57:AD:02:BD:A3:40:1A:72:63:55:5F:68:A7:96:63:FC:3E:13:A4:D4:36:9A:12:57:09:41:AA:28:0F`
(from the Aurora README). Any other certificate means **stop and tell the user**.

### 4. `keystore`
Creates `~/.aurora-setup/keystore/aurora-local.jks` with a random password, stored in
`keystore.json` next to it (mode 600). If the user already has a key (an earlier install), import
it instead, so updates keep Aurora's data:
`keystore --import FILE --alias ALIAS --storepass PASS [--keypass PASS]`.

### 5. `build [--package NAME]`
apktool decode → manifest patch → rebuild → zipalign + sign (v1/v2/v3) → verify. The patch:

- `package="com.aurora.store"` becomes the new name. Relative class names would be expanded
  first; Aurora has none.
- Declares and uses `<new>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX derives it from the
  package name at runtime).
- **Provider authorities stay `com.aurora.store.*`**, because the code refers to them as constants.
  Consequence: only **one** rebuilt Aurora can be installed per device (see Troubleshooting).

The package name is remembered. Changing it later needs `--force` and creates a second app.
Output: `~/.aurora-setup/out/AuroraStore-<ver>-<pkg>.apk`.

### 6. `push`
Copies the APK to `/storage/media/100/local/files/Docs/Download/`, which is **Files → Download** on
the device, and checks the size.

### 7. `install`  (UI automation, screen must be unlocked)
1. Opens the Files app directly in Download (deep link `filemanager://openDirectory` with
   `fileUri=file://docs/storage/Users/currentUser/Download`), then taps the APK.
2. If a chooser appears, picks 卓易通.
3. Waits for the Android installer ("Sicherheitswarnung" / security warning) and taps
   **INSTALLIEREN** / INSTALL / 安装 (also AKTUALISIEREN / UPDATE).
4. Waits for "Die Installation war erfolgreich" / "App installed" or a failure text.

If the script cannot find something, it saves a screenshot and stops. Take over with `ui`: tap the
file yourself, then run `install --skip-open` to continue at the installer dialog.

### 8. `launch`
Starts `<pkg>/com.aurora.store.ComposeActivity`. This also checks the install: error `10104001`
means the app is not installed.

### 9. First start and permissions (guided; the user flips the switches)
Aurora's first-start wizard: **Willkommen → Weiter → Berechtigungen** (required: *Installation
unbekannter Apps*, *Speicherverwaltung*; optional: background downloads, notifications, app links)
→ **Fertig** → login choice. Its UI is visible to `ui dump`, so you can drive "Weiter" and "Fertig"
with `ui tap-text`.

**Aurora's "Erlauben"/"Grant" buttons do nothing inside the container.** Open the settings pages
directly instead:

```
permissions                      # list the pages
permissions --page 1 --wait 120  # "Install unknown apps": the user flips the switch; the script waits, then goes back
permissions --page 2 --wait 120  # "All files access" (Aurora's "Speicherverwaltung")
permissions --page 0             # App info: notifications, all permissions, uninstall
```

`--wait` polls the switch through `dumpLayout`. The settings UI is sometimes invisible to
`dumpLayout`; in that case read the screenshot (`~/.aurora-setup/permissions-N.jpeg`) and ask the
user. Coordinates on a 1140×2442 screen: the "Allow app installs" switch is at about (1031, 629),
and the back arrow at about (92, 184). Back also works with `ui key Back`. When the user is done,
tap **Fertig** in the wizard.

**Login:** the user chooses anonymous or Google in Aurora. Do not enter their credentials.

Then suggest the user installs one small app from Aurora to confirm that installs work. Aurora
installs through the container's installer, and the same check applies. In testing, apps installed
by the rebuilt Aurora were accepted.

### Shortcut
`all [--package NAME]` runs doctor → tools → download → keystore → build → push → install →
launch and stops at the first problem. Then continue with step 9.

## Updating Aurora
Aurora cannot update itself, because its signature differs from the official one. Advise the user
to ignore Aurora's self-update offer. To update:

```
download && build && push && install    # same key, same package name → "AKTUALISIEREN"/update keeps data
```

`status` shows the downloaded, built and installed versions.

## UI helpers (`ui …`)

| Command                   | Use                                                                 |
|---------------------------|---------------------------------------------------------------------|
| `ui dump`                 | List visible elements: text, type, bounds, clickable, switch state |
| `ui find REGEX`           | Search the elements by text or description                         |
| `ui tap-text REGEX`       | Tap the first match (centre of its bounds)                         |
| `ui tap X Y`              | Tap raw coordinates (screen pixels)                                |
| `ui shot [FILE]`          | Screenshot (JPEG) for you to look at                               |
| `ui key Back`/`Home`      | Key events                                                          |
| `ui wake`                 | Wake the screen, extend the screen-off timeout to 30 min (an existing override is kept), report lock state |

What `dumpLayout` can see: HarmonyOS apps, the Android package installer, Aurora (Compose
accessibility), and Android Settings only sometimes. Other Android apps may be invisible, so use
screenshots for those. Raw `hdc shell` is fine for read-only checks (`bm dump -n <bundle>`,
`aa dump -a` for running tasks, `param get`).

## Troubleshooting

| Symptom | Cause / action |
|---|---|
| `no device found` | Cable or USB mode, USB debugging off, or the prompt not accepted. Run `hdc list targets`. On Windows, an hdc server from DevEco may already be running (fine). |
| `the device is locked` | The user unlocks it. With developer mode on, `aa start` cannot unlock the screen. |
| Installer: **"Installation fehlgeschlagen – Das Paket ist ungültig / Nicht mit dem System kompatibel"** | Most likely **another rebuilt Aurora is already installed** (different key or package): both claim the `com.aurora.store.*` provider authorities and the signature permission. Update the existing one with its original key (`keystore --import`), or let the user uninstall it first (`permissions --page 0` → DEINSTALLIEREN; its data is lost). |
| Installer refuses with "nicht unterstützt" / not supported | Installed via EasyAbroad or Chrome (allow-list). Use the 卓易通 path only. |
| Update refused, or "App nicht installiert" on update | Signed with a different key. Import the old key. Otherwise uninstall first (data loss). |
| Certificate mismatch in `download` | Do not continue. Report it to the user; the download or the release may be compromised. |
| `apktool` errors | Java too old (17+ needed), or a new Aurora version with new resources. Try `--version` with the previous release and report. |
| Aurora "Erlauben" does nothing | Expected. Use `permissions --page N`. |

## Known dead ends (do not retry)
- Building an own "卓易通" or pushing files into the container: the container's storage is
  locked, and frontends need Huawei approval.
- Installing from Chrome inside the container: EasyAbroad's allow-list rejects even renamed APKs.
- Reaching the container over `127.0.0.1` or `hdc rport`: it has its own network namespace.
- Spoofing the installer or origin: this bypasses a security check, so it is off-limits.
- `hdc install` of 卓易通 or other release HAPs: not possible, they come from AppGallery.
- Third-party Android keyboards in the container: they install, but only the `AncoIme` bridge can
  be enabled.

## Files
- `scripts/aurora_setup.py`: all logic (Python standard library).
- `setup.sh`, `setup.ps1`, `setup.cmd`: launchers.
- `~/.aurora-setup/` (or `--home` / `AURORA_SETUP_HOME`): `state.json`, `tools/`, `downloads/`,
  `keystore/` (**back this up**), `out/`, screenshots.
