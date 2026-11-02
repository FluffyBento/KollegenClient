void applyWaving(inout vec4 position, inout vec3 normal, float lightY) {
    if (lightY < 0.70) {
        return;
    }

    float amplitude = 0.012 - 0.012 * 6.66 * max((0.85 - lightY), 0.0);
    amplitude -= 0.12 * 0.00002 * (position.x * position.x + position.z * position.z);
    amplitude = max(amplitude, 0.0);
    const float xFreq = 0.4;
    const float zFreq = 0.15;
    const float xFreq1 = 0.6;
    const float zFreq1= 0.33;

    position.xyz += ViewPos;

    float wave = amplitude * (sin(2000 * GameTime + xFreq * position.x + zFreq * position.z)
    + sin(1500 * GameTime + xFreq1 * position.x + zFreq1 * position.z));

    position.y += wave;

    float f0 = cos(2000 * GameTime + xFreq * position.x + zFreq * position.z);
    float f1 = cos(1500 * GameTime + xFreq1 * position.x + zFreq1 * position.z);
    vec3 normalOffset = vec3(amplitude * (xFreq * f0 + xFreq1 * f1),
    0.0,
    amplitude * (zFreq * f0 + zFreq1 * f1));

    normal += 0.2 * normalOffset;
//    normal = normalize(normal);

    position.xyz -= ViewPos;
}