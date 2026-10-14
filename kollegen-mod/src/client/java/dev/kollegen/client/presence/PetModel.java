package dev.kollegen.client.presence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PetModel {
    private static final Map<String, PetModel> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();

    public float texWidth = 64.0F;
    public float texHeight = 64.0F;
    public final List<Bone> roots = new ArrayList<>();
    public final Map<String, Bone> byName = new HashMap<>();
    public final Map<String, PetAnim> anims = new HashMap<>();
    public final Behavior behavior = new Behavior();
    public float minX;
    public float minY;
    public float minZ;
    public float maxX;
    public float maxY;
    public float maxZ;

    private PetModel() {
    }

    public static final class Bone {
        public String name = "";
        public float px;
        public float py;
        public float pz;
        public float rx;
        public float ry;
        public float rz;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();
    }

    public static final class Cube {
        public float minX;
        public float minY;
        public float maxX;
        public float maxY;
        public float minZ;
        public float maxZ;
        public final List<BbModel.Face> faces = new ArrayList<>();
    }

    public static final class PetAnim {
        public float length;
        public final Map<String, BoneChans> bones = new HashMap<>();
    }

    public static final class BoneChans {
        public JsonElement rx;
        public JsonElement ry;
        public JsonElement rz;
        public JsonElement px;
        public JsonElement py;
        public JsonElement pz;
        public JsonElement sx;
        public JsonElement sy;
        public JsonElement sz;
    }

    public static final class Behavior {
        public String mode = "hover";
        public double hoverHeight = 1.6;
        public double hoverAmp = 0.1;
        public double hoverSpeed = 1.0;
        public double followStart = 2.2;
        public double followStop = 1.4;
        public double followSpeed = 4.5;
        public double teleportDistance = 16.0;
        public String idleAnim = "idle";
        public String moveAnim = "move";
        public String sitAnim = "sit";
        public double sitAfter = 6.0;
        public double sprintMult = 1.5;
        public boolean sneakSit = true;
        public boolean lookAtPlayer = true;
        public final List<Particle> particles = new ArrayList<>();
    }

    public static final class Particle {
        public String type = "";
        public double rate;
    }

    public static PetModel load(ResourceManager manager, Identifier modelId) {
        if (manager == null || modelId == null) return null;
        String key = modelId.toString();
        PetModel cached = CACHE.get(key);
        if (cached != null) return cached;
        if (MISSING.containsKey(key)) return null;
        try {
            net.minecraft.server.packs.resources.Resource res = manager.getResource(modelId).orElse(null);
            if (res == null) {
                MISSING.put(key, Boolean.TRUE);
                return null;
            }
            try (InputStream in = res.open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root == null || !root.isJsonObject()) {
                    MISSING.put(key, Boolean.TRUE);
                    return null;
                }
                PetModel model = parse(root.getAsJsonObject());
                if (model == null) {
                    MISSING.put(key, Boolean.TRUE);
                    return null;
                }
                CACHE.put(key, model);
                return model;
            }
        } catch (Throwable ignored) {
            MISSING.put(key, Boolean.TRUE);
            return null;
        }
    }

    public static PetModel parse(JsonObject root) {
        if (root == null) return null;
        PetModel model = new PetModel();
        try {
            if (root.has("texture") && root.get("texture").isJsonObject()) {
                JsonObject t = root.getAsJsonObject("texture");
                try {
                    if (t.has("width")) model.texWidth = t.get("width").getAsFloat();
                    if (t.has("height")) model.texHeight = t.get("height").getAsFloat();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        if (model.texWidth <= 0.0F) model.texWidth = 64.0F;
        if (model.texHeight <= 0.0F) model.texHeight = 64.0F;
        Map<String, JsonObject> rawBones = new HashMap<>();
        try {
            if (root.has("bones") && root.get("bones").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("bones")) {
                    if (el == null || !el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    String name = "";
                    try {
                        name = o.get("name").getAsString();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (name == null || name.isEmpty() || rawBones.containsKey(name)) continue;
                    rawBones.put(name, o);
                }
            }
        } catch (Throwable ignored) {
        }
        if (rawBones.isEmpty()) return null;
        Map<String, Bone> built = new HashMap<>();
        for (Map.Entry<String, JsonObject> e : rawBones.entrySet()) {
            Bone bone = readBone(e.getValue(), model.texWidth, model.texHeight);
            if (bone != null) built.put(e.getKey(), bone);
        }
        for (Map.Entry<String, Bone> e : built.entrySet()) {
            JsonObject raw = rawBones.get(e.getKey());
            String parent = null;
            try {
                if (raw.has("parent") && !raw.get("parent").isJsonNull()) parent = raw.get("parent").getAsString();
            } catch (Throwable ignored) {
            }
            Bone parentBone = parent != null ? built.get(parent) : null;
            if (parentBone != null) {
                parentBone.children.add(e.getValue());
            } else {
                model.roots.add(e.getValue());
            }
            model.byName.put(e.getKey(), e.getValue());
        }
        java.util.Set<String> visited = new java.util.HashSet<>();
        for (Bone rootBone : new ArrayList<>(model.roots)) {
            absolutize(rootBone, 0.0F, 0.0F, 0.0F, visited);
        }
        for (Map.Entry<String, Bone> e : built.entrySet()) {
            if (!visited.contains(e.getKey())) {
                model.roots.add(e.getValue());
                absolutize(e.getValue(), 0.0F, 0.0F, 0.0F, visited);
            }
        }
        for (Bone b : built.values()) {
            for (Cube c : b.cubes) {
                c.minX += b.px;
                c.maxX += b.px;
                c.minY += b.py;
                c.maxY += b.py;
                c.minZ += b.pz;
                c.maxZ += b.pz;
            }
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        boolean any = false;
        for (Bone b : model.byName.values()) {
            for (Cube c : b.cubes) {
                any = true;
                if (c.minX < minX) minX = c.minX;
                if (c.minY < minY) minY = c.minY;
                if (c.minZ < minZ) minZ = c.minZ;
                if (c.maxX > maxX) maxX = c.maxX;
                if (c.maxY > maxY) maxY = c.maxY;
                if (c.maxZ > maxZ) maxZ = c.maxZ;
            }
        }
        if (any) {
            model.minX = minX;
            model.minY = minY;
            model.minZ = minZ;
            model.maxX = maxX;
            model.maxY = maxY;
            model.maxZ = maxZ;
        }
        readAnimations(root, model);
        readBehavior(root, model);
        return model;
    }

    private static void absolutize(Bone bone, float ax, float ay, float az, java.util.Set<String> visited) {
        if (bone == null || !visited.add(bone.name)) return;
        bone.px += ax;
        bone.py += ay;
        bone.pz += az;
        for (Bone child : bone.children) {
            absolutize(child, bone.px, bone.py, bone.pz, visited);
        }
    }

    private static Bone readBone(JsonObject o, float texW, float texH) {
        Bone bone = new Bone();
        try {
            bone.name = o.get("name").getAsString();
        } catch (Throwable ignored) {
            return null;
        }
        float[] pivot = readFloat3(o, "pivot");
        bone.px = pivot[0] / 16.0F;
        bone.py = pivot[1] / 16.0F;
        bone.pz = pivot[2] / 16.0F;
        float[] rot = readFloat3(o, "rotation");
        bone.rx = rot[0];
        bone.ry = rot[1];
        bone.rz = rot[2];
        try {
            if (o.has("cubes") && o.get("cubes").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("cubes")) {
                    if (el == null || !el.isJsonObject()) continue;
                    Cube cube = readCube(el.getAsJsonObject(), texW, texH);
                    if (cube != null && !cube.faces.isEmpty()) bone.cubes.add(cube);
                }
            }
        } catch (Throwable ignored) {
        }
        return bone;
    }

    private static Cube readCube(JsonObject o, float texW, float texH) {
        float[] origin;
        float[] size;
        float texU = 0.0F;
        float texV = 0.0F;
        try {
            origin = readFloat3(o, "origin");
            size = readFloat3(o, "size");
            if (o.has("uv") && o.get("uv").isJsonArray()) {
                JsonArray uv = o.getAsJsonArray("uv");
                texU = uv.get(0).getAsFloat();
                texV = uv.get(1).getAsFloat();
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (size[0] <= 0.0F || size[1] <= 0.0F || size[2] <= 0.0F) return null;
        float x0 = origin[0] / 16.0F;
        float y0 = origin[1] / 16.0F;
        float z0 = origin[2] / 16.0F;
        float x1 = x0 + size[0] / 16.0F;
        float y1 = y0 + size[1] / 16.0F;
        float z1 = z0 + size[2] / 16.0F;
        Cube cube = new Cube();
        cube.minX = Math.min(x0, x1);
        cube.maxX = Math.max(x0, x1);
        cube.minY = Math.min(y0, y1);
        cube.maxY = Math.max(y0, y1);
        cube.minZ = Math.min(z0, z1);
        cube.maxZ = Math.max(z0, z1);
        float w = size[0];
        float h = size[1];
        float d = size[2];
        addFace(cube, BbModel.DOWN, texU + d, texV, texU + d + w, texV + d, texW, texH);
        addFace(cube, BbModel.UP, texU + d + w, texV + d, texU + d + w + d, texV, texW, texH);
        addFace(cube, BbModel.WEST, texU, texV + d, texU + d, texV + d + h, texW, texH);
        addFace(cube, BbModel.NORTH, texU + d, texV + d, texU + d + w, texV + d + h, texW, texH);
        addFace(cube, BbModel.EAST, texU + d + w, texV + d, texU + d + w + d, texV + d + h, texW, texH);
        addFace(cube, BbModel.SOUTH, texU + d + w + d, texV + d, texU + d + w + w, texV + d + h, texW, texH);
        return cube;
    }

    private static void addFace(Cube cube, int dir, float u1, float v1, float u2, float v2, float texW, float texH) {
        BbModel.Face face = new BbModel.Face();
        face.dir = dir;
        face.u1 = u1 / texW;
        face.v1 = v1 / texH;
        face.u2 = u2 / texW;
        face.v2 = v2 / texH;
        cube.faces.add(face);
    }

    private static void readAnimations(JsonObject root, PetModel model) {
        try {
            if (!root.has("animations") || !root.get("animations").isJsonObject()) return;
            JsonObject anims = root.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
                try {
                    if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
                    JsonObject a = entry.getValue().getAsJsonObject();
                    PetAnim anim = new PetAnim();
                    try {
                        if (a.has("length")) anim.length = a.get("length").getAsFloat();
                    } catch (Throwable ignored) {
                    }
                    if (a.has("bones") && a.get("bones").isJsonObject()) {
                        JsonObject bones = a.getAsJsonObject("bones");
                        for (Map.Entry<String, JsonElement> be : bones.entrySet()) {
                            try {
                                if (be.getValue() == null || !be.getValue().isJsonObject()) continue;
                                JsonObject bo = be.getValue().getAsJsonObject();
                                BoneChans chans = new BoneChans();
                                boolean any = false;
                                for (String chan : new String[]{"r", "p", "s"}) {
                                    if (!bo.has(chan) || !bo.get(chan).isJsonArray()) continue;
                                    JsonArray arr = bo.getAsJsonArray(chan);
                                    JsonElement[] comps = new JsonElement[]{null, null, null};
                                    for (int i = 0; i < 3 && i < arr.size(); i++) {
                                        try {
                                            if (!arr.get(i).isJsonNull()) comps[i] = arr.get(i);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                    if (comps[0] == null && comps[1] == null && comps[2] == null) continue;
                                    any = true;
                                    if ("r".equals(chan)) {
                                        chans.rx = comps[0];
                                        chans.ry = comps[1];
                                        chans.rz = comps[2];
                                    } else if ("p".equals(chan)) {
                                        chans.px = comps[0];
                                        chans.py = comps[1];
                                        chans.pz = comps[2];
                                    } else {
                                        chans.sx = comps[0];
                                        chans.sy = comps[1];
                                        chans.sz = comps[2];
                                    }
                                }
                                if (any) anim.bones.put(be.getKey(), chans);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    model.anims.put(entry.getKey(), anim);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readBehavior(JsonObject root, PetModel model) {
        try {
            if (!root.has("behavior") || !root.get("behavior").isJsonObject()) return;
            JsonObject b = root.getAsJsonObject("behavior");
            Behavior out = model.behavior;
            try {
                if (b.has("mode")) out.mode = b.get("mode").getAsString();
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("look_at_player")) out.lookAtPlayer = b.get("look_at_player").getAsBoolean();
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("hover") && b.get("hover").isJsonObject()) {
                    JsonObject h = b.getAsJsonObject("hover");
                    if (h.has("height")) out.hoverHeight = h.get("height").getAsDouble();
                    if (h.has("bob_amplitude")) out.hoverAmp = h.get("bob_amplitude").getAsDouble();
                    if (h.has("bob_speed")) out.hoverSpeed = h.get("bob_speed").getAsDouble();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("follow") && b.get("follow").isJsonObject()) {
                    JsonObject f = b.getAsJsonObject("follow");
                    if (f.has("start_distance")) out.followStart = f.get("start_distance").getAsDouble();
                    if (f.has("stop_distance")) out.followStop = f.get("stop_distance").getAsDouble();
                    if (f.has("speed")) out.followSpeed = f.get("speed").getAsDouble();
                    if (f.has("teleport_distance")) out.teleportDistance = f.get("teleport_distance").getAsDouble();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("states") && b.get("states").isJsonObject()) {
                    JsonObject s = b.getAsJsonObject("states");
                    if (s.has("idle")) out.idleAnim = s.get("idle").getAsString();
                    if (s.has("moving")) out.moveAnim = s.get("moving").getAsString();
                    if (s.has("sit")) out.sitAnim = s.get("sit").getAsString();
                    if (s.has("sit_after_idle_seconds")) out.sitAfter = s.get("sit_after_idle_seconds").getAsDouble();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("reactions") && b.get("reactions").isJsonObject()) {
                    JsonObject r = b.getAsJsonObject("reactions");
                    if (r.has("player_sprint") && r.get("player_sprint").isJsonObject()) {
                        JsonObject sp = r.getAsJsonObject("player_sprint");
                        if (sp.has("speed_multiplier")) out.sprintMult = sp.get("speed_multiplier").getAsDouble();
                    }
                    if (r.has("player_sneak") && r.get("player_sneak").isJsonObject()) {
                        JsonObject sn = r.getAsJsonObject("player_sneak");
                        if (sn.has("state")) out.sneakSit = "sit".equals(sn.get("state").getAsString());
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                if (b.has("particles") && b.get("particles").isJsonArray()) {
                    for (JsonElement el : b.getAsJsonArray("particles")) {
                        try {
                            if (el == null || !el.isJsonObject()) continue;
                            JsonObject p = el.getAsJsonObject();
                            Particle part = new Particle();
                            part.type = p.has("type") ? p.get("type").getAsString() : "";
                            if (p.has("rate_per_second")) part.rate = p.get("rate_per_second").getAsDouble();
                            if (part.type != null && !part.type.isEmpty() && part.rate > 0.0) out.particles.add(part);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public static double evalWave(JsonElement def, double phi, double fallback) {
        try {
            if (def == null || def.isJsonNull()) return fallback;
            if (def.isJsonPrimitive() && def.getAsJsonPrimitive().isNumber()) return def.getAsDouble();
            if (!def.isJsonArray()) return fallback;
            JsonArray a = def.getAsJsonArray();
            if (a.size() < 5) return fallback;
            double off = a.get(0).getAsDouble();
            double amp = a.get(1).getAsDouble();
            double cyc = a.get(2).getAsDouble();
            double phase = a.get(3).getAsDouble();
            String kind = "";
            try {
                kind = a.get(4).getAsString();
            } catch (Throwable ignored) {
            }
            double x = cyc * phi + phase;
            double wave;
            if ("saw".equals(kind)) {
                wave = x - Math.floor(x);
            } else {
                wave = Math.sin(x * Math.PI * 2.0);
            }
            return off + amp * wave;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static void sampleAnim(PetAnim anim, String boneName, double timeSec, float[] rotOut, float[] posOut, float[] scaleOut) {
        try {
            if (anim == null || anim.length <= 0.0F || boneName == null) return;
            BoneChans chans = anim.bones.get(boneName);
            if (chans == null) return;
            double phi = (timeSec % anim.length) / anim.length;
            if (phi < 0.0) phi += 1.0;
            if (rotOut != null && rotOut.length >= 3) {
                rotOut[0] = (float) evalWave(chans.rx, phi, 0.0);
                rotOut[1] = (float) evalWave(chans.ry, phi, 0.0);
                rotOut[2] = (float) evalWave(chans.rz, phi, 0.0);
            }
            if (posOut != null && posOut.length >= 3) {
                posOut[0] = (float) evalWave(chans.px, phi, 0.0);
                posOut[1] = (float) evalWave(chans.py, phi, 0.0);
                posOut[2] = (float) evalWave(chans.pz, phi, 0.0);
            }
            if (scaleOut != null && scaleOut.length >= 3) {
                scaleOut[0] = (float) evalWave(chans.sx, phi, 1.0);
                scaleOut[1] = (float) evalWave(chans.sy, phi, 1.0);
                scaleOut[2] = (float) evalWave(chans.sz, phi, 1.0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static float[] readFloat3(JsonObject o, String key) {
        float[] out = new float[]{0.0F, 0.0F, 0.0F};
        try {
            if (o.has(key) && o.get(key).isJsonArray()) {
                JsonArray a = o.getAsJsonArray(key);
                for (int i = 0; i < 3 && i < a.size(); i++) {
                    out[i] = a.get(i).getAsFloat();
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
