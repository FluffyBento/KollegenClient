#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Kollegen Client – selbst-enthaltendes AppImage mit gebündeltem WebKitGTK.
#
# Hintergrund: SteamOS/SteamDeck bringt kein system-webkit2gtk-4.1 mit (und
# kann es wegen des immutable RootFS nicht installieren). Nur ein AppImage,
# das libwebkit2gtk-4.1 + die WebKit-Tochterprozesse mitführt, läuft dort
# whitescreen-frei. Der Tauri-`appimage`-Bundler tut das nicht, daher bauen
# wir das AppDir hier selbst (linuxdeploy + gtk-Plugin + WebKit-Helpers +
# custom AppRun) und assemblen mit appimagetool.
#
# WICHTIG (v1.10.9): WebKitGTK ignoriert WEBKIT_EXEC_PATH vollständig – die
# Helfer-/Bundle-Pfade sind in libwebkit2gtk-4.1.so.0 hart kompiliert
# (/usr/lib/.../webkit2gtk-4.1). Fehlt das Systemverzeichnis, crasht die App
# überall ("Unable to spawn a new child process"). Fix: den kompilierten
# Pfad per ELF-Patch auf /tmp/klgn-webkit/ umschreiben; AppRun legt dort pro
# Start Symlinks zu den gebündelten Helfern/Bundles an.
#
# Aufruf:  scripts/build-appimage-webkit.sh <release-binary> <out.AppImage>
# ---------------------------------------------------------------------------
set -euo pipefail

BIN="${1:?usage: build-appimage-webkit.sh <binary> <out>}"
OUT="${2:?usage: build-appimage-webkit.sh <binary> <out>}"
PRODUCT="kollegen-client"
APPNAME="KollegenClient"
APPDIR="$(pwd)/AppDir"

ARCH="x86_64"
TOOLS="$(pwd)/.appimage-tools"
mkdir -p "$TOOLS"

# AppImage-Laufzeit in CI ohne FUSE: alle Tool-AppImages extrahieren statt moun­ten.
export APPIMAGE_EXTRACT_AND_RUN=1

fetch() { # $1=url $2=dest
  if [ ! -x "$2" ]; then
    echo "  -> lade $(basename "$2")"
    curl -fsSL -o "$2" "$1"
    chmod +x "$2"
  fi
}

LDAI="$TOOLS/linuxdeploy-$ARCH.AppImage"
PLUGIN="$TOOLS/linuxdeploy-plugin-gtk"
IMGTOOL="$TOOLS/appimagetool-$ARCH.AppImage"

fetch "https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-$ARCH.AppImage" "$LDAI"
# linuxdeploy-plugin-gtk ist heutzutage ein Bash-Script (kein AppImage-Build mehr).
# linuxdeploy sucht es per PATH unter 'linuxdeploy-plugin-gtk'.
curl -fsSL -o "$PLUGIN" "https://raw.githubusercontent.com/linuxdeploy/linuxdeploy-plugin-gtk/master/linuxdeploy-plugin-gtk.sh"
chmod +x "$PLUGIN"
fetch "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-$ARCH.AppImage" "$IMGTOOL"

# linuxdeploy sucht '--plugin gtk' per PATH nach linuxdeploy-plugin-gtk.
export PATH="$TOOLS:$PATH"

# WebKit-Helper-Verzeichnis auf dem Build-Host (CI: Ubuntu mit libwebkit2gtk-4.1-dev).
WEBKIT_DIR="/usr/lib/${MULTIARCH:-x86_64-linux-gnu}/webkit2gtk-4.1"
if [ ! -d "$WEBKIT_DIR" ] || [ ! -x "$WEBKIT_DIR/WebKitWebProcess" ]; then
  echo "FEHLER: WebKit-Helfer nicht gefunden unter $WEBKIT_DIR" >&2
  exit 1
fi

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/lib"

# Icon VOR linuxdeploy mit dem Namen platzieren, der zu Icon=dev.kollegen.client
# in der .desktop-Datei passt (linuxdeploy schlägt sonst beim Icon-Lookup fehl).
mkdir -p "$APPDIR/usr/share/icons/hicolor/512x512/apps"
cp icons/icon.png "$APPDIR/usr/share/icons/hicolor/512x512/apps/dev.kollegen.client.png"

echo "==> linuxdeploy: Binary + libs + WebKit-Helpers in AppDir"
"$LDAI" \
  --appdir "$APPDIR" \
  --executable "$BIN" \
  --executable "$WEBKIT_DIR/WebKitWebProcess" \
  --executable "$WEBKIT_DIR/WebKitNetworkProcess" \
  --desktop-file dev.kollegen.client.desktop \
  --icon-file icons/icon.png \
  --plugin gtk

echo "==> WebKit-Helper in WEBKIT_EXEC_PATH-Verzeichnis platzieren"
mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
for p in WebKitWebProcess WebKitNetworkProcess; do
  if [ -f "$APPDIR/usr/bin/$p" ]; then
    mv -f "$APPDIR/usr/bin/$p" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p"
  fi
done

echo "==> Zusätzliche WebKit-Bestandteile bündeln (linuxdeploy räumt sie nicht mit)"
# libwebkit2gtkinjectedbundle.so wird per dlopen geladen (keine ELF-Dependency)
# und WebKitResources enthält WebKits JS-/Daten-Dateien – beides fehlt
# sonst im AppDir und verursacht zur Laufzeit Fehler/Warnungen.
if [ -f "$WEBKIT_DIR/libwebkit2gtkinjectedbundle.so" ]; then
  cp -a "$WEBKIT_DIR/libwebkit2gtkinjectedbundle.so" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/"
else
  echo "WARNUNG: libwebkit2gtkinjectedbundle.so nicht unter $WEBKIT_DIR gefunden" >&2
fi
if [ -d "$WEBKIT_DIR/WebKitResources" ]; then
  cp -a "$WEBKIT_DIR/WebKitResources" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/"
else
  echo "WARNUNG: WebKitResources unter $WEBKIT_DIR nicht gefunden" >&2
fi

echo "==> ELF-Patch: kompilierten WebKit-Helferpfad auf /tmp/klgn-webkit/ umschreiben"
python3 - "$APPDIR" <<'PY'
import os, sys
root = sys.argv[1]
old = b"/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
new = b"/tmp/klgn-webkit/"
assert len(new) <= len(old), "Ersatzpfad länger als Original!"
targets = [
    "usr/lib/libwebkit2gtk-4.1.so.0",
    "usr/lib/libjavascriptcoregtk-4.1.so.0",
    "usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitWebProcess",
    "usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitNetworkProcess",
]
seen = 0
for rel in targets:
    p = os.path.join(root, rel)
    if not os.path.exists(p):
        continue
    data = open(p, "rb").read()
    n = data.count(old)
    if n == 0:
        continue
    data = data.replace(old, new + b"\x00" * (len(old) - len(new)))
    open(p, "wb").write(data)
    print(f"  patched {rel}: {n} Vorkommen")
    seen += n
if seen == 0:
    print("FEHLER: WebKit-Pfad nicht in den Binaries gefunden –", old.decode(),
          "– WebKit-Update geändert? Build abbrechen.", file=sys.stderr)
    sys.exit(1)
PY

echo "==> Resources (gebündelte Mod-JAR u.ä.) neben das Binary"
if [ -d resources ] && ls resources/* >/dev/null 2>&1; then
  mkdir -p "$APPDIR/usr/bin/resources"
  cp -r resources/* "$APPDIR/usr/bin/resources/"
fi

echo "==> AppRun"
cat > "$APPDIR/AppRun" <<'EORUN'
#!/usr/bin/env bash
# AppRun: AppDir-Pfade für WebKit + GTK setzen, dann Binary starten.
HERE="$(dirname "$(readlink -f "$0")")"
export LD_LIBRARY_PATH="$HERE/usr/lib:$HERE/usr/lib/x86_64-linux-gnu:$HERE/usr/lib64:$HERE/lib:$HERE/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
# WebKit-Tochterprozesse liegen im AppDir (nicht im Host, der fehlt auf SteamOS).
export WEBKIT_EXEC_PATH="$HERE/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
export WEBKIT_FRAMEWORK_DIR="$HERE/usr/lib/x86_64-linux-gnu"
# Single-Web-Process: umgeht das AppImage-Mount-Lifetime-Problem der
# WebKit-Tochterprozesse vollständig (Inhalt läuft im Mainprozess).
export WEBKIT_USE_SINGLE_WEB_PROCESS="${WEBKIT_USE_SINGLE_WEB_PROCESS:-1}"
# GTK-Laufzeit (pixbuf-Loader, GIO-Module) aus dem AppDir.
export GDK_PIXBUF_MODULE_FILE="$HERE/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders.cache"
export GIO_EXTRA_MODULES="$HERE/usr/lib/x86_64-linux-gnu/gio/modules"
export GST_PLUGIN_SYSTEM_PATH_1_0="$HERE/usr/lib/x86_64-linux-gnu/gstreamer-1.0"
# Stabiler Pfad statt flüchtigem FUSE-Mount (hilft zuverlässige Subprozesse).
export APPIMAGE_EXTRACT_AND_RUN="${APPIMAGE_EXTRACT_AND_RUN:-1}"
# WebKitGTK ignoriert WEBKIT_EXEC_PATH: die Helfer-/Bundle-Pfade sind in der
# Lib hart kompiliert und wurden beim Build per ELF-Patch auf /tmp/klgn-webkit/
# umgeschrieben. Symlinks dort pro Start anlegen (laufende Instanz nicht stören):
WEBKIT_TMP=/tmp/klgn-webkit
mkdir -p "$WEBKIT_TMP"
ensure_wk_link() {
  if [ -L "$WEBKIT_TMP/$1" ] && [ -e "$WEBKIT_TMP/$1" ]; then return; fi
  ln -sfn "$HERE/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$1" "$WEBKIT_TMP/$1"
}
for w in WebKitWebProcess WebKitNetworkProcess libwebkit2gtkinjectedbundle.so; do
  ensure_wk_link "$w"
done
if [ -d "$HERE/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitResources" ] && \
   { [ ! -e "$WEBKIT_TMP/WebKitResources" ] || [ ! -d "$WEBKIT_TMP/WebKitResources" ]; }; then
  ln -sfn "$HERE/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitResources" "$WEBKIT_TMP/WebKitResources"
fi
exec "$HERE/usr/bin/kollegen-client" "$@"
EORUN
chmod +x "$APPDIR/AppRun"

echo "==> appimagetool assemble"
ARCH="$ARCH" "$IMGTOOL" "$APPDIR" "$OUT"
ls -la "$OUT"