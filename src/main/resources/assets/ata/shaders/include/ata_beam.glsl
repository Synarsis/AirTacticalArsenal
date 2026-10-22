float beamLow(float az, float el) {
    float cut = -0.6 + clamp(az, 0.0, 6.0) * 0.27;
    float below = cut - el;
    float horizontal = 0.95 * exp(-pow((az - 2.0) / 14.0, 2.0)) + 0.05 * (1.0 - smoothstep(45.0, 65.0, abs(az)));
    float vertical = below < 1.5 ? mix(0.35, 1.0, max(below, 0.0) / 1.5)
        : max(exp(-(below - 1.5) / 6.0), 0.02 * (1.0 - smoothstep(35.0, 50.0, below)));
    return mix(0.02 * horizontal, horizontal * vertical, smoothstep(-0.8, 0.0, below));
}
float beamHigh(float az, float el) {
    float horizontal = 0.9 * exp(-pow(az / 8.0, 2.0)) + 0.1 * (1.0 - smoothstep(25.0, 40.0, abs(az)));
    float vertical = 0.9 * exp(-pow(el / 4.0, 2.0)) + 0.1 * (1.0 - smoothstep(15.0, 30.0, abs(el)));
    return horizontal * vertical;
}
float beamPattern(float mode, vec3 offset, vec3 dir, vec3 right) {
    float forward = dot(offset, dir);
    if (forward <= 0.0) return 0.0;
    float across = dot(offset, right);
    float lift = dot(offset, cross(right, dir));
    float az = degrees(atan(across, forward));
    float el = degrees(atan(lift, forward));
    float angle = degrees(atan(length(vec2(across, lift)), forward));
    return max(mode > 1.5 ? beamHigh(az, el) : beamLow(az, el), 0.01 * (1.0 - smoothstep(60.0, 80.0, angle)));
}
float beamIrradiance(float intensity, float pattern, float distance, float radius) {
    float window = clamp(1.0 - pow(distance / radius, 4.0), 0.0, 1.0);
    return intensity * pattern / max(distance * distance, 0.35 * 0.35) * window * window;
}
