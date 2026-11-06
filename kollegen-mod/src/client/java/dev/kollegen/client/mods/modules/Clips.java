package dev.kollegen.client.mods.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.BooleanSetting;
import dev.kollegen.client.mods.Category;
import dev.kollegen.client.mods.KeybindSetting;
import dev.kollegen.client.mods.ModeSetting;
import dev.kollegen.client.mods.Module;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

public class Clips extends Module {
    public static Clips INSTANCE;

    public final BooleanSetting recordToggle = new BooleanSetting("Clips aufnehmen", "Startet die Hintergrund-Aufnahme für Clips.", true);
    public final BooleanSetting recordOtherAudio = new BooleanSetting("Ton anderer Programme", "Nimmt Discord, Musik und andere Apps mit auf.", true);
    public final ModeSetting alsoRecordMode = new ModeSetting("Zusätzlich aufnehmen", "Nur Minecraft oder den gesamten Bildschirm aufnehmen.", new String[]{"Nur Minecraft", "Ganzen Bildschirm"}, 0);
    public final ModeSetting duration = new ModeSetting("Clip-Länge (Puffer)", "Wie viel Spielzeit rückwirkend gespeichert wird.", new String[]{"15 Sekunden", "30 Sekunden", "60 Sekunden", "2 Minuten", "5 Minuten"}, 1);
    public final ModeSetting quality = new ModeSetting("Qualität", "Videoauflösung und Bildrate der Aufnahme.", new String[]{"1080p60", "1440p60", "720p30", "4K60"}, 0);
    public final KeybindSetting clipKey = new KeybindSetting("Clip-Taste", "Speichert den Clip auf Tastendruck (auch Kombinationen wie Strg+F8).");

    private long lastTriggerTime = 0;
    private long lastConfigCheck = 0;
    private long lastFeedbackCheck = 0;
    private boolean wasKeyDown = false;
    private boolean isSavingConfig = false;

    private String outputDir = "";
    private String alsoRecordTarget = "";
    private String alsoRecordTitle = "";
    private boolean lastRecordToggle = true;
    private boolean lastRecordOtherAudio = true;
    private int lastAlsoRecordMode = 0;
    private int lastDuration = 1;
    private int lastQuality = 0;
    private int lastKey = -1;
    private boolean lastCtrl = false;
    private boolean lastShift = false;
    private boolean lastAlt = false;

    public Clips() {
        super("clips", "Clipping", "Nimmt die letzten Spielminuten auf Tastendruck auf.", Category.MISC);
        INSTANCE = this;
        this.enabled = true;
        clipKey.value = GLFW.GLFW_KEY_F1 + 7;

        add(recordToggle);
        add(recordOtherAudio);
        add(alsoRecordMode);
        add(duration);
        add(quality);
        add(clipKey);

        loadClipsConfig();
        captureSettingStates();
    }

    private void captureSettingStates() {
        lastRecordToggle = recordToggle.value;
        lastRecordOtherAudio = recordOtherAudio.value;
        lastAlsoRecordMode = alsoRecordMode.index;
        lastDuration = duration.index;
        lastQuality = quality.index;
        lastKey = clipKey.value;
        lastCtrl = clipKey.ctrl;
        lastShift = clipKey.shift;
        lastAlt = clipKey.alt;
    }

    private boolean hasSettingsChanged() {
        return lastRecordToggle != recordToggle.value ||
                lastRecordOtherAudio != recordOtherAudio.value ||
                lastAlsoRecordMode != alsoRecordMode.index ||
                lastDuration != duration.index ||
                lastQuality != quality.index ||
                lastKey != clipKey.value ||
                lastCtrl != clipKey.ctrl ||
                lastShift != clipKey.shift ||
                lastAlt != clipKey.alt;
    }

    public void saveClipsConfig() {
        isSavingConfig = true;
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", this.enabled && recordToggle.value);
            obj.addProperty("also_record_mode", alsoRecordMode.index == 1 ? "screens" : "none");
            obj.addProperty("also_record_target", alsoRecordTarget);
            obj.addProperty("also_record_title", alsoRecordMode.index == 1 ? (alsoRecordTitle.isEmpty() ? "Screen 1 (Primary)" : alsoRecordTitle) : "None");
            obj.addProperty("record_other_audio", recordOtherAudio.value);
            obj.addProperty("hotkey", formatHotkeyString());

            int durSec = switch (duration.index) {
                case 0 -> 15;
                case 1 -> 30;
                case 2 -> 60;
                case 3 -> 120;
                case 4 -> 300;
                default -> 30;
            };
            obj.addProperty("duration_seconds", durSec);
            obj.addProperty("quality", quality.options[quality.index]);
            if (outputDir != null && !outputDir.isEmpty()) {
                obj.addProperty("output_dir", outputDir);
            }

            String json = obj.toString();
            Path[] candidates = new Path[]{
                    FabricLoader.getInstance().getConfigDir().resolve("clips_config.json"),
                    FabricLoader.getInstance().getGameDir().resolve("clips_config.json"),
                    FabricLoader.getInstance().getGameDir().getParent().getParent().resolve("clips_config.json")
            };
            for (Path p : candidates) {
                try {
                    if (p.getParent() != null) Files.createDirectories(p.getParent());
                    Files.writeString(p, json);
                } catch (Throwable ignored) {}
            }
            captureSettingStates();
        } catch (Throwable ignored) {
        } finally {
            isSavingConfig = false;
        }
    }

    public String formatHotkeyString() {
        if (clipKey.value <= 0) return "F8";
        StringBuilder sb = new StringBuilder();
        if (clipKey.ctrl) sb.append("CTRL+");
        if (clipKey.shift) sb.append("SHIFT+");
        if (clipKey.alt) sb.append("ALT+");
        sb.append(KeybindSetting.rawKeyName(clipKey.value));
        return sb.toString();
    }

    private void parseHotkey(String name) {
        if (name == null || name.isEmpty()) return;
        String[] parts = name.toUpperCase().split("\\+");
        boolean hasCtrl = false;
        boolean hasShift = false;
        boolean hasAlt = false;
        int foundKey = -1;
        for (String p : parts) {
            String s = p.trim();
            if (s.equals("CTRL") || s.equals("STRG") || s.equals("CONTROL")) hasCtrl = true;
            else if (s.equals("SHIFT") || s.equals("UMSCHALT")) hasShift = true;
            else if (s.equals("ALT")) hasAlt = true;
            else {
                int k = parseKey(s);
                if (k > 0) foundKey = k;
            }
        }
        if (foundKey > 0) {
            clipKey.value = foundKey;
            clipKey.ctrl = hasCtrl;
            clipKey.shift = hasShift;
            clipKey.alt = hasAlt;
        }
    }

    private int parseKey(String name) {
        if (name == null || name.isEmpty()) return -1;
        if (name.startsWith("F") && name.length() <= 3) {
            try {
                int fn = Integer.parseInt(name.substring(1));
                if (fn >= 1 && fn <= 25) {
                    return GLFW.GLFW_KEY_F1 + (fn - 1);
                }
            } catch (Throwable ignored) {}
        }
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
            if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        }
        return -1;
    }

    private void loadClipsConfig() {
        if (isSavingConfig) return;
        try {
            Path[] candidates = new Path[]{
                    FabricLoader.getInstance().getConfigDir().resolve("clips_config.json"),
                    FabricLoader.getInstance().getGameDir().resolve("clips_config.json"),
                    FabricLoader.getInstance().getGameDir().getParent().getParent().resolve("clips_config.json")
            };
            for (Path p : candidates) {
                if (Files.isRegularFile(p)) {
                    String json = Files.readString(p);
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    if (obj.has("enabled")) {
                        boolean en = obj.get("enabled").getAsBoolean();
                        this.enabled = en;
                        this.recordToggle.value = en;
                    }
                    if (obj.has("record_other_audio")) {
                        this.recordOtherAudio.value = obj.get("record_other_audio").getAsBoolean();
                    }
                    if (obj.has("also_record_mode")) {
                        String m = obj.get("also_record_mode").getAsString().toLowerCase();
                        this.alsoRecordMode.index = m.contains("screen") ? 1 : 0;
                    }
                    if (obj.has("duration_seconds")) {
                        int dur = obj.get("duration_seconds").getAsInt();
                        if (dur <= 15) this.duration.index = 0;
                        else if (dur <= 30) this.duration.index = 1;
                        else if (dur <= 60) this.duration.index = 2;
                        else if (dur <= 120) this.duration.index = 3;
                        else this.duration.index = 4;
                    }
                    if (obj.has("quality")) {
                        String q = obj.get("quality").getAsString();
                        for (int i = 0; i < quality.options.length; i++) {
                            if (quality.options[i].equalsIgnoreCase(q)) {
                                quality.index = i;
                                break;
                            }
                        }
                    }
                    if (obj.has("hotkey")) {
                        parseHotkey(obj.get("hotkey").getAsString().trim());
                    }
                    if (obj.has("output_dir")) {
                        this.outputDir = obj.get("output_dir").getAsString();
                    }
                    if (obj.has("also_record_target")) {
                        this.alsoRecordTarget = obj.get("also_record_target").getAsString();
                    }
                    if (obj.has("also_record_title")) {
                        this.alsoRecordTitle = obj.get("also_record_title").getAsString();
                    }
                    captureSettingStates();
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private boolean checkKeyActive(int key) {
        if (key <= 0) return false;
        try {
            if (InputConstants.isKeyDown(key)) return true;
        } catch (Throwable ignored) {}
        try {
            if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F1 + 11) {
                int sdlKey = InputConstants.KEY_F1 + (key - GLFW.GLFW_KEY_F1);
                if (InputConstants.isKeyDown(sdlKey)) return true;
            }
            if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
                int glfwKey = GLFW.GLFW_KEY_F1 + (key - InputConstants.KEY_F1);
                if (InputConstants.isKeyDown(glfwKey)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (now - lastFeedbackCheck > 250) {
            lastFeedbackCheck = now;
            checkClipDoneFeedback();
        }
        if (now - lastConfigCheck > 1500) {
            lastConfigCheck = now;
            if (hasSettingsChanged()) {
                saveClipsConfig();
            } else {
                loadClipsConfig();
            }
        }

        if (enabled && recordToggle.value && clipKey.value > 0) {
            boolean modOk = true;
            if (clipKey.ctrl && !KollegenMod.isCtrlDown()) modOk = false;
            if (clipKey.shift && !KollegenMod.isShiftDown()) modOk = false;
            if (clipKey.alt && !KollegenMod.isAltDown()) modOk = false;

            boolean down = modOk && checkKeyActive(clipKey.value);
            if (down && !wasKeyDown) {
                if (now - lastTriggerTime > 2500) {
                    lastTriggerTime = now;
                    triggerClip();
                }
            }
            wasKeyDown = down;
        }
    }

    private void checkClipDoneFeedback() {
        try {
            Path[] candidates = new Path[]{
                    FabricLoader.getInstance().getConfigDir().resolve(".kollegen-clip-done"),
                    FabricLoader.getInstance().getGameDir().resolve(".kollegen-clip-done")
            };
            for (Path p : candidates) {
                if (Files.isRegularFile(p)) {
                    Files.deleteIfExists(p);
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[Clips] §aClip erfolgreich gespeichert! ✓"));
                    }
                    if (mc != null && mc.player != null) {
                        mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onKey() {
        if (!enabled || !recordToggle.value) return;
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime > 2500) {
            lastTriggerTime = now;
            triggerClip();
        }
    }

    public void triggerClip() {
        try {
            Path[] targets = new Path[]{
                    FabricLoader.getInstance().getConfigDir().resolve(".kollegen-clip-trigger"),
                    FabricLoader.getInstance().getGameDir().resolve(".kollegen-clip-trigger")
            };
            for (Path sig : targets) {
                try {
                    Files.writeString(sig, String.valueOf(System.currentTimeMillis()));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            try {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[Clips] §eClip wird aufgenommen..."));
            } catch (Throwable ignored) {}
        }
        if (mc != null && mc.player != null) {
            try {
                mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } catch (Throwable ignored) {}
        }
    }
}
