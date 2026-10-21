#version 150

uniform sampler2D DepthSampler;

uniform mat4 InvProj;
uniform vec4 Haze;
uniform vec2 MaxShift;
uniform vec2 HazeSize;

in vec2 local;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    float r = length(local);
    if (r >= 1.0) {
        discard;
    }
    vec2 uv = gl_FragCoord.xy / HazeSize;

    float sceneDepth = texture(DepthSampler, uv).r;
    if (sceneDepth < 1.0) {
        vec4 v = InvProj * vec4(uv * 2.0 - 1.0, sceneDepth * 2.0 - 1.0, 1.0);
        float dist = -(v.z / v.w);
        if (dist < Haze.x - 1.0) {
            discard;
        }
    }

    float mask = 1.0 - smoothstep(0.35, 1.0, r);
    vec2 q = local * 3.0 + vec2(0.0, -Haze.z * 2.5);
    vec2 n = vec2(noise(q), noise(q + vec2(31.7, 17.3))) * 2.0 - 1.0;
    fragColor = vec4(n * mask * Haze.y * MaxShift, 0.0, 1.0);
}
