# aurora-setup

Richtet den **Aurora Store** (quelloffener Google-Play-Client, GPL-3.0) auf einem
**HarmonyOS-Gerät** ein, z. B. einem Huawei Mate XT 2 mit HarmonyOS 7. Am besten lässt man das von
einem KI-Agenten erledigen, etwa Claude Code, Codex oder Cursor.

## Was du selbst tun musst

1. Am Gerät die **Entwickleroptionen** einschalten (Einstellungen → Über das Telefon →
   Build-Nummer 7× antippen) und **USB-Debugging** aktivieren.
2. Das Gerät per USB anschließen und die Abfrage „USB-Debugging zulassen?“ bestätigen.
3. **卓易通** aus der AppGallery installieren (Huawei-ID nötig) und beim ersten Start die
   Bedingungen akzeptieren.
4. Während der Agent arbeitet, das Gerät entsperren, wenn er darum bittet. Die
   Android-Berechtigungen schaltest du selbst ein; der Agent öffnet dir dafür die richtige Seite.
5. In Aurora anmelden (anonym oder mit Google).

## Was der Agent macht

Siehe [AGENTS.md](AGENTS.md). Kurz zusammengefasst:

1. Die offizielle APK von GitLab laden und das **Signaturzertifikat prüfen**.
2. Lokal mit eigenem Paketnamen neu bauen und **mit deinem eigenen Schlüssel** signieren.
3. Über die Dateien-App an 卓易通 übergeben und im Android-Installer bestätigen.
4. Aurora starten und dich durch die Berechtigungen führen.

Einen Agenten in diesem Ordner starten und z. B. sagen: *„Richte Aurora Store auf meinem
angeschlossenen Gerät ein.“*

Von Hand geht es so:

```sh
./setup.sh doctor          # macOS/Linux   (Windows: .\setup.ps1 doctor)
./setup.sh all             # alles bis zum Start von Aurora
./setup.sh permissions     # Berechtigungsseiten anzeigen / öffnen
```

Voraussetzungen: Python 3.8+, Java 17+ (JDK), `hdc`. Für `hdc` gibt es DevEco Studio bzw. die
HarmonyOS Command Line Tools, oder `./setup.sh get-hdc` (großer Download vom
OpenHarmony-Spiegel, ohne Login).

## Wichtig

- **Sichere `~/.aurora-setup/keystore/`.** Updates müssen mit demselben Schlüssel signiert sein.
  Sonst geht ein Update nur nach dem Deinstallieren, und Auroras Daten sind weg.
- Aurora kann sich nicht selbst aktualisieren. Für ein Update führst du `download`, `build`,
  `push` und `install` erneut aus.
- Pro Gerät ist nur **eine** umgebaute Aurora möglich.
- Dieses Repo enthält **keine** APK und keinen Schlüssel. Jede Kopie wird lokal aus dem geprüften
  Original gebaut. Aurora Store steht unter GPL-3.0, den Quellcode gibt es unter
  https://gitlab.com/AuroraOSS/AuroraStore.
- Kein offizielles Projekt von Aurora OSS, Huawei oder 卓易通. Die Nutzung erfolgt auf eigene
  Verantwortung.

Getestet: Huawei Mate XT 2 (LAP-AL10), HarmonyOS 7.0.0.109 (API 26), 卓易通 1.0.10.81, Aurora
Store 4.8.4 (hw), macOS mit Apple Silicon.
