#!/usr/bin/env bash
set -euo pipefail

BIN="${1:?usage: build-appimage-webkit.sh <binary> <out>}"
OUT="${2:?usage: build-appimage-webkit.sh <binary> <out>}"
PRODUCT="kollegen-client"
APPNAME="KollegenClient"
APPDIR="$(pwd)/AppDir"

ARCH="x86_64"
TOOLS="$(pwd)/.appimage-tools"
mkdir -p "$TOOLS"

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
curl -fsSL -o "$PLUGIN" "https://raw.githubusercontent.com/linuxdeploy/linuxdeploy-plugin-gtk/master/linuxdeploy-plugin-gtk.sh"
chmod +x "$PLUGIN"
fetch "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-$ARCH.AppImage" "$IMGTOOL"

export PATH="$TOOLS:$PATH"

WEBKIT_DIR="/usr/lib/${MULTIARCH:-x86_64-linux-gnu}/webkit2gtk-4.1"
if [ ! -d "$WEBKIT_DIR" ] || [ ! -x "$WEBKIT_DIR/WebKitWebProcess" ]; then
  echo "FEHLER: WebKit-Helfer nicht gefunden unter $WEBKIT_DIR" >&2
  exit 1
fi

INJECTED=""
for cand in "$WEBKIT_DIR/injected-bundle/libwebkit2gtkinjectedbundle.so" "$WEBKIT_DIR/libwebkit2gtkinjectedbundle.so"; do
  if [ -f "$cand" ]; then
    INJECTED="$cand"
    break
  fi
done
if [ -z "$INJECTED" ]; then
  echo "FEHLER: libwebkit2gtkinjectedbundle.so nicht gefunden (weder unter" >&2
  echo "  $WEBKIT_DIR/injected-bundle/ noch unter $WEBKIT_DIR)" >&2
  exit 1
fi
echo "==> Injected bundle gefunden: $INJECTED"

rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin" "$APPDIR/usr/lib"

mkdir -p "$APPDIR/usr/share/icons/hicolor/512x512/apps"
cp icons/icon.png "$APPDIR/usr/share/icons/hicolor/512x512/apps/dev.kollegen.client.png"

echo "==> linuxdeploy: Binary + libs in AppDir"
"$LDAI" \
  --appdir "$APPDIR" \
  --executable "$BIN" \
  --desktop-file dev.kollegen.client.desktop \
  --icon-file icons/icon.png \
  --plugin gtk

echo "==> DEBUG: Dateien nach linuxdeploy:"
find "$APPDIR" -name "*.so*" -o -name "WebKit*Process*" 2>/dev/null | head -20 || true

echo "==> WebKit-Helfer manuell kopieren"
mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
for p in WebKitWebProcess WebKitNetworkProcess; do
  if [ -f "$WEBKIT_DIR/$p" ]; then
    cp -f "$WEBKIT_DIR/$p" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p.real"
    echo "  kopiert: $p"
  else
    echo "FEHLER: $p nicht gefunden in $WEBKIT_DIR"
    exit 1
  fi
done

# Auch die WebKit-Bibliotheken kopieren
echo "==> WebKit-Bibliotheken kopieren"
for lib in libwebkit2gtk-4.1.so.0 libjavascriptcoregtk-4.1.so.0; do
  if [ -f "$WEBKIT_DIR/../$lib" ]; then
    cp -f "$WEBKIT_DIR/../$lib" "$APPDIR/usr/lib/"
    echo "  kopiert: $lib"
  elif [ -f "/usr/lib/x86_64-linux-gnu/$lib" ]; then
    cp -f "/usr/lib/x86_64-linux-gnu/$lib" "$APPDIR/usr/lib/"
    echo "  kopiert: $lib (aus /usr/lib)"
  else
    echo "WARNUNG: $lib nicht gefunden"
  fi
done

echo "==> WebKit-Helfer in WEBKIT_EXEC_PATH-Verzeichnis platzieren"
mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
for p in WebKitWebProcess WebKitNetworkProcess; do
  if [ -f "$APPDIR/usr/bin/$p" ]; then
    mv -f "$APPDIR/usr/bin/$p" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p.real"
  elif [ -f "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p.real" ]; then
    echo "  bereits vorhanden: $p.real"
  else
    echo "FEHLER: $p nicht gefunden in AppDir"
    exit 1
  fi
done

echo "==> ELF-Patch: Hardcoded WebKit-Pfad auf \$ORIGIN umschreiben"
python3 - "$APPDIR" <<'PY'
import os, sys
root = sys.argv[1]
old = b"/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"
new = b"$ORIGIN"
targets = [
    "usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitWebProcess.real",
    "usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/WebKitNetworkProcess.real",
    "usr/lib/libwebkit2gtk-4.1.so.0",
    "usr/lib/libjavascriptcoregtk-4.1.so.0",
]
seen = 0
for rel in targets:
    p = os.path.join(root, rel)
    if not os.path.exists(p):
        print(f"  FEHLER: Datei nicht gefunden: {p}", file=sys.stderr)
        continue
    data = open(p, "rb").read()
    n = data.count(old)
    if n == 0:
        print(f"  INFO: Kein Pfad gefunden in {rel}", file=sys.stderr)
        continue
    # Replace with $ORIGIN + null padding
    replacement = new + b"\x00" * (len(old) - len(new))
    data = data.replace(old, replacement)
    open(p, "wb").write(data)
    print(f"  gepatcht {rel}: {n} Vorkommen")
    seen += n
if seen == 0:
    print("WARNUNG: WebKit-Pfad nicht in den Binaries gefunden", file=sys.stderr)
PY

echo "==> WebKit-Helper Wrapper erstellen (setzen LD_LIBRARY_PATH + WEBKIT_EXEC_PATH)"
for p in WebKitWebProcess WebKitNetworkProcess; do
  cat > "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
export WEBKIT_EXEC_PATH="\$HERE"
export LD_LIBRARY_PATH="\$HERE/../..:\$HERE/../../..:\$HERE/../../../..:\$HERE/../../../../lib:\$HERE/../../../../lib/x86_64-linux-gnu\${LD_LIBRARY_PATH:+:\$LD_LIBRARY_PATH}"
exec "\$HERE/$p.real" "\$@"
EOF
  chmod +x "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/$p"
done

echo "==> Zusätzliche WebKit-Bestandteile bündeln (linuxdeploy räumt sie nicht mit)"
if [ -f "$INJECTED" ]; then
  mkdir -p "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/injected-bundle"
  cp -a "$INJECTED" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/injected-bundle/"
  echo "  -> Injected-Bundle gebündelt"
else
  echo "FEHLER: libwebkit2gtkinjectedbundle.so fehlt" >&2
  exit 1
fi
if [ -f "$WEBKIT_DIR/libwebkit2gtkinjectedbundle.so" ] && \
   [ ! -f "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/injected-bundle/libwebkit2gtkinjectedbundle.so" ]; then
  cp -a "$WEBKIT_DIR/libwebkit2gtkinjectedbundle.so" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/injected-bundle/"
fi
if [ -d "$WEBKIT_DIR/WebKitResources" ]; then
  cp -a "$WEBKIT_DIR/WebKitResources" "$APPDIR/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1/"
else
  echo "WARNUNG: WebKitResources unter $WEBKIT_DIR nicht gefunden" >&2
fi

echo "==> Resources (gebündelte Mod-JAR u.ä.) neben das Binary"
if [ -d resources ] && ls resources/* >/dev/null 2>&1; then
  mkdir -p "$APPDIR/usr/bin/resources"
  cp -r resources/* "$APPDIR/usr/bin/resources/"
fi

echo "==> AppRun"
cat > "$APPDIR/AppRun" <<'EORUN'
HERE="$(dirname "$(readlink -f "$0")")"

# Set up WebKit exec path to our bundled directory
export WEBKIT_EXEC_PATH="$HERE/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"

export LD_LIBRARY_PATH="$HERE/usr/lib:$HERE/usr/lib/x86_64-linux-gnu:$HERE/usr/lib64:$HERE/lib:$HERE/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export WEBKIT_FRAMEWORK_DIR="$HERE/usr/lib/x86_64-linux-gnu"
export WEBKIT_USE_SINGLE_WEB_PROCESS="${WEBKIT_USE_SINGLE_WEB_PROCESS:-1}"
export GDK_PIXBUF_MODULE_FILE="$HERE/usr/lib/x86_64-linux-gnu/gdk-pixbuf-2.0/2.10.0/loaders.cache"
export GIO_EXTRA_MODULES="$HERE/usr/lib/x86_64-linux-gnu/gio/modules"
export GST_PLUGIN_SYSTEM_PATH_1_0="$HERE/usr/lib/x86_64-linux-gnu/gstreamer-1.0"
export APPIMAGE_EXTRACT_AND_RUN="${APPIMAGE_EXTRACT_AND_RUN:-1}"
exec "$HERE/usr/bin/kollegen-client" "$@"
EORUN
chmod +x "$APPDIR/AppRun"

echo "==> appimagetool assemble"
ARCH="$ARCH" "$IMGTOOL" "$APPDIR" "$OUT"
ls -la "$OUT"