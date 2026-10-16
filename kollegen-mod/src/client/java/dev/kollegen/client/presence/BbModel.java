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

public final class BbModel {
    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private static final Map<String, BbModel> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<>();

    public final float texWidth;
    public final float texHeight;
    public final List<Bone> bones = new ArrayList<>();
    public final List<Cube> loose = new ArrayList<>();
    public float cx;
    public float cy;
    public float cz;
    public float animLength;
    public final Map<String, AnimBone> anims = new HashMap<>();

    public static final class AnimBone {
        public float[] rTimes = new float[0];
        public float[][] rVals = new float[0][];
        public byte[] rInterp = new byte[0];
        public float[] pTimes = new float[0];
        public float[][] pVals = new float[0][];
        public byte[] pInterp = new byte[0];
        public float[] sTimes = new float[0];
        public float[][] sVals = new float[0][];
        public byte[] sInterp = new byte[0];
    }

    private BbModel(float texWidth, float texHeight) {
        this.texWidth = texWidth;
        this.texHeight = texHeight;
    }

    public static final class Bone {
        public String name = "";
        public float ox;
        public float oy;
        public float oz;
        public float rx;
        public float ry;
        public float rz;
        public final List<Cube> cubes = new ArrayList<>();
        public final List<Bone> children = new ArrayList<>();

        public boolean hasRotation() {
            return rx != 0.0F || ry != 0.0F || rz != 0.0F;
        }
    }

    public static final class Cube {
        public float ox;
        public float oy;
        public float oz;
        public float rx;
        public float ry;
        public float rz;
        public float minX;
        public float minY;
        public float maxX;
        public float maxY;
        public float minZ;
        public float maxZ;
        public float frameW;
        public final List<Face> faces = new ArrayList<>();

        public boolean hasRotation() {
            return rx != 0.0F || ry != 0.0F || rz != 0.0F;
        }
    }

    public static final class Face {
        public int dir;
        public float u1;
        public float v1;
        public float u2;
        public float v2;
    }

    public static BbModel load(ResourceManager manager, Identifier modelId) {
        if (manager == null || modelId == null) return null;
        String key = modelId.toString();
        BbModel cached = CACHE.get(key);
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
                BbModel model = parse(root.getAsJsonObject());
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

    public static BbModel parse(JsonObject root) {
        if (root == null) return null;
        float texW = 64.0F;
        float texH = 64.0F;
        try {
            if (root.has("resolution") && root.get("resolution").isJsonObject()) {
                JsonObject res = root.getAsJsonObject("resolution");
                if (res.has("width")) texW = res.get("width").getAsFloat();
                if (res.has("height")) texH = res.get("height").getAsFloat();
            } else if (root.has("texture_size") && root.get("texture_size").isJsonArray()) {
                JsonArray ts = root.getAsJsonArray("texture_size");
                if (ts.size() > 0) texW = ts.get(0).getAsFloat();
                if (ts.size() > 1) texH = ts.get(1).getAsFloat();
            }
        } catch (Throwable ignored) {
        }
        if (texW <= 0.0F) texW = 64.0F;
        if (texH <= 0.0F) texH = 64.0F;
        BbModel model = new BbModel(texW, texH);
        Map<String, Cube> byUuid = new HashMap<>();
        try {
            if (root.has("elements") && root.get("elements").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("elements")) {
                    if (el == null || !el.isJsonObject()) continue;
                    Cube cube = readCube(el.getAsJsonObject(), texW, texH);
                    if (cube == null) continue;
                    String uuid = "";
                    try {
                        if (el.getAsJsonObject().has("uuid")) uuid = el.getAsJsonObject().get("uuid").getAsString();
                    } catch (Throwable ignored) {
                    }
                    if (!uuid.isEmpty()) byUuid.put(uuid, cube);
                    else model.loose.add(cube);
                }
            }
        } catch (Throwable ignored) {
        }
        Map<String, Boolean> claimed = new HashMap<>();
        try {
            if (root.has("outliner") && root.get("outliner").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("outliner")) {
                    Bone bone = readOutlinerEntry(el, byUuid, claimed);
                    if (bone != null) model.bones.add(bone);
                }
            }
        } catch (Throwable ignored) {
        }
        for (Map.Entry<String, Cube> e : byUuid.entrySet()) {
            if (!claimed.containsKey(e.getKey())) model.loose.add(e.getValue());
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        List<Cube> all = new ArrayList<>();
        for (Bone b : model.bones) collectCubes(b, all);
        all.addAll(model.loose);
        for (Cube c : all) {
            if (c.minX < minX) minX = c.minX;
            if (c.minY < minY) minY = c.minY;
            if (c.minZ < minZ) minZ = c.minZ;
            if (c.maxX > maxX) maxX = c.maxX;
            if (c.maxY > maxY) maxY = c.maxY;
            if (c.maxZ > maxZ) maxZ = c.maxZ;
        }
        if (!all.isEmpty()) {
            model.cx = (minX + maxX) / 2.0F;
            model.cy = (minY + maxY) / 2.0F;
            model.cz = (minZ + maxZ) / 2.0F;
        }
        readAnimation(root, model);
        return model;
    }

    private static void collectCubes(Bone bone, List<Cube> out) {
        out.addAll(bone.cubes);
        for (Bone child : bone.children) collectCubes(child, out);
    }

    private static void readAnimation(JsonObject root, BbModel model) {
        try {
            if (!root.has("animations") || !root.get("animations").isJsonArray()) return;
            JsonArray anims = root.getAsJsonArray("animations");
            if (anims.size() == 0) return;
            JsonObject chosen = null;
            for (JsonElement el : anims) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject a = el.getAsJsonObject();
                if (chosen == null) chosen = a;
                try {
                    if (a.has("loop") && "loop".equals(a.get("loop").getAsString())) {
                        chosen = a;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (chosen == null) return;
            float length = 0.0F;
            try {
                if (chosen.has("length")) length = chosen.get("length").getAsFloat();
            } catch (Throwable ignored) {
            }
            if (length <= 0.0F) return;
            if (!chosen.has("animators") || !chosen.get("animators").isJsonObject()) return;
            JsonObject animators = chosen.getAsJsonObject("animators");
            for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                try {
                    if (entry.getValue() == null || !entry.getValue().isJsonObject()) continue;
                    JsonObject at = entry.getValue().getAsJsonObject();
                    if (!at.has("name") || !at.has("keyframes")) continue;
                    String boneName = at.get("name").getAsString();
                    if (boneName == null || boneName.isEmpty()) continue;
                    JsonArray keys = at.getAsJsonArray("keyframes");
                    List<Float> rt = new ArrayList<>();
                    List<float[]> rv = new ArrayList<>();
                    List<Byte> ri = new ArrayList<>();
                    List<Float> pt = new ArrayList<>();
                    List<float[]> pv = new ArrayList<>();
                    List<Byte> pi = new ArrayList<>();
                    List<Float> st = new ArrayList<>();
                    List<float[]> sv = new ArrayList<>();
                    List<Byte> si = new ArrayList<>();
                    for (JsonElement kel : keys) {
                        if (kel == null || !kel.isJsonObject()) continue;
                        JsonObject k = kel.getAsJsonObject();
                        String channel = "";
                        try {
                            channel = k.get("channel").getAsString();
                        } catch (Throwable ignored) {
                            continue;
                        }
                        if (!"rotation".equals(channel) && !"position".equals(channel) && !"scale".equals(channel)) continue;
                        float time = 0.0F;
                        try {
                            time = k.get("time").getAsFloat();
                        } catch (Throwable ignored) {
                            continue;
                        }
                        float[] xyz = new float[]{0.0F, 0.0F, 0.0F};
                        try {
                            JsonArray dps = k.getAsJsonArray("data_points");
                            if (dps == null || dps.size() == 0) continue;
                            JsonObject dp = dps.get(0).getAsJsonObject();
                            xyz[0] = parseAnimFloat(dp, "x");
                            xyz[1] = parseAnimFloat(dp, "y");
                            xyz[2] = parseAnimFloat(dp, "z");
                        } catch (Throwable ignored) {
                            continue;
                        }
                        byte interp = 0;
                        try {
                            String s = k.get("interpolation").getAsString();
                            if ("catmullrom".equals(s)) interp = 1;
                            else if ("step".equals(s)) interp = 2;
                        } catch (Throwable ignored) {
                        }
                        if ("rotation".equals(channel)) {
                            rt.add(time);
                            rv.add(xyz);
                            ri.add(interp);
                        } else if ("scale".equals(channel)) {
                            st.add(time);
                            sv.add(xyz);
                            si.add(interp);
                        } else {
                            pt.add(time);
                            pv.add(xyz);
                            pi.add(interp);
                        }
                    }
                    if (rt.isEmpty() && pt.isEmpty() && st.isEmpty()) continue;
                    AnimBone ab = model.anims.get(boneName);
                    if (ab == null) {
                        ab = new AnimBone();
                        model.anims.put(boneName, ab);
                    }
                    if (!rt.isEmpty()) {
                        ab.rTimes = toFloatArray(rt);
                        ab.rVals = rv.toArray(new float[0][]);
                        ab.rInterp = toByteArray(ri);
                    }
                    if (!pt.isEmpty()) {
                        ab.pTimes = toFloatArray(pt);
                        ab.pVals = pv.toArray(new float[0][]);
                        ab.pInterp = toByteArray(pi);
                    }
                    if (!st.isEmpty()) {
                        ab.sTimes = toFloatArray(st);
                        ab.sVals = sv.toArray(new float[0][]);
                        ab.sInterp = toByteArray(si);
                    }
                } catch (Throwable ignored) {
                }
            }
            if (!model.anims.isEmpty()) model.animLength = length;
        } catch (Throwable ignored) {
        }
    }

    private static float parseAnimFloat(JsonObject o, String key) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) return 0.0F;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return e.getAsFloat();
            return Float.parseFloat(e.getAsString());
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    private static float[] toFloatArray(List<Float> in) {
        float[] out = new float[in.size()];
        for (int i = 0; i < in.size(); i++) out[i] = in.get(i);
        return out;
    }

    private static byte[] toByteArray(List<Byte> in) {
        byte[] out = new byte[in.size()];
        for (int i = 0; i < in.size(); i++) out[i] = in.get(i);
        return out;
    }

    public static boolean sampleTrack(float[] times, float[][] vals, byte[] interp, double timeSec, float length, float[] out) {
        try {
            if (times == null || vals == null || times.length == 0 || out == null || out.length < 3) return false;
            int n = times.length;
            if (n == 1 || length <= 0.0F) {
                float[] v = vals[Math.min(0, vals.length - 1)];
                out[0] = v[0];
                out[1] = v[1];
                out[2] = v[2];
                return true;
            }
            double l = length;
            double rel = (timeSec - times[0]) % l;
            if (rel < 0) rel += l;
            double tt = times[0] + rel;
            int seg = n - 1;
            for (int k = 0; k < n; k++) {
                double end = (k + 1 < n) ? times[k + 1] : (times[0] + l);
                if (tt >= times[k] && tt <= end) {
                    seg = k;
                    break;
                }
            }
            double t0 = times[seg];
            double t1 = (seg + 1 < n) ? times[seg + 1] : (times[0] + l);
            double span = t1 - t0;
            double u = span > 0.0 ? (tt - t0) / span : 0.0;
            if (u < 0.0) u = 0.0;
            if (u > 1.0) u = 1.0;
            byte mode = (interp != null && seg < interp.length) ? interp[seg] : 0;
            float[] a = vals[seg];
            float[] b = vals[(seg + 1) % n];
            if (mode == 2) {
                out[0] = a[0];
                out[1] = a[1];
                out[2] = a[2];
                return true;
            }
            if (mode == 1) {
                float[] p0 = vals[(seg - 1 + n) % n];
                float[] p3 = vals[(seg + 2) % n];
                for (int c = 0; c < 3; c++) {
                    out[c] = catmullRom(p0[c], a[c], b[c], p3[c], (float) u);
                }
                return true;
            }
            float f = (float) u;
            out[0] = a[0] + (b[0] - a[0]) * f;
            out[1] = a[1] + (b[1] - a[1]) * f;
            out[2] = a[2] + (b[2] - a[2]) * f;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (2.0F * p1 + (-p0 + p2) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    private static Bone readOutlinerEntry(JsonElement el, Map<String, Cube> byUuid, Map<String, Boolean> claimed) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            return null;
        }
        if (!el.isJsonObject()) return null;
        JsonObject o = el.getAsJsonObject();
        Bone bone = new Bone();
        try {
            if (o.has("name")) bone.name = o.get("name").getAsString();
        } catch (Throwable ignored) {
        }
        float[] origin = readFloat3(o, "origin");
        bone.ox = origin[0];
        bone.oy = origin[1];
        bone.oz = origin[2];
        float[] rot = readFloat3(o, "rotation");
        bone.rx = -rot[0];
        bone.ry = rot[1];
        bone.rz = -rot[2];
        try {
            if (o.has("children") && o.get("children").isJsonArray()) {
                for (JsonElement child : o.getAsJsonArray("children")) {
                    if (child == null || child.isJsonNull()) continue;
                    if (child.isJsonPrimitive()) {
                        String uuid = child.getAsString();
                        Cube cube = byUuid.get(uuid);
                        if (cube != null) {
                            bone.cubes.add(cube);
                            claimed.put(uuid, Boolean.TRUE);
                        }
                    } else if (child.isJsonObject()) {
                        Bone nested = readOutlinerEntry(child, byUuid, claimed);
                        if (nested != null) bone.children.add(nested);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return bone;
    }

    private static Cube readCube(JsonObject o, float texW, float texH) {
        float[] from;
        float[] to;
        try {
            from = readFloat3(o, "from");
            to = readFloat3(o, "to");
        } catch (Throwable ignored) {
            return null;
        }
        Cube cube = new Cube();
        try {
            if (o.has("name") && "visor_lens".equals(o.get("name").getAsString()) && Boolean.getBoolean("kollegen.lensAnim")) cube.frameW = 128.0F;
        } catch (Throwable ignored) {
        }
        cube.minX = Math.min(from[0], to[0]) / 16.0F;
        cube.maxX = Math.max(from[0], to[0]) / 16.0F;
        cube.minY = (24.0F - Math.max(from[1], to[1])) / 16.0F;
        cube.maxY = (24.0F - Math.min(from[1], to[1])) / 16.0F;
        cube.minZ = Math.min(from[2], to[2]) / 16.0F;
        cube.maxZ = Math.max(from[2], to[2]) / 16.0F;
        float[] origin = readFloat3(o, "origin");
        cube.ox = origin[0];
        cube.oy = origin[1];
        cube.oz = origin[2];
        float[] rot = readFloat3(o, "rotation");
        cube.rx = -rot[0];
        cube.ry = rot[1];
        cube.rz = -rot[2];
        try {
            if (o.has("faces") && o.get("faces").isJsonObject()) {
                JsonObject faces = o.getAsJsonObject("faces");
                addFace(faces, "down", DOWN, cube, texW, texH);
                addFace(faces, "up", UP, cube, texW, texH);
                addFace(faces, "north", NORTH, cube, texW, texH);
                addFace(faces, "south", SOUTH, cube, texW, texH);
                addFace(faces, "west", WEST, cube, texW, texH);
                addFace(faces, "east", EAST, cube, texW, texH);
            }
        } catch (Throwable ignored) {
        }
        if (cube.faces.isEmpty()) return null;
        return cube;
    }

    private static void addFace(JsonObject faces, String key, int dir, Cube cube, float texW, float texH) {
        if (!faces.has(key) || faces.get(key).isJsonNull() || !faces.get(key).isJsonObject()) return;
        JsonObject f = faces.getAsJsonObject(key);
        try {
            if (f.has("enabled") && !f.get("enabled").getAsBoolean()) return;
        } catch (Throwable ignored) {
        }
        try {
            if (!f.has("uv") || !f.get("uv").isJsonArray()) return;
            JsonArray uv = f.getAsJsonArray("uv");
            if (uv.size() < 4) return;
            Face face = new Face();
            face.dir = dir;
            face.u1 = uv.get(0).getAsFloat() / texW;
            face.v1 = uv.get(1).getAsFloat() / texH;
            face.u2 = uv.get(2).getAsFloat() / texW;
            face.v2 = uv.get(3).getAsFloat() / texH;
            cube.faces.add(face);
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

    public static float pivotX(float ox) {
        return ox / 16.0F;
    }

    public static float pivotY(float oy) {
        return (24.0F - oy) / 16.0F;
    }

    public static float pivotZ(float oz) {
        return oz / 16.0F;
    }

    public static float[][] cornersFor(Cube cube, int dir) {
        return cornersFor(cube.minX, cube.minY, cube.maxX, cube.maxY, cube.minZ, cube.maxZ, dir);
    }

    public static float[][] cornersFor(float minX, float minY, float maxX, float maxY, float minZ, float maxZ, int dir) {
        if (dir == DOWN) {
            return new float[][]{{maxX, minY, maxZ}, {minX, minY, maxZ}, {minX, minY, minZ}, {maxX, minY, minZ}};
        }
        if (dir == UP) {
            return new float[][]{{maxX, maxY, minZ}, {minX, maxY, minZ}, {minX, maxY, maxZ}, {maxX, maxY, maxZ}};
        }
        if (dir == NORTH) {
            return new float[][]{{maxX, minY, minZ}, {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}};
        }
        if (dir == SOUTH) {
            return new float[][]{{minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}};
        }
        if (dir == WEST) {
            return new float[][]{{minX, minY, minZ}, {minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}};
        }
        return new float[][]{{maxX, minY, maxZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}};
    }

    public static float[][] uvsFor(Face face) {
        return new float[][]{{face.u2, face.v1}, {face.u1, face.v1}, {face.u1, face.v2}, {face.u2, face.v2}};
    }

    public static float[] normalFor(int dir) {
        if (dir == DOWN) return new float[]{0.0F, -1.0F, 0.0F};
        if (dir == UP) return new float[]{0.0F, 1.0F, 0.0F};
        if (dir == NORTH) return new float[]{0.0F, 0.0F, -1.0F};
        if (dir == SOUTH) return new float[]{0.0F, 0.0F, 1.0F};
        if (dir == WEST) return new float[]{-1.0F, 0.0F, 0.0F};
        return new float[]{1.0F, 0.0F, 0.0F};
    }
}
