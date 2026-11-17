package dev.kollegen.client.map;

public class MapChunk {
    public final int chunkX;
    public final int chunkZ;
    public final int[] colors = new int[256];
    public long lastSampled;

    public MapChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.lastSampled = System.currentTimeMillis();
    }
}
