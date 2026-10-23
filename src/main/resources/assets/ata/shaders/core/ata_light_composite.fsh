#version 150

uniform sampler2D SceneSampler;
uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform sampler2D HazeSampler;
uniform sampler2D BloomSampler;
uniform float BloomStrength;
uniform sampler2D BeamSampler;
uniform vec2 BeamSize;
uniform float BeamEnabled;

uniform vec4 Params;
uniform sampler2D GlareSampler;
uniform float GlareCount;
uniform float Daylight;
uniform vec3 GlareDir0;
uniform vec3 GlareDir1;
uniform vec3 GlareDir2;
uniform vec3 GlareDir3;
uniform sampler2D LevelsSampler;
uniform sampler2D LightmapSampler;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform vec2 DepthSize;
uniform vec3 LevelOrigin;
uniform vec3 LevelMod;

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

#moj_import <ata:ata_surface.glsl>

#moj_import <ata:ata_beam_medium.glsl>
vec3 airAt(vec2 uv) {
    if (BeamEnabled < 0.5) return vec3(0.0);
    vec2 cell = uv * BeamSize - 0.5;
    vec2 fraction = fract(cell);
    vec2 base = (floor(cell) + 0.5) / BeamSize;
    float centre = positionAt(uv).z;
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int index = 0; index < 4; index++) {
        vec2 offset = vec2(float(index & 1), float(index >> 1));
        vec2 neighbor = clamp(base + offset / BeamSize, vec2(0.0), vec2(1.0));
        vec2 interpolation = mix(1.0 - fraction, fraction, offset);
        float weight = interpolation.x * interpolation.y * beamBilateral(centre, positionAt(neighbor).z);
        sum += texture(BeamSampler, neighbor).rgb * weight;
        weights += weight;
    }
    return sum / max(weights, 0.00001);
}

vec3 glareAt(vec3 ray) {
    vec3 sum = vec3(0.0);
    for (int index = 0; index < 4; index++) {
        if (float(index) >= GlareCount) break;
        vec3 direction = index == 0 ? GlareDir0 : index == 1 ? GlareDir1 : index == 2 ? GlareDir2 : GlareDir3;
        float eye = texelFetch(GlareSampler, ivec2(index, 0), 0).r;
        float angle = degrees(acos(clamp(dot(ray, direction), -1.0, 1.0)));
        sum += vec3(1.0, 0.86, 0.68) * (0.8 * eye / (angle * angle + 0.5 * 0.5)) * (1.0 - 0.7 * Daylight);
    }
    return sum;
}

void main() {
    if (Params.z > 0.5 && Params.z < 1.5) {
        vec3 acc = max(texture(LightSampler, texCoord).rgb * Params.w, vec3(0.0));
        fragColor = vec4(1.0 - exp(-acc * 1.5), 1.0);
        return;
    }
    if (Params.z > 2.5) {
        vec3 acc = max(texture(LightSampler, texCoord).rgb * Params.w, vec3(0.0));
        fragColor = vec4(clamp(acc, vec3(0.0), vec3(1.0)), 1.0);
        return;
    }
    vec2 uv = texCoord;
    if (Params.y > 0.5) {
        uv = clamp(uv + texture(HazeSampler, texCoord).rg, vec2(0.0), vec2(1.0));
    }
    vec4 scene = texture(SceneSampler, uv);
    float depth = texture(DepthSampler, texCoord).r;
    float exposure = GlareCount > 0.5 ? texelFetch(GlareSampler, ivec2(4, 0), 0).r : 1.0;
    if (depth >= 1.0) {
        vec3 sky = pow(max(scene.rgb, vec3(0.0)), vec3(GAMMA)) * exposure;
        fragColor = vec4(pow(sky + airAt(texCoord), vec3(1.0 / GAMMA)) + glareAt(normalize(positionAt(texCoord))), scene.a);
        return;
    }
    vec3 c = pow(max(scene.rgb, vec3(0.0)), vec3(GAMMA));
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 albedo = c / max(lum, 0.02) * clamp(lum * 8.0, ALBEDO_FLOOR, 1.0) * ALBEDO_GAIN;
    if (Params.z > 1.5) {
        fragColor = vec4(pow(albedo, vec3(1.0 / GAMMA)), 1.0);
        return;
    }
    vec3 viewPoint = positionAt(texCoord);
    vec3 worldPoint = (InvView * vec4(viewPoint, 1.0)).xyz;
    vec3 worldNormal = mat3(InvView) * normalAt(texCoord, viewPoint);
    vec3 absoluteNormal = abs(worldNormal);
    float face = absoluteNormal.y >= max(absoluteNormal.x, absoluteNormal.z) ? (worldNormal.y > 0.0 ? 1.0 : 0.5)
        : absoluteNormal.x >= absoluteNormal.z ? 0.6 : 0.8;
    vec3 factor = brightnessAt(worldPoint, worldNormal) * face;
    vec3 recovered = clamp(c / max(factor, vec3(0.03)), vec3(0.0), vec3(0.95));
    albedo = mix(albedo, recovered, smoothstep(vec3(0.0), vec3(0.03), factor));
    vec4 lighting = texture(LightSampler, texCoord) * Params.x;
    vec3 add = albedo * lighting.rgb + vec3(1.0, 0.86, 0.68) * lighting.a
        + texture(BloomSampler, texCoord).rgb * BloomStrength + airAt(texCoord);
    c *= exposure;
    vec3 o = vec3(c.r + max(shoulder(c.r + add.r) - shoulder(c.r), 0.0),
                  c.g + max(shoulder(c.g + add.g) - shoulder(c.g), 0.0),
                  c.b + max(shoulder(c.b + add.b) - shoulder(c.b), 0.0));
    fragColor = vec4(pow(max(o, vec3(0.0)), vec3(1.0 / GAMMA)) + glareAt(normalize(viewPoint)), scene.a);
}
