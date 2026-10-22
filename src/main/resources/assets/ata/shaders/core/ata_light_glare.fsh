#version 150
uniform sampler2D DepthSampler;
uniform sampler2D PreviousSampler;
uniform mat4 Proj;
uniform vec2 DepthSize;
uniform vec2 Timing;
uniform vec4 Eye0;
uniform vec4 Eye1;
uniform vec4 Eye2;
uniform vec4 Eye3;
out vec4 fragColor;
float visibleEye(vec4 eye) {
    if (eye.w <= 0.0 || eye.z >= -0.05) return 0.0;
    vec4 clip = Proj * vec4(eye.xyz, 1.0);
    vec3 screen = clip.xyz / clip.w * 0.5 + 0.5;
    if (any(lessThan(screen.xy, vec2(0.0))) || any(greaterThan(screen.xy, vec2(1.0)))) return 0.0;
    float visible = 0.0;
    for (int index = 0; index < 5; index++) {
        vec2 offset = index == 0 ? vec2(0.0) : index == 1 ? vec2(2.0, 0.0)
            : index == 2 ? vec2(-2.0, 0.0) : index == 3 ? vec2(0.0, 2.0) : vec2(0.0, -2.0);
        float depth = texture(DepthSampler, screen.xy + offset / DepthSize).r;
        visible += depth + 0.0001 >= screen.z ? 0.2 : 0.0;
    }
    return eye.w * visible;
}
void main() {
    int index = int(gl_FragCoord.x);
    if (index < 4) {
        vec4 eye = index == 0 ? Eye0 : index == 1 ? Eye1 : index == 2 ? Eye2 : Eye3;
        fragColor = vec4(visibleEye(eye), 0.0, 0.0, 1.0);
    } else {
        float sum = visibleEye(Eye0) + visibleEye(Eye1) + visibleEye(Eye2) + visibleEye(Eye3);
        float target = max(0.65, 1.0 / (1.0 + 0.1 * sum));
        float previous = Timing.y > 0.5 ? texelFetch(PreviousSampler, ivec2(4, 0), 0).r : 1.0;
        float duration = target < previous ? 0.3 : 1.5;
        float exposure = mix(previous, target, 1.0 - exp(-Timing.x / duration));
        fragColor = vec4(exposure, 0.0, 0.0, 1.0);
    }
}
