#version 150
uniform sampler2D DepthSampler;
uniform sampler2D PreviousSampler;
uniform mat4 Proj;
uniform mat4 InvProj;
uniform vec2 DepthSize;
uniform vec4 Timing;
uniform vec4 Eye0;
uniform vec4 Eye1;
uniform vec4 Eye2;
uniform vec4 Eye3;
uniform vec4 Slots;
out vec4 fragColor;
const float PI2 = 6.2831853;
const float DEPTH_MARGIN = 0.3;
const float SMOOTHING = 0.08;
float linearDistance(vec2 uv, float depth) {
    vec4 point = InvProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return -point.z / point.w;
}
float seenEye(vec4 eye) {
    if (eye.w <= 0.0 || eye.z >= -0.05) return 0.0;
    vec4 clip = Proj * vec4(eye.xyz, 1.0);
    vec3 screen = clip.xyz / clip.w * 0.5 + 0.5;
    if (any(lessThan(screen.xy, vec2(0.0))) || any(greaterThan(screen.xy, vec2(1.0)))) return 0.0;
    float lamp = -eye.z;
    float radius = clamp(Timing.z * Proj[1][1] / lamp * 0.5 * DepthSize.y, 1.5, 12.0);
    float visible = 0.0;
    for (int index = 0; index < 9; index++) {
        float angle = PI2 * float(index - 1) / 8.0;
        vec2 offset = index == 0 ? vec2(0.0) : radius * vec2(cos(angle), sin(angle));
        vec2 uv = clamp(screen.xy + offset / DepthSize, vec2(0.0), vec2(1.0));
        float depth = texture(DepthSampler, uv).r;
        visible += depth >= 1.0 || linearDistance(uv, depth) >= lamp - DEPTH_MARGIN ? 1.0 / 9.0 : 0.0;
    }
    return visible;
}
vec4 eyeAt(int slot) {
    return slot == 0 ? Eye0 : slot == 1 ? Eye1 : slot == 2 ? Eye2 : Eye3;
}
float smoothedEye(int slot) {
    vec4 eye = eyeAt(slot);
    float now = seenEye(eye);
    float before = slot == 0 ? Slots.x : slot == 1 ? Slots.y : slot == 2 ? Slots.z : Slots.w;
    if (Timing.y < 0.5 || before < -0.5) return now;
    float previous = texelFetch(PreviousSampler, ivec2(int(before + 0.5), 0), 0).g;
    return mix(previous, now, 1.0 - exp(-Timing.x / SMOOTHING));
}
void main() {
    int index = int(gl_FragCoord.x);
    if (index < 4) {
        float seen = smoothedEye(index);
        fragColor = vec4(eyeAt(index).w * seen, seen, 0.0, 1.0);
    } else {
        float sum = 0.0;
        for (int slot = 0; slot < 4; slot++) {
            sum += eyeAt(slot).w * smoothedEye(slot);
        }
        float target = max(0.4, 1.0 / (1.0 + 0.25 * sum));
        float previous = Timing.y > 0.5 ? texelFetch(PreviousSampler, ivec2(4, 0), 0).r : 1.0;
        float duration = target < previous ? 0.3 : 1.5;
        float exposure = mix(previous, target, 1.0 - exp(-Timing.x / duration));
        fragColor = vec4(exposure, 0.0, 0.0, 1.0);
    }
}
