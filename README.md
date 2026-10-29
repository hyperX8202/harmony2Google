# Gclick – Google on HarmonyOS in a few taps

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-TeamHX-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/TeamHX)

**Gclick** is a step-by-step setup wizard for HarmonyOS NEXT phones (tested on the Huawei Mate XT 2
with HarmonyOS 7). It walks you through everything needed to use Google apps on the device:
permissions, the [gBART keyboard](https://github.com/hyperX8202/gbart), EasyAbroad, the Google apps,
a modified Aurora Store, your Google account, your contacts and (optionally) your calendar.

You do not need a computer, USB debugging or developer mode. Gclick is an Android app that runs in
the Android container of HarmonyOS ("ANCO"). It is installed through
卓易通 (Zhuoyitong / DroiTong), an app from AppGallery.

> **Important – Gclick installs a modified Aurora Store.** Please read
> [About the modified Aurora Store](#about-the-modified-aurora-store) before you use it.

Repository name: `harmony2Google`. The repo also contains the older agent-driven Aurora-only setup,
see [Alternative: agent-driven Aurora setup](#alternative-agent-driven-aurora-setup).

## Install

1. Download `Gclick_1.0.apk` from the [latest release](../../releases/latest) to your phone
   (the same file is also kept in the repo: [`gclick/release/Gclick_1.0.apk`](gclick/release/Gclick_1.0.apk)).
   - SHA-256: `72e806315e78a52d9ae444c3eb99ce49a89b4dff96f9910ff83bcae11d046be6`
   - Signing certificate SHA-256:
     `AB:5D:98:1F:9A:20:0A:AB:76:FC:AA:B4:C4:17:AB:CC:42:D3:44:9C:92:59:9A:AE:EB:93:07:94:9F:48:3A:AA`
   - Or build it yourself, see [Build from source](#build-from-source).
2. Open **Files → Download** and tap the APK. HarmonyOS sends you to AppGallery to install
   **卓易通**. Install it, open it once and accept its terms. Then it installs Gclick.
3. Open **Gclick** and follow the steps. Each step appears only after the previous one is done, and
   finished steps collapse with a ✔.

Gclick speaks English, German, French, Spanish, Italian, Portuguese, Dutch, Polish, Turkish,
Russian and Chinese and follows the system language.

## What Gclick does

| # | Step | What happens |
|---|------|--------------|
| 1 | **Permissions** | Opens the right settings page for each permission: display over other apps, install apps, access to all files, notifications, contacts, calendar, battery exemption, and "pop-ups in the background" (this one is a HarmonyOS host setting that Gclick cannot read, so you confirm it yourself). |
| 2 | **gBART keyboard** | Opens the gBART page in AppGallery so you can install the HarmonyOS keyboard. |
| 3 | **EasyAbroad** | Opens EasyAbroad in AppGallery. You install it, start it once and accept its terms. |
| 4 | **Google apps** | Installs Chrome, Google Maps, YouTube, Google, Gmail, Google Drive and Google Photos through EasyAbroad, one after another, and brings you back to Gclick after each one. **Chrome is installed first and takes a long time. Be patient.** |
| 5 | **Aurora Store** | Installs the modified Aurora Store and guides you through the microG setting "Allow App Installation". |
| 6 | **Google sign-in** | Opens the system "Add account" screen so you sign in to your Google account yourself. |
| 7 | **Transfer contacts** | Syncs your Google contacts, builds a vCard file and opens it in the HarmonyOS Contacts app. |
| 8 | **Transfer calendar** (optional) | Opens the Google Calendar export in Chrome, finds the downloaded ZIP and opens each `.ics` file in the HarmonyOS Calendar app. |

Gclick checks what is already installed and skips finished steps. Gclick never sees your
credentials: you type them into the Google sign-in screen yourself.

## About the modified Aurora Store

Gclick does **not** install the official Aurora Store. It installs a **modified build** of
Aurora Store 4.8.4 (Huawei variant), package name `com.ab.astore`. This is why, and what is
different.

**Why.** On this device, apps from EasyAbroad's catalog (Spotify, Firefox, Netflix, Joyn, Google
Photos and thousands more) are blocked when they are installed through any route other than
EasyAbroad itself. An unmodified Aurora Store downloads them, but the installation never completes.

**What is modified:**

- **Installs go through EasyAbroad first.** When you tap *Install* or *Update* for an app that
  EasyAbroad knows, the modified Aurora does not download anything itself. It opens EasyAbroad's
  install page for that app and shows the toast "Installing via EasyAbroad". Apps EasyAbroad does
  not know are installed directly by Aurora as usual. The list of known package names is
  `aurora-mod/assets/easyabroad.txt` (24,060 names, a snapshot taken from EasyAbroad 3.05.62; it
  may become outdated).
- **Only the microG installer is available.** The other installers are removed from the settings
  and microG is selected permanently, because it is the only one that works in this container.
  microG refuses installs until you switch on *Allow App Installation* (EasyAbroad framework
  settings → Play Store services → ⋮ → App Installer Settings). Gclick checks this for you and
  shows where to tap.
- **The "install unknown apps" button of the first-start wizard now works** (it opens the correct
  container settings page).
- **Package name and signature.** The app is renamed to `com.ab.astore` and signed with a key that
  is not the official Aurora key, so it cannot be updated from the official releases (and Aurora's
  built-in self-update must be ignored). Only one modified Aurora can be installed per device.
- The helper code only starts EasyAbroad through its public intents and watches whether the
  package got installed. It adds no analytics and no network code. The only permission added to Aurora's
  manifest is `REORDER_TASKS` (to bring Aurora back to the front), plus one internal foreground
  service.

**Open source and verifiable.** Aurora Store is licensed under GPL-3.0, and so is this
modification. The full source of the changes is in [`aurora-mod/`](aurora-mod/):
a patch against the **unmodified official release** (SHA-256 of the release APK is pinned),
the helper classes, the package list and a build script. The build is reproducible: building from
this repo yields the same `classes.dex`, `classes2.dex`, `classes3.dex`, manifest and resources as
the APK inside Gclick (only the signature differs). The unmodified Aurora Store source code is at
<https://gitlab.com/AuroraOSS/AuroraStore>.

**Please note:**

- This is an **unofficial** build. It is not made, endorsed or supported by Aurora OSS, Google,
  Huawei, 卓易通 or EasyAbroad. Do not report problems with it to the Aurora OSS project.
- Aurora signs in to Google Play with **your own Google account**. Using a third-party Play client
  may be against Google's terms of service. Use it at your own risk.
- Everything is provided **as is**, without warranty. You install apps and sign in at your own
  responsibility.

## Known limitations

- After an install through EasyAbroad the modified Aurora does **not** return to the foreground by
  itself. Switch back to Aurora manually.
- Updates of apps that EasyAbroad knows are handed to EasyAbroad even when EasyAbroad has no newer
  version.
- The EasyAbroad package list is a snapshot and can be out of date.
- The calendar is not synced continuously. Step 8 imports a one-time export.
- "Pop-ups in the background" and the apps installed on the HarmonyOS side (gBART, EasyAbroad)
  cannot be detected from inside the container, so those steps need your confirmation.

## Troubleshooting

| Problem | What to do |
|---------|------------|
| Aurora downloads an app but never installs it, or the error says **"Install is disabled"** | Switch on *Allow App Installation*: EasyAbroad framework settings → Play Store services → ⋮ (top right) → App Installer Settings. |
| Some of Aurora's own first-start "Grant" buttons do nothing | That is a limitation of the container. Open the app's permission page from the system settings (Gclick shows where). |
| An update of Gclick or Aurora is refused | The new APK was signed with a different key. Uninstall the old one first (its data is lost) or build with the same key. |
| Chrome takes minutes to install in step 4 | That is normal for the first installation. Wait. |

## Build from source

You need Java 17+ (JDK), `curl`, `patch`, `zip` and `openssl`. No Android SDK, Gradle or Python is
required. The scripts download apktool, uber-apk-signer, R8 and a compile-time `android.jar` stub
(SHA-256 pinned where possible), and create a **new signing key for you** on first use in
`~/.gclick-keys/` (back it up; updates need the same key).

```sh
./setup.sh download --variant hw    # official Aurora Store 4.8.4 (hw); the certificate is verified
aurora-mod/build.sh                 # official APK + patch + helper  ->  aurora-mod/out/astore.apk
gclick/build.sh                     # Gclick sources + that Aurora    ->  gclick/out/Gclick_1.0.apk
```

To use your own key: `KEYSTORE=/path/to.jks KEYSTORE_ALIAS=alias KEYSTORE_PASS=secret`.

## Alternative: agent-driven Aurora setup

Before Gclick existed, this repo only contained a script, driven by an AI agent (Claude Code, Codex,
Cursor), that puts the official Aurora Store on a HarmonyOS device over USB: it downloads the
official APK, verifies the signing certificate, renames the package, signs it with your own key
and installs it through 卓易通. See [AGENTS.md](AGENTS.md) for the runbook.

```sh
./setup.sh doctor       # macOS/Linux (Windows: .\setup.ps1 doctor)
./setup.sh all          # everything up to the first start of Aurora
./setup.sh permissions  # show / open the permission pages
```

Requirements: Python 3.8+, Java 17+ (JDK), `hdc` and a device with developer options and USB
debugging on. Back up `~/.aurora-setup/keystore/`.

## Repository layout

| Path | Content |
|------|---------|
| `gclick/` | The Gclick app: sources (`src/`), manifest and resources (`proj/`), build script, the release APK (`release/`). See [gclick/README.md](gclick/README.md). |
| `aurora-mod/` | The modified Aurora Store: patch, helper classes, EasyAbroad package list, build script. See [aurora-mod/README.md](aurora-mod/README.md). |
| `scripts/`, `setup.*`, `AGENTS.md` | The agent-driven Aurora-only setup. |

## Support

Gclick and gBART are free and have no ads. If they saved you some time, you can support the
development with a coffee: [buymeacoffee.com/TeamHX](https://buymeacoffee.com/TeamHX)

## License and credits

GPL-3.0, see [LICENSE](LICENSE). Gclick builds on and ships a modified
[Aurora Store](https://gitlab.com/AuroraOSS/AuroraStore) (GPL-3.0, © Aurora OSS and contributors).
The keyboard is [gBART](https://github.com/hyperX8202/gbart). Gclick is a Team HX project.
