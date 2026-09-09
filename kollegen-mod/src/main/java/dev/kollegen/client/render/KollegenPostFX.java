package dev.kollegen.client.render;


public final class KollegenPostFX {

    
    private static volatile boolean enabled = false;

    
    private static volatile float vignetteIntensity = 0.35f; 
    private static volatile float tintR = 1.0f;
    private static volatile float tintG = 1.0f;
    private static volatile float tintB = 1.0f;

    
    private static volatile int lastWidth = 0;
    private static volatile int lastHeight = 0;

    private KollegenPostFX() {
        
    }

    
    
    

    
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    
    public static boolean isEnabled() {
        return enabled;
    }

    
    public static void setVignetteIntensity(float v) {
        vignetteIntensity = clamp01(v);
    }

    
    public static void setTint(float r, float g, float b) {
        tintR = clamp01(r);
        tintG = clamp01(g);
        tintB = clamp01(b);
    }

    
    public static void resize(int width, int height) {
        lastWidth = width;
        lastHeight = height;
        
    }

    
    public static void close() {
        
    }

    
    public static void apply(int screenWidth, int screenHeight, float partialTicks) {
        if (!enabled) return;
        
        lastWidth = screenWidth;
        lastHeight = screenHeight;

        
        
        
        
        final float v = vignetteIntensity;
        final float r = tintR;
        final float g = tintG;
        final float b = tintB;
        
        
        
        
        

        
        
    }

    
    
    

    private static float clamp01(float x) {
        if (x <= 0f) return 0f;
        if (x >= 1f) return 1f;
        return x;
    }

}
