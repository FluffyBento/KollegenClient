package dev.kollegen.client.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class EmoteManager {

    private static final List<Emote> ALL_EMOTES = new ArrayList<>();
    private static final Map<String, Emote> BY_ID = new LinkedHashMap<>();
    private static final Set<String> UNLOCKED = new HashSet<>();
    private static final String[] WHEEL = new String[8];

    private static Emote activeBaseEmote = null;
    private static long baseEmoteStartTime = 0;

    private static Emote activeActionEmote = null;
    private static long actionEmoteStartTime = 0;

    private static CameraType previousCameraType = null;
    public static boolean autoThirdPerson = true;

    static {
        register(new Emote("wave", "Winken", "Hebt die Hand und winkt freundlich.", EmoteType.ONE_TIME, true, 0, 2000, "👋"));
        register(new Emote("dab", "Dab", "Blitzschneller, cooler Dab.", EmoteType.ONE_TIME, true, 0, 2000, "😎"));
        register(new Emote("clap", "Klatschen", "Klatscht begeistert Beifall.", EmoteType.ONE_TIME, true, 0, 2200, "👏"));
        register(new Emote("bow", "Verbeugung", "Edle, höfliche Verbeugung.", EmoteType.ONE_TIME, true, 0, 2500, "🙇"));
        register(new Emote("point", "Zeigen", "Zeigt bestimmt nach vorne.", EmoteType.ONE_TIME, true, 0, 2000, "👉"));
        register(new Emote("facepalm", "Facepalm", "Schlägt die Hand vor den Kopf.", EmoteType.ONE_TIME, true, 0, 2200, "🤦"));
        register(new Emote("sit", "Sitzen", "Setzt sich entspannt auf den Boden.", EmoteType.IDLE, true, 0, 0, "🪑"));
        register(new Emote("liedown", "Hinlegen", "Legt sich flach auf den Boden.", EmoteType.IDLE, true, 0, 0, "🛏"));
        register(new Emote("squat", "Kniebeugen", "Sportliche Kniebeugen.", EmoteType.CONTINUOUS, true, 0, 0, "🏋"));
        register(new Emote("tpose", "T-Pose", "Dominante, unerschütterliche T-Pose.", EmoteType.CONTINUOUS, true, 0, 0, "🧍"));

        register(new Emote("floss", "Floss Dance", "Legendärer schneller Floss-Tanz.", EmoteType.CONTINUOUS, false, 800, 0, "🕺"));
        register(new Emote("meditate", "Meditieren", "Schwebt im Schneidersitz frei in der Luft.", EmoteType.CONTINUOUS, false, 1200, 0, "🧘"));
        register(new Emote("takethel", "Take the L", "Klassischer Siegestanz mit L an der Stirn.", EmoteType.CONTINUOUS, false, 1000, 0, "🏆"));
        register(new Emote("guitar", "Luftgitarre", "Rockt auf der unsichtbaren E-Gitarre.", EmoteType.CONTINUOUS, false, 900, 0, "🎸"));
        register(new Emote("breakdance", "Breakdance", "Akrobatische Bodendrehungen.", EmoteType.CONTINUOUS, false, 1500, 0, "🤸"));
        register(new Emote("backflip", "Salto", "Spektakulärer 360° Rückwärtssalto.", EmoteType.ONE_TIME, false, 1100, 1500, "🌀"));
        register(new Emote("jumpingjacks", "Hampelmann", "Rhythmischer Hampelmann-Sprung.", EmoteType.CONTINUOUS, false, 750, 0, "⭐"));
        register(new Emote("robot", "Roboter", "Mechanische, präzise Roboter-Moves.", EmoteType.CONTINUOUS, false, 950, 0, "🤖"));

        String[] defWheel = new String[]{"wave", "dab", "sit", "floss", "meditate", "squat", "clap", "bow"};
        System.arraycopy(defWheel, 0, WHEEL, 0, 8);

        for (Emote e : ALL_EMOTES) {
            if (e.isDefault) {
                UNLOCKED.add(e.id);
            }
        }

        load();
    }

    private static void register(Emote e) {
        ALL_EMOTES.add(e);
        BY_ID.put(e.id, e);
    }

    public static List<Emote> getAllEmotes() {
        return Collections.unmodifiableList(ALL_EMOTES);
    }

    public static Emote getById(String id) {
        return BY_ID.get(id);
    }

    public static boolean isUnlocked(String id) {
        Emote e = BY_ID.get(id);
        if (e != null && e.isDefault) return true;
        return UNLOCKED.contains(id);
    }

    public static void unlock(String id) {
        UNLOCKED.add(id);
        save();
    }

    public static String[] getWheel() {
        return WHEEL.clone();
    }

    public static void setWheelSlot(int index, String emoteId) {
        if (index >= 0 && index < WHEEL.length) {
            WHEEL[index] = emoteId;
            save();
        }
    }

    public static Emote getActiveBaseEmote() {
        return activeBaseEmote;
    }

    public static Emote getActiveActionEmote() {
        return activeActionEmote;
    }

    public static boolean isEmoting() {
        return activeBaseEmote != null || activeActionEmote != null;
    }

    public static void playEmote(Emote emote) {
        if (emote == null) return;
        Minecraft mc = Minecraft.getInstance();

        if (emote.type == EmoteType.ONE_TIME) {
            activeActionEmote = emote;
            actionEmoteStartTime = System.currentTimeMillis();
            ensureThirdPerson();
            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.2f);
            return;
        }

        if (activeBaseEmote == emote) {
            stopBaseEmote();
            playUiSound(SoundEvents.UI_BUTTON_CLICK, 0.8f);
            return;
        }

        activeBaseEmote = emote;
        baseEmoteStartTime = System.currentTimeMillis();
        ensureThirdPerson();
        playUiSound(SoundEvents.PLAYER_LEVELUP, 1.0f);
    }

    public static void stopBaseEmote() {
        activeBaseEmote = null;
        baseEmoteStartTime = 0;
        checkRestoreCamera();
    }

    public static void stopActionEmote() {
        activeActionEmote = null;
        actionEmoteStartTime = 0;
        checkRestoreCamera();
    }

    public static void stopAllEmotes() {
        activeBaseEmote = null;
        baseEmoteStartTime = 0;
        activeActionEmote = null;
        actionEmoteStartTime = 0;
        checkRestoreCamera();
        playUiSound(SoundEvents.UI_BUTTON_CLICK, 0.7f);
    }

    public static void onPlayerMove() {
        if (activeBaseEmote != null && activeBaseEmote.type == EmoteType.IDLE && (System.currentTimeMillis() - baseEmoteStartTime > 600)) {
            stopBaseEmote();
        }
    }

    public static void tick() {
        if (activeActionEmote != null) {
            long elapsed = System.currentTimeMillis() - actionEmoteStartTime;
            if (elapsed >= activeActionEmote.durationMs) {
                stopActionEmote();
            }
        }
    }

    private static void ensureThirdPerson() {
        if (!autoThirdPerson) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            previousCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    private static void checkRestoreCamera() {
        if (!isEmoting() && previousCameraType != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null) {
                mc.options.setCameraType(previousCameraType);
            }
            previousCameraType = null;
        }
    }

    private static void playUiSound(net.minecraft.sounds.SoundEvent se, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
        }
    }

    private static void playUiSound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> se, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
        }
    }

    public static void applyAnimation(PlayerModel model, AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (state.id != mc.player.getId()) return;

        tick();

        float tBase = (System.currentTimeMillis() - baseEmoteStartTime) / 1000.0f;
        float tAction = (System.currentTimeMillis() - actionEmoteStartTime) / 1000.0f;

        if (activeBaseEmote != null) {
            activeBaseEmote.animate(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, tBase);
        }

        if (activeActionEmote != null) {
            activeActionEmote.animate(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg, tAction);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("kollegen-emotes.json");
    }

    public static void load() {
        try {
            Path p = configFile();
            if (!Files.exists(p)) return;
            String text = Files.readString(p);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();

            if (root.has("auto_third_person")) {
                autoThirdPerson = root.get("auto_third_person").getAsBoolean();
            }

            if (root.has("unlocked") && root.get("unlocked").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("unlocked")) {
                    UNLOCKED.add(el.getAsString());
                }
            }

            if (root.has("wheel") && root.get("wheel").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("wheel");
                for (int i = 0; i < Math.min(arr.size(), WHEEL.length); i++) {
                    String id = arr.get(i).getAsString();
                    if (BY_ID.containsKey(id)) {
                        WHEEL[i] = id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            Path p = configFile();
            JsonObject root = new JsonObject();
            root.addProperty("auto_third_person", autoThirdPerson);

            JsonArray unl = new JsonArray();
            for (String u : UNLOCKED) {
                unl.add(u);
            }
            root.add("unlocked", unl);

            JsonArray wh = new JsonArray();
            for (String s : WHEEL) {
                wh.add(s != null ? s : "");
            }
            root.add("wheel", wh);

            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, root.toString());
        } catch (Throwable ignored) {
        }
    }
}
