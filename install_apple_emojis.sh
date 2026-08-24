#!/bin/bash
set -e

echo "1. Erstelle Schriftarten-Ordner..."
mkdir -p ~/.local/share/fonts
mkdir -p ~/.config/fontconfig

echo "2. Lade Apple Color Emoji herunter..."
curl -L -o ~/.local/share/fonts/AppleColorEmoji-Linux.ttf \
  https://github.com

echo "3. Erstelle fontconfig-Konfiguration..."
cat << 'FONTCONF' > ~/.config/fontconfig/fonts.conf
<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE fontconfig SYSTEM 'fonts.dtd'>
<fontconfig>
  <match target="pattern">
    <test qual="any" name="family">
      <string>Emoji</string>
    </test>
    <edit name="family" mode="assign" binding="same">
      <string>Apple Color Emoji</string>
    </edit>
  </match>
  <match target="pattern">
    <test qual="any" name="family">
      <string>Noto Color Emoji</string>
    </test>
    <edit name="family" mode="assign" binding="same">
      <string>Apple Color Emoji</string>
    </edit>
  </match>
</fontconfig>
FONTCONF

echo "4. Aktualisiere Schriftarten-Cache..."
fc-cache -f -v

echo "Fertig! Bitte starten Sie Ihren Webbrowser komplett neu."
