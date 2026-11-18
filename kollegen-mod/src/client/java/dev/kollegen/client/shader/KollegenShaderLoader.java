package dev.kollegen.client.shader;

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
        List<String> sorted = new ArrayList<>(list.subList(1, list.size()));
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        list.subList(1, list.size()).clear();
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
        String lower = packName.toLowerCase();

        if (lower.contains("bsl")) {
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
        if (!Files.exists(packFile)) return;

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
                    } else if (k.contains("MOTION_BLUR")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v) || "2".equals(v);
                        KollegenShader.INSTANCE.motionBlurEnabled.value = b;
                    } else if (k.contains("VIGNETTE")) {
                        boolean b = "true".equalsIgnoreCase(v) || "1".equals(v);
                        KollegenShader.INSTANCE.vignetteEnabled.value = b;
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
                if (mc.getShaderManager() != null) {
                    net.minecraft.client.renderer.PostChain chain = mc.getShaderManager().getPostChain(KollegenShader.SHADER_ID, net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
                    if (chain != null) {
                        KollegenShader.updateShaderUniforms(chain);
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
