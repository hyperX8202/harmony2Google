# aurora-mod – the modified Aurora Store used by Gclick

This directory contains **everything that differs from the official Aurora Store 4.8.4 (hw)**
release. Aurora Store is GPL-3.0; so are these changes. Upstream source:
<https://gitlab.com/AuroraOSS/AuroraStore>.

| File | Content |
|------|---------|
| `patches/aurora-4.8.4-hw-gclick.patch` | Unified diff against the apktool decode of the official `AuroraStore-hw-4.8.4.apk` (38 files: smali, manifest, `apktool.yml`). |
| `helper/com/ab/hook/*.java` | Java sources of the helper classes (`EA`, `EAService`, `EAAlarm`), compiled to `classes3.dex`. |
| `assets/easyabroad.txt` | Package names EasyAbroad can install (24,060 lines, snapshot from EasyAbroad 3.05.62). |
| `common.sh`, `build.sh` | Build scripts. |

The release APK that the patch applies to is pinned by SHA-256
(`9fe9c3c6a64998da7550de6cb1a4ef9c0f60d4142eaf33448c58d68e7b6e6863`). Get it with
`./setup.sh download --variant hw` in the repo root; that step also verifies the official signing
certificate.

## What the changes do

Class names are the obfuscated names of the official 4.8.4 build.

- **Package rename** to `com.ab.astore` (manifest and the places that carry the package name).
- **Installer chosen per app** (`o20`, `o20$a`, `ix3`, and the call sites `zf2`, `op5`, `vh2`, `ng2`):
  the stored installer preference is ignored. `o20.a(ZI)` takes the installer index from the caller;
  the new wrappers `o20.f(String)` / `o20.g(Download)` ask `com.ab.hook.EA.idx(context, package)`:
  index 6 (microG) if EasyAbroad knows the package (only reached when the EasyAbroad hand-off
  failed), index 0 (session installer, i.e. DroiTong) for every other app. The installer option
  list in the settings has a single entry (microG) and is only a display.
- **EasyAbroad switch** (`zf2`, the download helper): at the start of the "install app" and
  "update app" functions the package name is checked with `com.ab.hook.EA.handle(context, pkg)`.
  If EasyAbroad knows the package, `EA` starts EasyAbroad's install page for it
  (`com.easy.abroad/.activities.MainActivity` with `openAppEntrance=2`, `type=activity_app_detail`,
  `package_name`, `operation=install`) and Aurora skips its own download. In every other case, or if
  anything fails, the original Aurora code runs.
- **`EAService` / `EAAlarm`**: a foreground service that watches the package's `lastUpdateTime`
  and then tries to bring Aurora's task back to the front. This part is **not reliable** yet.
- **First-start wizard** (`iz5`): the "install unknown apps" button opens the container's
  `ManageAppExternalSourcesActivity` directly.
- **Manifest**: `REORDER_TASKS` permission, the `EAService` service, the `EAAlarm` receiver.

## Build and verify

```sh
./setup.sh download --variant hw
aurora-mod/build.sh            # -> aurora-mod/out/astore.apk, signed with your key
```

The script: decodes the official APK, applies the patch (`patch -p1`), adds `easyabroad.txt`,
compiles the helper to `classes3.dex`, rebuilds with apktool 3.0.3 and signs. Building with the same
tool versions gives byte-identical `classes*.dex`, `AndroidManifest.xml`, `resources.arsc` and
assets as the APK embedded in the Gclick release; only the signature block differs.

## Updating the package list

`easyabroad.txt` was extracted from EasyAbroad's own package lists. To refresh it, extract the
lists from a newer EasyAbroad APK and replace the file. One package name per line, sorted.
