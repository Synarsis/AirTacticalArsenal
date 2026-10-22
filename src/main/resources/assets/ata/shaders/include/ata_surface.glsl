vec3 positionAt(vec2 uv) {
    vec4 point = InvProj * vec4(uv * 2.0 - 1.0, texture(DepthSampler, uv).r * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}

vec3 normalAt(vec2 uv, vec3 centre) {
    vec2 pixel = 1.0 / DepthSize;
    vec3 positiveX = positionAt(uv + vec2(pixel.x, 0.0));
    vec3 negativeX = positionAt(uv - vec2(pixel.x, 0.0));
    vec3 positiveY = positionAt(uv + vec2(0.0, pixel.y));
    vec3 negativeY = positionAt(uv - vec2(0.0, pixel.y));
    vec3 across = abs(positiveX.z - centre.z) < abs(negativeX.z - centre.z) ? positiveX - centre : centre - negativeX;
    vec3 vertical = abs(positiveY.z - centre.z) < abs(negativeY.z - centre.z) ? positiveY - centre : centre - negativeY;
    vec3 normal = cross(across, vertical);
    normal = length(normal) > 0.000001 ? normalize(normal) : vec3(0.0, 0.0, 1.0);
    return dot(normal, -centre) < 0.0 ? -normal : normal;
}

vec2 levelsAt(ivec3 cell) {
    if (any(lessThan(cell, ivec3(0))) || any(greaterThanEqual(cell, ivec3(128, 64, 128)))) {
        return vec2(0.0);
    }
    ivec3 ring = (cell + ivec3(LevelMod)) & ivec3(127, 63, 127);
    ivec2 atlas = ivec2(ring.x + 128 * (ring.y & 7), ring.z + 128 * (ring.y >> 3));
    int packedLevel = int(texelFetch(LevelsSampler, atlas, 0).r * 255.0 + 0.5);
    return vec2(float(packedLevel & 15), float(packedLevel >> 4));
}

vec3 brightnessAt(vec3 point, vec3 normal) {
    vec3 absoluteNormal = abs(normal);
    vec3 axis = absoluteNormal.y >= max(absoluteNormal.x, absoluteNormal.z) ? vec3(0.0, sign(normal.y), 0.0)
        : absoluteNormal.x >= absoluteNormal.z ? vec3(sign(normal.x), 0.0, 0.0) : vec3(0.0, 0.0, sign(normal.z));
    vec3 first = abs(axis.x) > 0.5 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0);
    vec3 second = abs(axis.y) > 0.5 ? vec3(0.0, 0.0, 1.0) : vec3(0.0, 1.0, 0.0);
    vec3 samplePoint = point + axis * 0.5 - LevelOrigin;
    vec3 shifted = samplePoint - 0.5 * (first + second);
    ivec3 base = ivec3(floor(shifted));
    vec2 fraction = vec2(dot(fract(shifted), first), dot(fract(shifted), second));
    vec2 levels = mix(mix(levelsAt(base), levelsAt(base + ivec3(first)), fraction.x),
        mix(levelsAt(base + ivec3(second)), levelsAt(base + ivec3(first + second)), fraction.x), fraction.y);
    return pow(texture(LightmapSampler, (levels + 0.5) / 16.0).rgb, vec3(GAMMA));
}
