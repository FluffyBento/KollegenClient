package dev.kollegen.client.shader;

import dev.kollegen.client.KollegenMod;
import dev.kollegen.client.mods.modules.KollegenShader;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class KollegenShaderLoader {

    private static final KollegenShaderLoader INSTANCE = new KollegenShaderLoader();

    private String activeShaderPack = "(Deaktiviert)";
    private final List<String> cachedPacks = new ArrayList<>();

    private KollegenShaderLoader() {
    }

    public static KollegenShaderLoader getInstance() {
        return INSTANCE;
    }

    public Path getShaderpacksDir() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("shaderpacks");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Throwable ignored) {
            }
        }
        return dir;
    }

    public Path getConfigFile() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve("kollegen-shaders.properties");
    }

    public void init() {
        loadConfig();
        refreshPacks();
        if (isShaderPackActive()) {
            applyShaderPack(this.activeShaderPack, false);
        }
    }

    public synchronized List<String> refreshPacks() {
        List<String> list = new ArrayList<>();
        list.add("(Deaktiviert)");
        list.add("Kollegen Shader (Integriert)");
        Path dir = getShaderpacksDir();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    String name = p.getFileName().toString();
                    if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".zip")) {
                        list.add(name);
                    } else if (Files.isDirectory(p) && (Files.isDirectory(p.resolve("shaders")) || Files.isRegularFile(p.resolve("shaders.properties")))) {
                        list.add(name);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        List<String> sorted = new ArrayList<>(list.subList(2, list.size()));
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        list.subList(2, list.size()).clear();
        list.addAll(sorted);

        this.cachedPacks.clear();
        this.cachedPacks.addAll(list);
        return this.cachedPacks;
    }

    public synchronized List<String> getShaderPacks() {
        if (this.cachedPacks.isEmpty()) {
            return refreshPacks();
        }
        return this.cachedPacks;
    }

    public String getActiveShaderPack() {
        return this.activeShaderPack != null ? this.activeShaderPack : "(Deaktiviert)";
    }

    public boolean isShaderPackActive() {
        String p = getActiveShaderPack();
        return !p.equalsIgnoreCase("(Deaktiviert)") && !p.toLowerCase().contains("deaktiviert");
    }

    public void setShaderPack(String packName) {
        if (packName == null || packName.trim().isEmpty()) {
            packName = "(Deaktiviert)";
        }
        this.activeShaderPack = packName.trim();
        applyShaderPack(this.activeShaderPack, true);
        saveConfig();
        KollegenMod.LOGGER.info("Kollegen: Shaderpack aktiviert -> {}", this.activeShaderPack);

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String msg = this.activeShaderPack.contains("Deaktiviert") ? "§7Shader deaktiviert" : "§aShader geladen: §f" + this.activeShaderPack;
                mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyShaderPack(String packName, boolean reload) {
        if (packName == null || packName.equalsIgnoreCase("(Deaktiviert)") || packName.toLowerCase().contains("deaktiviert")) {
            this.activeShaderPack = "(Deaktiviert)";
            if (KollegenShader.INSTANCE != null) {
                KollegenShader.INSTANCE.enabled = false;
                KollegenShader.INSTANCE.onDisable();
            }
            return;
        }

        this.activeShaderPack = packName;
        if (KollegenShader.INSTANCE == null) {
            return;
        }

        KollegenShader.INSTANCE.enabled = true;
        KollegenShader.INSTANCE.onEnable();
        String lower = packName.toLowerCase();

        if (lower.contains("kollegen") || lower.contains("integriert")) {
            KollegenShader.INSTANCE.preset.index = 0;
            KollegenShader.INSTANCE.applyPreset(0);
        } else if (lower.contains("bsl")) {
            KollegenShader.INSTANCE.preset.index = 1;
            KollegenShader.INSTANCE.applyPreset(1);
        } else if (lower.contains("reimagined")) {
            KollegenShader.INSTANCE.preset.index = 2;
            KollegenShader.INSTANCE.applyPreset(2);
        } else if (lower.contains("unbound")) {
            KollegenShader.INSTANCE.preset.index = 3;
            KollegenShader.INSTANCE.applyPreset(3);
        } else if (lower.contains("bliss")) {
            KollegenShader.INSTANCE.preset.index = 4;
            KollegenShader.INSTANCE.applyPreset(4);
        } else if (lower.contains("vibrant") || lower.contains("sildur")) {
            KollegenShader.INSTANCE.preset.index = 5;
            KollegenShader.INSTANCE.applyPreset(5);
        } else if (lower.contains("cinema") || lower.contains("nostalgia") || lower.contains("kappa")) {
            KollegenShader.INSTANCE.preset.index = 6;
            KollegenShader.INSTANCE.applyPreset(6);
        } else if (lower.contains("warm") || lower.contains("sun")) {
            KollegenShader.INSTANCE.preset.index = 7;
            KollegenShader.INSTANCE.applyPreset(7);
        } else {
            KollegenShader.INSTANCE.preset.index = 1;
            KollegenShader.INSTANCE.applyPreset(1);
        }

        inspectPackProperties(packName);

        if (reload) {
            triggerReload();
        }
    }

    private void inspectPackProperties(String packName) {
        Path packFile = getShaderpacksDir().resolve(packName);
        if (Files.exists(packFile)) {
            try {
                if (Files.isRegularFile(packFile) && packName.toLowerCase().endsWith(".zip")) {
                    try (ZipFile zip = new ZipFile(packFile.toFile())) {
                        ZipEntry entry = zip.getEntry("shaders/shaders.properties");
                        if (entry == null) {
                            entry = zip.getEntry("shaders.properties");
                        }
                        if (entry != null) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                parsePropertiesStream(in);
                            }
                        }
                    }
                } else if (Files.isDirectory(packFile)) {
                    Path propPath = packFile.resolve("shaders").resolve("shaders.properties");
                    if (!Files.isRegularFile(propPath)) {
                        propPath = packFile.resolve("shaders.properties");
                    }
                    if (Files.isRegularFile(propPath)) {
                        try (InputStream in = Files.newInputStream(propPath)) {
                            parsePropertiesStream(in);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Path sideTxt = getShaderpacksDir().resolve(packName + ".txt");
        if (Files.isRegularFile(sideTxt)) {
            try (InputStream in = Files.newInputStream(sideTxt)) {
                parsePropertiesStream(in);
            } catch (Throwable ignored) {
            }
        }
    }

    private void parsePropertiesStream(InputStream in) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String k = line.substring(0, eq).trim().toUpperCase();
                String v = line.substring(eq + 1).trim();

                if (KollegenShader.INSTANCE != null) {
                    if (k.contains("BLOOM")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v) && !"-1".equals(v);
                        KollegenShader.INSTANCE.bloomEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.bloom.value = Math.max(0.1, Math.min(1.0, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("MOTION_BLUR")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v) || "2".equals(v);
                        KollegenShader.INSTANCE.motionBlurEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.motionBlurStrength.value = Math.max(0.05, Math.min(0.95, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("VIGNETTE")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v);
                        KollegenShader.INSTANCE.vignetteEnabled.value = b;
                        try {
                            double d = Double.parseDouble(v);
                            if (d > 0.0) {
                                KollegenShader.INSTANCE.vignetteStrength.value = Math.max(0.05, Math.min(1.0, d));
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("SATURATION") || k.contains("COLOR_SATURATION")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.saturationEnabled.value = true;
                            KollegenShader.INSTANCE.saturation.value = Math.max(0.5, Math.min(3.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("VIBRANCE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.vibranceEnabled.value = true;
                            KollegenShader.INSTANCE.vibrance.value = Math.max(0.5, Math.min(2.5, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("CONTRAST")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.contrastEnabled.value = true;
                            KollegenShader.INSTANCE.contrast.value = Math.max(0.5, Math.min(2.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.equals("EXPOSURE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.exposureEnabled.value = true;
                            KollegenShader.INSTANCE.exposure.value = Math.max(0.5, Math.min(2.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("LIGHT_SHAFT") || k.contains("GODRAY") || k.contains("VOLUMETRIC_LIGHT")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v);
                        if (b) {
                            try {
                                double d = Double.parseDouble(v);
                                KollegenShader.INSTANCE.godRays.value = Math.max(0.1, Math.min(1.0, d));
                            } catch (NumberFormatException ignored) {
                                KollegenShader.INSTANCE.godRays.value = 0.65;
                            }
                        } else {
                            KollegenShader.INSTANCE.godRays.value = 0.0;
                        }
                    } else if (k.contains("AMBIENT_OCCLUSION") || k.contains("SSAO") || k.equals("AO")) {
                        boolean b = !"false".equalsIgnoreCase(v) && !"0".equals(v);
                        if (b) {
                            try {
                                double d = Double.parseDouble(v);
                                KollegenShader.INSTANCE.ssao.value = Math.max(0.1, Math.min(1.0, d));
                            } catch (NumberFormatException ignored) {
                                KollegenShader.INSTANCE.ssao.value = 0.50;
                            }
                        } else {
                            KollegenShader.INSTANCE.ssao.value = 0.0;
                        }
                    } else if (k.contains("FOG") || k.contains("ATMOSPHERE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.depthFog.value = Math.max(0.05, Math.min(1.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (k.contains("WATER") || k.contains("WAVE")) {
                        try {
                            double d = Double.parseDouble(v);
                            KollegenShader.INSTANCE.waterShimmer.value = Math.max(0.05, Math.min(1.0, d));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void triggerReload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                if (KollegenShader.isShaderActive()) {
                    KollegenShader.INSTANCE.onEnable();
                }
                if (mc.getShaderManager() != null) {
                    net.minecraft.client.renderer.PostChain chain = mc.getShaderManager().getPostChain(KollegenShader.SHADER_ID, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
                    if (chain != null) {
                        KollegenShader.updateShaderUniforms(chain);
                    }
                    if (KollegenShader.isMotionBlurActive()) {
                        net.minecraft.client.renderer.PostChain blurChain = mc.getShaderManager().getPostChain(KollegenShader.BLUR_ID, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
                        if (blurChain != null) {
                            KollegenShader.updateBlurUniforms(blurChain);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public void loadConfig() {
        Path path = getConfigFile();
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(in);
            String p = props.getProperty("active_shaderpack", "(Deaktiviert)");
            if (p != null && !p.trim().isEmpty()) {
                this.activeShaderPack = p.trim();
            }
        } catch (Throwable ignored) {
        }
    }

    public void saveConfig() {
        Path path = getConfigFile();
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Properties props = new Properties();
            props.setProperty("active_shaderpack", getActiveShaderPack());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, null);
            }
        } catch (Throwable ignored) {
        }
    }
}
