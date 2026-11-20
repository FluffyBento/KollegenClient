package dev.kollegen.client.menu;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DisplaySettings {

    private static final int[][] WINDOW_SIZES = {
            { 1280, 720 },
            { 1600, 900 },
            { 1920, 1080 },
            { 2560, 1440 }
    };

    private DisplaySettings() {
    }

    public static int getWindowMode() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (!mc.options.fullscreen().get()) {
            return 2;
        }
        return window.isExclusiveFullscreen() ? 0 : 1;
    }

    public static void cycleWindowMode() {
        int current = getWindowMode();
        int next = (current + 1) % 3;
        applyWindowMode(next);
    }

    public static void applyWindowMode(int mode) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (mode == 0) {
            window.setExclusiveFullscreen(true);
            mc.options.fullscreen().set(true);
            window.setFullscreen(true);
            window.changeFullscreenVideoMode();
        } else if (mode == 1) {
            window.setExclusiveFullscreen(false);
            mc.options.fullscreen().set(true);
            window.setFullscreen(true);
        } else {
            mc.options.fullscreen().set(false);
            window.setFullscreen(false);
        }
        mc.options.save();
    }

    public static String getWindowModeLabel() {
        int mode = getWindowMode();
        return switch (mode) {
            case 0 -> "Modus: Vollbild (Exklusiv)";
            case 1 -> "Modus: Randloses Fenster";
            case 2 -> "Modus: Normales Fenster";
            default -> "Modus: Unbekannt";
        };
    }

    public static int getWindowModeColor() {
        int mode = getWindowMode();
        return switch (mode) {
            case 0 -> 0xFF10B981;
            case 1 -> 0xFF38BDF8;
            case 2 -> 0xFFF59E0B;
            default -> 0xFFFFFFFF;
        };
    }

    public static List<VideoMode> getAvailableResolutions() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        Monitor monitor = window.findBestMonitor();
        if (monitor == null) return List.of();

        Map<String, VideoMode> map = new LinkedHashMap<>();
        for (VideoMode mode : monitor.videoModes()) {
            String key = mode.getWidth() + "x" + mode.getHeight();
            VideoMode existing = map.get(key);
            if (existing == null || mode.getRefreshRate() > existing.getRefreshRate()) {
                map.put(key, mode);
            }
        }
        List<VideoMode> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(b.getWidth() * b.getHeight(), a.getWidth() * a.getHeight()));
        return list;
    }

    public static void cycleResolution() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int mode = getWindowMode();
        if (mode == 2) {
            cycleWindowedSize();
            return;
        }

        Monitor monitor = window.findBestMonitor();
        if (monitor == null) return;
        List<VideoMode> resList = getAvailableResolutions();
        if (resList.isEmpty()) return;

        Optional<VideoMode> pref = window.getPreferredFullscreenVideoMode();
        int curIdx = -1;
        if (pref.isPresent()) {
            VideoMode cur = pref.get();
            for (int i = 0; i < resList.size(); i++) {
                VideoMode m = resList.get(i);
                if (m.getWidth() == cur.getWidth() && m.getHeight() == cur.getHeight()) {
                    curIdx = i;
                    break;
                }
            }
        }

        int nextIdx = curIdx + 1;
        if (nextIdx >= resList.size()) {
            window.setPreferredFullscreenVideoMode(Optional.empty());
            mc.options.fullscreenVideoModeString = "";
        } else {
            VideoMode target = resList.get(nextIdx);
            window.setPreferredFullscreenVideoMode(Optional.of(target));
            mc.options.fullscreenVideoModeString = target.write();
        }
        window.changeFullscreenVideoMode();
        mc.options.save();
    }

    public static void cycleWindowedSize() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int curW = window.getWidth();
        int curH = window.getHeight();
        int next = 0;
        for (int i = 0; i < WINDOW_SIZES.length; i++) {
            if (WINDOW_SIZES[i][0] == curW && WINDOW_SIZES[i][1] == curH) {
                next = (i + 1) % WINDOW_SIZES.length;
                break;
            }
        }
        window.setWindowed(WINDOW_SIZES[next][0], WINDOW_SIZES[next][1]);
    }

    public static String getResolutionLabel() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        int mode = getWindowMode();
        if (mode == 2) {
            return "Fenster: " + window.getWidth() + "x" + window.getHeight();
        }
        Optional<VideoMode> pref = window.getPreferredFullscreenVideoMode();
        if (pref.isEmpty()) {
            return "Auflösung: Standard (" + window.getScreenWidth() + "x" + window.getScreenHeight() + ")";
        }
        VideoMode vm = pref.get();
        return "Auflösung: " + vm.getWidth() + "x" + vm.getHeight() + " (" + Math.round(vm.getRefreshRate()) + "Hz)";
    }
}
