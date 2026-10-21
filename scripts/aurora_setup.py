#!/usr/bin/env python3
"""Aurora Store setup for HarmonyOS devices (Android container "ANCO" via 卓易通).

Every step is a sub-command so an agent can run, inspect and repeat them one by one:

    doctor        check host tools, device connection, HarmonyOS version, 卓易通
    zhuoyitong    open 卓易通 in AppGallery (the user installs it)
    tools         download apktool + uber-apk-signer (pinned SHA-256)
    get-hdc       download hdc from the public OpenHarmony SDK mirror (large download)
    download      fetch the official Aurora Store APK from GitLab and verify its certificate
    keystore      create (or import) the personal signing key
    build         rename the package, rebuild and sign the APK locally
    push          copy the APK to the device's Download folder
    install       hand the APK to 卓易通 and confirm the Android installer (UI automation)
    launch        start Aurora Store on the device
    permissions   open the Android settings pages Aurora needs and explain what to switch on
    ui            helpers for agents: dump | find | tap | tap-text | shot | key | wake
    all           doctor, tools, download, keystore, build, push, install, launch
    status        show versions, key and package name from the local state

State (downloads, key, builds) lives in ~/.aurora-setup (override with --home or AURORA_SETUP_HOME).
Only Python 3.8+ standard library is used; Java 17+ and hdc must be installed.
"""

import argparse
import hashlib
import io
import json
import os
import platform
import re
import secrets
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

# --- constants --------------------------------------------------------------------------------

GITLAB_PROJECT = "6922885"  # AuroraOSS/AuroraStore
GITLAB_API = f"https://gitlab.com/api/v4/projects/{GITLAB_PROJECT}/releases"
GITLAB_UPLOAD = f"https://gitlab.com/-/project/{GITLAB_PROJECT}"
# Signing certificate of the official Aurora Store builds (README of AuroraOSS/AuroraStore).
AURORA_CERT_SHA256 = "4c626157ad02bda3401a7263555f68a79663fc3e13a4d4369a12570941aa280f"
AURORA_PACKAGE = "com.aurora.store"
AURORA_LAUNCH_ACTIVITY = "com.aurora.store.ComposeActivity"
DEFAULT_PACKAGE = "com.ab.astore"

TOOLS = {
    "apktool.jar": (
        "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar",
        "dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423",
    ),
    "uber-apk-signer.jar": (
        "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar",
        "e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835",
    ),
}

OHOS_MIRROR = "https://repo.huaweicloud.com/openharmony/os/7.0-Release"

ZHUOYITONG = "com.droi.tong"
FILES_APP = "com.huawei.hmos.files"
DEVICE_DOWNLOAD_DIR = "/storage/media/100/local/files/Docs/Download"
DEVICE_TMP = "/data/local/tmp"

# Button texts of the Android package installer / chooser (de, en, zh). Matched case-insensitively.
TEXT_INSTALL = r"^(installieren|install|安装|aktualisieren|update|更新)$"
TEXT_DONE = r"(installation war erfolgreich|app installed|app wurde installiert|应用已安装|安装成功)"
TEXT_FAILED = r"(fehlgeschlagen|failed|nicht installiert|installieren nicht möglich|not installed|无法安装|安装失败|nicht unterstützt)"
TEXT_ZHUOYITONG = r"(卓易通|zhuoyitong)"


class SetupError(Exception):
    pass


# --- helpers ----------------------------------------------------------------------------------

def log(msg):
    print(msg, flush=True)


def step(msg):
    print(f"\n==> {msg}", flush=True)


def run(cmd, check=True, capture=True, timeout=None):
    """Run a command (list), return stdout text."""
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE if capture else None,
                             stderr=subprocess.STDOUT if capture else None, timeout=timeout)
    except FileNotFoundError:
        raise SetupError(f"program not found: {cmd[0]}")
    out = res.stdout.decode("utf-8", "replace") if capture and res.stdout is not None else ""
    if check and res.returncode != 0:
        raise SetupError(f"command failed ({res.returncode}): {' '.join(map(str, cmd))}\n{out}")
    return out


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, dest, expected_sha256=None):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    log(f"    downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "aurora-setup"})
    with urllib.request.urlopen(req, timeout=60) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f, 1 << 20)
    if expected_sha256:
        got = sha256_file(tmp)
        if got != expected_sha256:
            tmp.unlink()
            raise SetupError(f"SHA-256 mismatch for {url}\n  expected {expected_sha256}\n  got      {got}")
    tmp.replace(dest)
    return dest


def http_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": "aurora-setup"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


# --- state ------------------------------------------------------------------------------------

class State:
    def __init__(self, home):
        self.home = Path(home).expanduser()
        self.file = self.home / "state.json"
        self.data = json.loads(self.file.read_text()) if self.file.exists() else {}

    def save(self):
        self.home.mkdir(parents=True, exist_ok=True)
        self.file.write_text(json.dumps(self.data, indent=2) + "\n")

    def get(self, key, default=None):
        return self.data.get(key, default)

    def set(self, **kw):
        self.data.update(kw)
        self.save()

    @property
    def tools(self):
        return self.home / "tools"

    @property
    def key_dir(self):
        return self.home / "keystore"


# --- host tools -------------------------------------------------------------------------------

def find_hdc(args):
    if args.hdc:
        return args.hdc
    for name in ("hdc", "hdc.exe"):
        p = shutil.which(name)
        if p:
            return p
    local = Path(args.home).expanduser() / "tools" / "hdc" / ("hdc.exe" if os.name == "nt" else "hdc")
    if local.exists():
        return str(local)
    candidates = []
    if sys.platform == "darwin":
        candidates.append("/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/toolchains/hdc")
    elif os.name == "nt":
        for base in (os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("LOCALAPPDATA", "")):
            candidates.append(os.path.join(base, "Huawei", "DevEco Studio", "sdk", "default", "openharmony",
                                           "toolchains", "hdc.exe"))
    for c in candidates:
        if c and os.path.exists(c):
            return c
    return None


def java():
    j = shutil.which("java")
    if not j:
        raise SetupError("Java 17+ not found (install e.g. Temurin 17 and make 'java' available on PATH)")
    return j


def keytool():
    k = shutil.which("keytool")
    if not k:
        jh = os.environ.get("JAVA_HOME")
        if jh:
            cand = Path(jh) / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")
            if cand.exists():
                return str(cand)
        raise SetupError("keytool not found (it ships with the JDK; put the JDK's bin folder on PATH)")
    return k


def tool_jar(state, name):
    p = state.tools / name
    if not p.exists():
        raise SetupError(f"{name} missing - run the 'tools' step first")
    return str(p)


# --- device -----------------------------------------------------------------------------------

class Device:
    def __init__(self, hdc, target=None):
        if not hdc:
            raise SetupError("hdc not found. Install DevEco Studio / HarmonyOS Command Line Tools, "
                             "or run 'get-hdc', or pass --hdc PATH")
        self.hdc = hdc
        self.target = target

    def _base(self):
        return [self.hdc] + (["-t", self.target] if self.target else [])

    def targets(self):
        out = run([self.hdc, "list", "targets"], check=False)
        return [l.strip() for l in out.splitlines() if l.strip() and "[Empty]" not in l]

    def shell(self, cmd, check=False, timeout=120):
        return run(self._base() + ["shell", cmd], check=check, timeout=timeout)

    def send(self, local, remote):
        out = run(self._base() + ["file", "send", str(local), remote], check=False, timeout=600)
        if "finish" not in out.lower():
            raise SetupError(f"hdc file send failed:\n{out}")

    def recv(self, remote, local):
        out = run(self._base() + ["file", "recv", remote, str(local)], check=False, timeout=120)
        if "finish" not in out.lower():
            raise SetupError(f"hdc file recv failed:\n{out}")

    def param(self, name):
        return self.shell(f"param get {name}").strip()

    def bundle_installed(self, bundle):
        return self.shell(f"bm dump -n {bundle}").lstrip().startswith(f"{bundle}:")

    # screen
    def screen_locked(self):
        # The window table lists visible windows first, then a line of dashes, then hidden ones (ZOrd -1).
        out = self.shell("hidumper -s WindowManagerService -a '-a'")
        in_table = False
        for line in out.splitlines():
            if line.startswith("WindowName"):
                in_table = True
                continue
            if in_table and re.fullmatch(r"-{20,}", line.strip()):
                break
            m = re.match(r"SCBScreenLock\S*\s+(?:\S+\s+){6}(-?\d+)", line)
            if in_table and m and m.group(1) != "-1":
                return True
        return False

    def keep_awake(self):
        """Wake the screen and extend the screen-off timeout to 30 min. Returns True if we set the override."""
        self.shell("power-shell wakeup")
        dump = self.shell("hidumper -s 3301 -a -s")
        m = re.search(r"OverrideTimeout=(-?\d+)", dump)
        if m and int(m.group(1)) > 0:
            return False  # someone else (e.g. another session) already set an override; leave it alone
        self.shell("power-shell timeout -o 1800000")
        return True

    def restore_timeout(self):
        self.shell("power-shell timeout -r")

    # UI
    def dump(self):
        remote = f"{DEVICE_TMP}/aurora_setup_ui.json"
        self.shell(f"uitest dumpLayout -p {remote}", timeout=60)
        local = Path(tempfile.gettempdir()) / f"aurora_setup_ui_{os.getpid()}.json"
        self.recv(remote, local)
        self.shell(f"rm {remote}")
        data = json.loads(local.read_text(encoding="utf-8"))
        local.unlink()
        nodes = []

        def walk(n):
            a = n.get("attributes", {})
            b = re.findall(r"-?\d+", a.get("bounds", ""))
            if len(b) == 4:
                x1, y1, x2, y2 = map(int, b)
                nodes.append({"text": a.get("text", ""), "type": a.get("type", ""), "id": a.get("id", ""),
                              "bundle": a.get("bundleName", ""), "bounds": (x1, y1, x2, y2),
                              "clickable": a.get("clickable") == "true", "checked": a.get("checked"),
                              "desc": a.get("description", "")})
            for c in n.get("children", []):
                walk(c)

        walk(data)
        return nodes

    def find(self, pattern, nodes=None):
        nodes = self.dump() if nodes is None else nodes
        rx = re.compile(pattern, re.I)
        return [n for n in nodes if (n["text"] and rx.search(n["text"].strip())) or
                (n["desc"] and rx.search(n["desc"].strip()))]

    def tap(self, x, y):
        self.shell(f"uitest uiInput click {int(x)} {int(y)}")

    def tap_node(self, n):
        x1, y1, x2, y2 = n["bounds"]
        self.tap((x1 + x2) / 2, (y1 + y2) / 2)

    def key(self, name):
        self.shell(f"uitest uiInput keyEvent {name}")

    def screenshot(self, local):
        remote = f"{DEVICE_TMP}/aurora_setup_shot.jpeg"
        self.shell(f"snapshot_display -f {remote}")
        self.recv(remote, local)
        self.shell(f"rm {remote}")
        return local

    def wait_for(self, pattern, timeout=30, interval=1.5):
        end = time.time() + timeout
        while time.time() < end:
            hits = self.find(pattern)
            if hits:
                return hits
            time.sleep(interval)
        return []

    def start(self, bundle, ability, module=None, action=None, uri=None, mime=None, params=None):
        cmd = f"aa start -b {bundle} -a '{ability}'"
        if module:
            cmd += f" -m {module}"
        if action:
            cmd += f" -A {action}"
        if uri:
            cmd += f" -U '{uri}'"
        if mime:
            cmd += f" -t {mime}"
        for k, v in (params or {}).items():
            cmd += f" --ps {k} '{v}'"
        return self.shell(cmd)


def device(args):
    d = Device(find_hdc(args), args.target)
    t = d.targets()
    if not t:
        raise SetupError("no device found by hdc. Check: USB cable (data), developer options + USB debugging on, "
                         "'Allow USB debugging' confirmed on the phone, USB mode set to file transfer")
    if len(t) > 1 and not args.target:
        raise SetupError(f"several devices connected ({', '.join(t)}); pass --target SERIAL")
    return d


def require_unlocked(d):
    if d.screen_locked():
        raise SetupError("the device is locked. Ask the user to unlock it (PIN) and run the step again.")


# --- commands ---------------------------------------------------------------------------------

def cmd_doctor(args, state):
    ok = True
    step("Host")
    log(f"    OS: {platform.system()} {platform.machine()}, Python {platform.python_version()}")
    try:
        v = run([java(), "-version"], check=False).splitlines()[0]
        m = re.search(r'version "(\d+)', v)
        major = int(m.group(1)) if m else 0
        log(f"    Java: {v}" + ("" if major >= 17 else "  <-- need 17 or newer"))
        ok &= major >= 17
        keytool()
    except SetupError as e:
        log(f"    !! {e}")
        ok = False
    for name in TOOLS:
        p = state.tools / name
        log(f"    {name}: {'ok' if p.exists() else 'missing (run: tools)'}")
    hdc = find_hdc(args)
    log(f"    hdc: {hdc or 'NOT FOUND'}")
    if not hdc:
        return False

    step("Device")
    d = Device(hdc, args.target)
    t = d.targets()
    if not t:
        log("    !! no device. Developer options + USB debugging on? USB debugging prompt accepted on the phone?")
        return False
    log(f"    targets: {', '.join(t)}")
    if len(t) > 1 and not args.target:
        log("    !! several devices - pass --target")
        return False
    model = d.param("const.product.model")
    osv = d.param("const.product.software.version")
    api = d.param("const.ohos.apiversion")
    log(f"    model: {model}  software: {osv}  API: {api}")
    if d.param("const.ohos.anco.audio.version") == "":
        log("    ?? no ANCO marker found - this device may not have the Android container")
    zy = d.bundle_installed(ZHUOYITONG)
    log(f"    卓易通 ({ZHUOYITONG}): {'installed' if zy else 'NOT installed - install it from AppGallery (user)'}")
    ok &= zy
    locked = d.screen_locked()
    log(f"    screen: {'LOCKED - user must unlock' if locked else 'unlocked'}")

    step("Local state")
    log(f"    home: {state.home}")
    log(f"    package: {state.get('package', DEFAULT_PACKAGE + ' (default)')}")
    log(f"    keystore: {'present' if (state.key_dir / 'keystore.json').exists() else 'not created yet'}")
    log(f"    downloaded: {state.get('downloaded_version', '-')}  built: {state.get('built_version', '-')}")
    log("\nREADY" if ok else "\nNOT READY - fix the items marked above")
    return ok


def cmd_tools(args, state):
    step("Downloading build tools")
    for name, (url, sha) in TOOLS.items():
        p = state.tools / name
        if p.exists() and sha256_file(p) == sha:
            log(f"    {name}: ok")
            continue
        download(url, p, sha)
        log(f"    {name}: downloaded and verified")


def cmd_get_hdc(args, state):
    """Stream the OpenHarmony SDK archive and extract only hdc (no login needed, but 1-3 GB download)."""
    system = platform.system()
    if system == "Darwin":
        archive, pattern = "ohos-sdk-mac-public.tar.gz", r"toolchains-darwin-(arm64|x64)?.*\.zip$"
    elif system in ("Linux", "Windows"):
        archive = "ohos-sdk-windows_linux-public.tar.gz"
        pattern = r"linux/toolchains-linux-x64.*\.zip$" if system == "Linux" else r"windows/toolchains-windows-x64.*\.zip$"
    else:
        raise SetupError(f"unsupported OS {system}")
    if system == "Linux" and platform.machine().lower() not in ("x86_64", "amd64"):
        raise SetupError("the OpenHarmony SDK only ships hdc for x86_64 Linux")
    url = f"{OHOS_MIRROR}/{archive}"
    step(f"Downloading {url} (large) and extracting hdc")
    expected = urllib.request.urlopen(url + ".sha256", timeout=30).read().decode().split()[0].lower()
    dest = state.tools / "hdc"
    dest.mkdir(parents=True, exist_ok=True)

    class Hashing(io.RawIOBase):
        def __init__(self, r):
            self.r, self.h, self.n = r, hashlib.sha256(), 0

        def readable(self):
            return True

        def readinto(self, b):
            data = self.r.read(len(b))
            self.h.update(data)
            self.n += len(data)
            if self.n // (100 << 20) != (self.n - len(data)) // (100 << 20):
                log(f"    {self.n >> 20} MB")
            b[:len(data)] = data
            return len(data)

    req = urllib.request.Request(url, headers={"User-Agent": "aurora-setup"})
    extracted = {}
    with urllib.request.urlopen(req, timeout=60) as r:
        hr = Hashing(r)
        buf = io.BufferedReader(hr, 1 << 20)
        with tarfile.open(fileobj=buf, mode="r|gz") as tar:
            for m in tar:
                if m.isfile() and re.search(pattern, m.name) and not extracted:
                    log(f"    found {m.name}")
                    z = zipfile.ZipFile(io.BytesIO(tar.extractfile(m).read()))
                    for n in z.namelist():
                        if re.search(r"(^|/)(hdc|hdc\.exe|libusb[^/]*)$", n):
                            extracted[Path(n).name] = z.read(n)
        while buf.read(1 << 20):  # the checksum covers the whole archive
            pass
    if not extracted:
        raise SetupError("toolchains archive not found inside the SDK")
    if hr.h.hexdigest() != expected:
        raise SetupError(f"SDK archive SHA-256 mismatch (expected {expected}, got {hr.h.hexdigest()})")
    for name, data in extracted.items():
        target = dest / name
        target.write_bytes(data)
        target.chmod(0o755)
        log(f"    -> {target}")
    log("    archive SHA-256 verified")
    exe = dest / ("hdc.exe" if os.name == "nt" else "hdc")
    try:
        log(f"    hdc version: {run([str(exe), '-v'], check=False).strip()}")
    except (OSError, SetupError) as e:
        hint = (" The macOS SDK ships an x86_64 hdc: on Apple Silicon install Rosetta with "
                "'softwareupdate --install-rosetta --agree-to-license' (the user agrees to the licence), "
                "or use the hdc from DevEco Studio." if sys.platform == "darwin" else "")
        raise SetupError(f"extracted {exe} but it does not run: {e}.{hint}")


def fetch_release(version=None):
    if version:
        rels = [http_json(f"{GITLAB_API}/{version}")]
    else:
        rels = [r for r in http_json(f"{GITLAB_API}?per_page=10") if not r.get("upcoming_release")]
    for rel in rels:
        assets = {}
        for name, path in re.findall(r"\[([^\]]+\.apk)\]\((/uploads/[0-9a-f]+/[^)]+)\)", rel["description"]):
            assets[name] = GITLAB_UPLOAD + path
        for link in rel.get("assets", {}).get("links", []):
            if link["name"].endswith(".apk"):
                assets[link["name"]] = link.get("direct_asset_url") or link["url"]
        if assets:
            return rel["tag_name"], assets
    raise SetupError("no Aurora Store release with APK assets found")


def verify_aurora_cert(state, apk):
    out = run([java(), "-jar", tool_jar(state, "uber-apk-signer.jar"), "-a", str(apk), "--onlyVerify",
               "--verbose"], check=False)
    if "signature verified" not in out:
        raise SetupError(f"signature of {apk} does not verify:\n{out}")
    certs = set(re.findall(r"\bSHA256: ([0-9a-f]{64}) /", out))
    if certs != {AURORA_CERT_SHA256}:
        raise SetupError(f"unexpected signing certificate(s) {certs or '-'}; expected {AURORA_CERT_SHA256}. "
                         f"Do NOT install this file.")
    schemes = re.search(r"signature verified \[([^\]]*)\]", out)
    return schemes.group(1) if schemes else "?"


def cmd_download(args, state):
    step("Looking up Aurora Store release on GitLab")
    version, assets = fetch_release(args.version)
    variant = args.variant
    want = f"AuroraStore-hw-{version}.apk" if variant == "hw" else f"AuroraStore-{version}.apk"
    if want not in assets:
        if variant == "hw" and f"AuroraStore-{version}.apk" in assets:
            log(f"    no Huawei build for {version}, using the standard build")
            want = f"AuroraStore-{version}.apk"
        else:
            raise SetupError(f"{want} not in release {version}: {', '.join(assets)}")
    log(f"    version {version}, file {want}")
    dest = state.home / "downloads" / want
    if not dest.exists():
        download(assets[want], dest)
    step("Verifying signing certificate")
    schemes = verify_aurora_cert(state, dest)
    log(f"    ok: official Aurora certificate {AURORA_CERT_SHA256[:16]}..., schemes [{schemes}]")
    log(f"    file SHA-256 {sha256_file(dest)}")
    state.set(downloaded_version=version, downloaded_apk=str(dest), downloaded_url=assets[want])


def keystore_info(state):
    f = state.key_dir / "keystore.json"
    return json.loads(f.read_text()) if f.exists() else None


def cmd_keystore(args, state):
    info = keystore_info(state)
    state.key_dir.mkdir(parents=True, exist_ok=True)
    if args.import_keystore:
        if info and not args.force:
            raise SetupError("a keystore already exists; use --force to replace it (apps signed with the old key "
                             "can then only be updated after uninstalling = data loss)")
        src = Path(args.import_keystore).expanduser()
        dst = state.key_dir / "aurora-local.jks"
        shutil.copyfile(src, dst)
        info = {"file": dst.name, "alias": args.alias, "storepass": args.storepass,
                "keypass": args.keypass or args.storepass}
        run([keytool(), "-list", "-keystore", str(dst), "-storepass", info["storepass"], "-alias", info["alias"]])
        (state.key_dir / "keystore.json").write_text(json.dumps(info, indent=2) + "\n")
        try:
            os.chmod(state.key_dir / "keystore.json", 0o600)
            os.chmod(dst, 0o600)
        except OSError:
            pass
        log(f"    imported {src} -> {dst}")
    elif info:
        log(f"    keystore exists: {state.key_dir / info['file']}")
    else:
        step("Creating personal signing key")
        pw = secrets.token_urlsafe(18)
        dst = state.key_dir / "aurora-local.jks"
        run([keytool(), "-genkeypair", "-keystore", str(dst), "-storetype", "PKCS12", "-alias", "aurora",
             "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000", "-storepass", pw, "-keypass", pw,
             "-dname", "CN=Aurora Store local build"])
        info = {"file": dst.name, "alias": "aurora", "storepass": pw, "keypass": pw}
        (state.key_dir / "keystore.json").write_text(json.dumps(info, indent=2) + "\n")
        try:
            os.chmod(state.key_dir / "keystore.json", 0o600)
            os.chmod(dst, 0o600)
        except OSError:
            pass
        log(f"    created {dst}")
    out = run([keytool(), "-list", "-v", "-keystore", str(state.key_dir / info["file"]), "-storepass",
               info["storepass"], "-alias", info["alias"]], check=False)
    m = re.search(r"SHA256:\s*([0-9A-F:]{95})", out)
    log(f"    certificate SHA-256: {m.group(1) if m else '?'}")
    log("    IMPORTANT: back up the folder " + str(state.key_dir) + " - every future Aurora update must be signed "
        "with this key, otherwise it can only be installed after uninstalling (losing Aurora's data).")


def patch_manifest(path, new_pkg):
    text = Path(path).read_text(encoding="utf-8")
    m = re.search(r'\bpackage="([^"]+)"', text)
    if not m:
        raise SetupError("package attribute not found in AndroidManifest.xml")
    old = m.group(1)
    if old != AURORA_PACKAGE:
        log(f"    note: original package is {old}")
    # Expand relative class names (".Foo") before renaming, otherwise they would resolve to the new package.
    text = re.sub(r'(android:name|android:targetActivity|android:backupAgent)="\.([^"]+)"',
                  lambda mm: f'{mm.group(1)}="{old}.{mm.group(2)}"', text)
    text = text.replace(f'package="{old}"', f'package="{new_pkg}"', 1)
    # AndroidX derives <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION at runtime from the package name.
    dyn_old = f"{old}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    dyn_new = f"{new_pkg}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    if dyn_old in text and dyn_new not in text:
        anchor = re.search(rf'<uses-permission android:name="{re.escape(dyn_old)}"\s*/>', text)
        ins = (f'\n    <permission android:name="{dyn_new}" android:protectionLevel="signature"/>'
               f'\n    <uses-permission android:name="{dyn_new}"/>')
        if anchor:
            text = text[:anchor.end()] + ins + text[anchor.end():]
        else:
            text = text.replace("<application", ins.strip() + "\n    <application", 1)
    Path(path).write_text(text, encoding="utf-8")
    return old


def cmd_build(args, state):
    apk = state.get("downloaded_apk")
    if not apk or not Path(apk).exists():
        raise SetupError("no downloaded APK - run 'download' first")
    info = keystore_info(state)
    if not info:
        raise SetupError("no keystore - run 'keystore' first")
    pkg = args.package or state.get("package") or DEFAULT_PACKAGE
    if state.get("package") and pkg != state.get("package") and not args.force:
        raise SetupError(f"package name was {state.get('package')} before; changing it creates a second app. "
                         f"Use --force if that is intended.")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", pkg):
        raise SetupError(f"invalid package name {pkg}")
    verify_aurora_cert(state, apk)  # never build from an unverified file
    version = state.get("downloaded_version")
    work = state.home / "build"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    src = work / "src"
    jar = tool_jar(state, "apktool.jar")
    step(f"Decoding {Path(apk).name}")
    run([java(), "-jar", jar, "d", "-f", "-o", str(src), apk])
    step(f"Renaming package to {pkg}")
    old = patch_manifest(src / "AndroidManifest.xml", pkg)
    log(f"    {old} -> {pkg}")
    step("Rebuilding")
    unsigned = work / "unsigned.apk"
    run([java(), "-jar", jar, "b", "-f", "-o", str(unsigned), str(src)])
    step("Signing (zipalign + v1/v2/v3)")
    outdir = work / "signed"
    run([java(), "-jar", tool_jar(state, "uber-apk-signer.jar"), "-a", str(unsigned), "--ks",
         str(state.key_dir / info["file"]), "--ksAlias", info["alias"], "--ksPass", info["storepass"],
         "--ksKeyPass", info["keypass"], "-o", str(outdir)])
    signed = next(outdir.glob("*-aligned-signed.apk"))
    out = run([java(), "-jar", tool_jar(state, "uber-apk-signer.jar"), "-a", str(signed), "--onlyVerify"],
              check=False)
    if "signature verified" not in out:
        raise SetupError(f"signed APK does not verify:\n{out}")
    final = state.home / "out" / f"AuroraStore-{version}-{pkg}.apk"
    final.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(signed, final)
    shutil.rmtree(work)
    log(f"    built {final}")
    state.set(package=pkg, built_version=version, built_apk=str(final))


def cmd_push(args, state):
    apk = state.get("built_apk")
    if not apk or not Path(apk).exists():
        raise SetupError("nothing built yet - run 'build' first")
    d = device(args)
    remote = f"{DEVICE_DOWNLOAD_DIR}/{Path(apk).name}"
    step(f"Copying to device: {remote}")
    d.send(apk, remote)
    size = Path(apk).stat().st_size
    ls = d.shell(f"ls -l '{remote}'")
    if str(size) not in ls:
        raise SetupError(f"file on device has unexpected size:\n{ls}")
    log("    ok (visible in Files > Download)")
    state.set(pushed_file=Path(apk).name)


def open_with_zhuoyitong(d, name):
    """Open Downloads in the Files app and tap the APK; pick 卓易通 if a chooser appears."""
    d.key("Home")
    time.sleep(1)
    d.start(FILES_APP, "EntryAbility", uri="filemanager://openDirectory",
            params={"fileUri": "file://docs/storage/Users/currentUser/Download"})
    hits = d.wait_for(re.escape(name), timeout=10)
    if not hits:
        # fall back: plain start, then the "Download" folder / search would be needed -> let the agent do it
        raise SetupError(f"could not find {name} in the Files app. Open Files > Download manually (or with "
                         f"'ui tap-text') and tap the file, then run 'install --skip-open'.")
    d.tap_node(hits[0])
    time.sleep(2)
    nodes = d.dump()
    chooser = d.find(TEXT_ZHUOYITONG, nodes)
    if chooser:
        log("    chooser shown - picking 卓易通")
        d.tap_node(chooser[0])
        time.sleep(2)


def cmd_install(args, state):
    name = state.get("pushed_file")
    if not name:
        raise SetupError("nothing pushed yet - run 'push' first")
    d = device(args)
    if not d.bundle_installed(ZHUOYITONG):
        raise SetupError("卓易通 is not installed. The user must install it from AppGallery (Huawei ID).")
    require_unlocked(d)
    set_timeout = d.keep_awake()
    try:
        if not args.skip_open:
            step(f"Opening {name} with 卓易通")
            open_with_zhuoyitong(d, name)
        step("Waiting for the Android package installer")
        hits = d.wait_for(TEXT_INSTALL + "|" + TEXT_FAILED, timeout=40)
        if not hits:
            shot = d.screenshot(state.home / "install-timeout.jpeg")
            raise SetupError(f"installer button not found. Look at {shot} and continue with 'ui' commands.")
        failed = [h for h in hits if re.search(TEXT_FAILED, h["text"], re.I)]
        if failed:
            raise SetupError(f"installation refused: {failed[0]['text']!r}")
        texts = " | ".join(n["text"] for n in d.dump() if n["text"])
        log(f"    dialog: {texts[:400]}")
        btn = [h for h in hits if re.match(TEXT_INSTALL, h["text"].strip(), re.I)]
        log(f"    tapping {btn[0]['text']!r}")
        d.tap_node(btn[0])
        step("Waiting for the result")
        res = d.wait_for(TEXT_DONE + "|" + TEXT_FAILED + "|" + TEXT_INSTALL, timeout=120, interval=2)
        while res and all(re.match(TEXT_INSTALL, r["text"].strip(), re.I) for r in res):
            # a second confirmation (e.g. security warning "INSTALLIEREN" / update prompt)
            log(f"    second confirmation: tapping {res[0]['text']!r}")
            d.tap_node(res[0])
            time.sleep(2)
            res = d.wait_for(TEXT_DONE + "|" + TEXT_FAILED + "|" + TEXT_INSTALL, timeout=120, interval=2)
        if not res:
            shot = d.screenshot(state.home / "install-result.jpeg")
            raise SetupError(f"no result after 2 min; screenshot: {shot}")
        if re.search(TEXT_FAILED, res[0]["text"], re.I):
            details = " | ".join(n["text"] for n in d.dump() if n["text"])[:400]
            raise SetupError(f"installation refused: {res[0]['text']!r}\n  screen: {details}\n  If it says "
                             f"'Das Paket ist ungültig' while another rebuilt Aurora (other key or package) is "
                             f"installed: both claim the same provider authorities - see AGENTS.md, Troubleshooting.")
        log(f"    {res[0]['text']}")
        state.set(installed_version=state.get("built_version"))
        done = d.find(r"^(fertig|done|完成)$")
        if done:
            d.tap_node(done[0])
    finally:
        if set_timeout:
            d.restore_timeout()


def cmd_zhuoyitong(args, state):
    """Open 卓易通's AppGallery page; installing it (Huawei ID, terms) is the user's part."""
    d = device(args)
    if d.bundle_installed(ZHUOYITONG):
        log("    卓易通 is already installed")
        return
    require_unlocked(d)
    out = d.start("com.huawei.hmsapp.appgallery", "MainAbility",
                  uri=f"store://appgallery.huawei.com/app/detail?id={ZHUOYITONG}")
    if "successfully" not in out:
        raise SetupError(f"could not open AppGallery: {out.strip()}")
    log("    AppGallery shows 卓易通. Ask the user to tap Install (Huawei ID needed), then to open it once "
        "and accept its terms. Check again with 'doctor'.")


def cmd_launch(args, state):
    d = device(args)
    require_unlocked(d)
    pkg = state.get("package", DEFAULT_PACKAGE)
    out = d.start(pkg, AURORA_LAUNCH_ACTIVITY, module="entry")
    log("    " + out.strip().splitlines()[0])
    if "10104001" in out:
        raise SetupError(f"{pkg} is not installed in the Android container")
    if "successfully" not in out:
        raise SetupError(f"could not start {pkg}:\n{out}")


# Android settings activities in the container (verified on HarmonyOS 7 / Mate XT 2); "package:<pkg>" selects the app.
# Aurora's own "Erlauben"/"Grant" buttons in its first-start wizard do NOT open these pages inside the container.
# uitest dumpLayout sees the settings UI only sometimes, so every page is also captured as a screenshot.
SETTINGS_PAGES = [
    ("com.android.settings.applications.InstalledAppDetails", "App info / App-Info",
     "hub: 'In-App-Installationen', 'Zugriff auf alle Dateien', 'Benachrichtigungen', 'Alle Berechtigungen'"),
    ("com.android.settings.Settings$ManageAppExternalSourcesActivity",
     "Install unknown apps / Unbekannte Apps installieren", "required: lets Aurora install apps"),
    ("com.android.settings.Settings$AppManageExternalStorageActivity", "All files access / Zugriff auf alle Dateien",
     "recommended: Aurora keeps downloads and exports outside its private folder"),
]


def cmd_permissions(args, state):
    d = device(args)
    require_unlocked(d)
    pkg = state.get("package", DEFAULT_PACKAGE)
    if args.page is None:
        log("Pages (open one with --page N, the user flips the switch, then 'ui key Back'):")
        for i, (_, title, why) in enumerate(SETTINGS_PAGES):
            log(f"    {i}: {title} - {why}")
        log("These are Android special permissions: the user switches them on. Aurora's first-start wizard "
            "links to the same pages. Notifications: page 0 > 'Benachrichtigungen'.")
        return
    ability, title, _ = SETTINGS_PAGES[args.page]
    out = d.start("com.android.settings", ability, module="entry", uri=f"package:{pkg}")
    if "successfully" not in out:
        raise SetupError(f"could not open {title}: {out.strip()}")
    time.sleep(2.5)
    shot = d.screenshot(state.home / f"permissions-{args.page}.jpeg")
    log(f"    opened {title}; screenshot: {shot}")
    if args.page == 0:
        return
    state_now = switch_state(d)
    log(f"    switch: {state_now or 'not visible to dumpLayout - read the screenshot'}")
    if args.wait and state_now != "on":
        log(f"    waiting up to {args.wait}s for the user to switch it on ...")
        end = time.time() + args.wait
        while time.time() < end and state_now != "on":
            time.sleep(2)
            state_now = switch_state(d)
        log(f"    switch: {state_now or 'unknown (check the screen)'}")
        if state_now == "on":
            d.key("Back")


def switch_state(d):
    """'on'/'off' for the single Android Switch on a settings page, None if dumpLayout cannot see it."""
    try:
        switches = [n for n in d.dump() if n["type"] == "android.widget.Switch"]
    except SetupError:
        return None
    if len(switches) != 1:
        return None
    return "on" if switches[0]["checked"] in (True, "true") else "off"


def cmd_ui(args, state):
    d = device(args)
    if args.ui_cmd == "dump":
        for n in d.dump():
            if n["text"] or n["clickable"] or n["desc"]:
                log(f"{n['text']!r:40.40} {n['type']:14.14} {n['bounds']} {'click' if n['clickable'] else ''}"
                    f"{' checked=' + str(n['checked']) if re.search('Toggle|Checkbox|Switch', n['type']) else ''}"
                    f"  {n['bundle']}")
    elif args.ui_cmd == "find":
        for n in d.find(args.arg[0]):
            log(f"{n['text']!r} {n['type']} {n['bounds']}")
    elif args.ui_cmd == "tap":
        d.tap(int(args.arg[0]), int(args.arg[1]))
    elif args.ui_cmd == "tap-text":
        hits = d.find(args.arg[0])
        if not hits:
            raise SetupError(f"no element matching {args.arg[0]!r}")
        d.tap_node(hits[0])
        log(f"    tapped {hits[0]['text']!r} at {hits[0]['bounds']}")
    elif args.ui_cmd == "shot":
        dest = Path(args.arg[0]) if args.arg else state.home / "screen.jpeg"
        log(str(d.screenshot(dest)))
    elif args.ui_cmd == "key":
        d.key(args.arg[0])
    elif args.ui_cmd == "wake":
        log("override set" if d.keep_awake() else "override already active")
        log("locked" if d.screen_locked() else "unlocked")


def cmd_status(args, state):
    log(json.dumps({k: v for k, v in state.data.items()}, indent=2))
    info = keystore_info(state)
    log(f"keystore: {state.key_dir / info['file'] if info else '-'}")


def cmd_all(args, state):
    if not cmd_doctor(args, state):
        cmd_tools(args, state)
        if not cmd_doctor(args, state):
            raise SetupError("doctor reports problems - fix them first")
    cmd_tools(args, state)
    cmd_download(args, state)
    cmd_keystore(args, state)
    cmd_build(args, state)
    cmd_push(args, state)
    cmd_install(args, state)
    cmd_launch(args, state)
    log("\nDone. Next: let the user log in to Aurora (anonymous or Google) and set the permissions "
        "('permissions' step).")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--home", default=os.environ.get("AURORA_SETUP_HOME", str(Path.home() / ".aurora-setup")))
    p.add_argument("--hdc", help="path to hdc")
    p.add_argument("--target", help="device serial (hdc list targets)")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("doctor")
    sub.add_parser("tools")
    sub.add_parser("zhuoyitong")
    sub.add_parser("get-hdc")
    s = sub.add_parser("download")
    s.add_argument("--version")
    s.add_argument("--variant", choices=["hw", "standard"], default="hw")
    s = sub.add_parser("keystore")
    s.add_argument("--import", dest="import_keystore", metavar="FILE")
    s.add_argument("--alias")
    s.add_argument("--storepass")
    s.add_argument("--keypass")
    s.add_argument("--force", action="store_true")
    s = sub.add_parser("build")
    s.add_argument("--package")
    s.add_argument("--force", action="store_true")
    sub.add_parser("push")
    s = sub.add_parser("install")
    s.add_argument("--skip-open", action="store_true", help="the APK is already opened with 卓易通")
    sub.add_parser("launch")
    s = sub.add_parser("permissions")
    s.add_argument("--page", type=int)
    s.add_argument("--wait", type=int, default=0, metavar="SECONDS",
                   help="poll until the user has switched the permission on, then go back")
    s = sub.add_parser("ui")
    s.add_argument("ui_cmd", choices=["dump", "find", "tap", "tap-text", "shot", "key", "wake"])
    s.add_argument("arg", nargs="*")
    s = sub.add_parser("all")
    s.add_argument("--version")
    s.add_argument("--variant", choices=["hw", "standard"], default="hw")
    s.add_argument("--package")
    s.add_argument("--force", action="store_true")
    s.add_argument("--skip-open", action="store_true")
    sub.add_parser("status")
    args = p.parse_args()
    if args.cmd == "keystore" and args.import_keystore and not (args.alias and args.storepass):
        p.error("--import needs --alias and --storepass")
    if args.cmd == "all":
        args.import_keystore = None
    state = State(args.home)
    fn = globals()["cmd_" + args.cmd.replace("-", "_")]
    try:
        res = fn(args, state)
        if args.cmd == "doctor" and not res:
            sys.exit(2)
    except SetupError as e:
        print(f"\nERROR: {e}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)


if __name__ == "__main__":
    if os.name == "nt":
        try:
            sys.stdout.reconfigure(encoding="utf-8")
            sys.stderr.reconfigure(encoding="utf-8")
        except AttributeError:
            pass
    main()
