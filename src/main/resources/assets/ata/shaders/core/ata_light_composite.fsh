#version 150

uniform sampler2D SceneSampler;
uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HazeSampler;

uniform vec4 Params;

in vec2 texCoord;

out vec4 fragColor;

const float GAMMA = 2.2;
const float KNEE = 0.8;
const float SHOULDER_WIDTH = 0.2;
const float ALBEDO_GAIN = 0.75;
const float ALBEDO_FLOOR = 0.4;

float shoulder(float x) {
    return x <= KNEE ? x : KNEE + SHOULDER_WIDTH * (1.0 - exp(-(x - KNEE) / SHOULDER_WIDTH));
}

void main() {
    if (Params.z > 0.5) {
        vec3 acc = max(texture(LightSampler, texCoord).rgb * Params.w, vec3(0.0));
        fragColor = vec4(1.0 - exp(-acc * 1.5), 1.0);
        return;
    }
    vec2 uv = texCoord;
    if (Params.y > 0.5) {
        uv = clamp(uv + texture(HazeSampler, texCoord).rg, vec2(0.0), vec2(1.0));
    }
    vec4 scene = texture(SceneSampler, uv);
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 1.0) {
        fragColor = scene;
        return;
    }
    vec3 c = pow(max(scene.rgb, vec3(0.0)), vec3(GAMMA));
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 albedo = c / max(lum, 0.02) * clamp(lum * 8.0, ALBEDO_FLOOR, 1.0) * ALBEDO_GAIN;
    vec3 light = texture(LightSampler, texCoord).rgb * Params.x;
    vec3 add = albedo * light;
    vec3 o = vec3(c.r + max(shoulder(c.r + add.r) - shoulder(c.r), 0.0),
                  c.g + max(shoulder(c.g + add.g) - shoulder(c.g), 0.0),
                  c.b + max(shoulder(c.b + add.b) - shoulder(c.b), 0.0));
    fragColor = vec4(pow(max(o, vec3(0.0)), vec3(1.0 / GAMMA)), scene.a);
}
