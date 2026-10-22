#version 150
uniform sampler2D SceneSampler;
uniform sampler2D LightSampler;
uniform sampler2D DepthSampler;
uniform sampler2D LevelsSampler;
uniform sampler2D LightmapSampler;
uniform vec4 Params;
uniform mat4 InvProj;
uniform mat4 InvView;
uniform vec2 DepthSize;
uniform vec3 LevelOrigin;
uniform vec3 LevelMod;
in vec2 texCoord;
out vec4 fragColor;
const float GAMMA = 2.2;
#moj_import <ata:ata_surface.glsl>
void main() {
    if (texture(DepthSampler, texCoord).r >= 1.0) {fragColor = vec4(0.0); return;}
    vec3 scene = pow(max(texture(SceneSampler, texCoord).rgb, vec3(0.0)), vec3(GAMMA));
    vec3 viewPoint = positionAt(texCoord);
    vec3 worldPoint = (InvView * vec4(viewPoint, 1.0)).xyz;
    vec3 normal = mat3(InvView) * normalAt(texCoord, viewPoint);
    vec3 absoluteNormal = abs(normal);
    float face = absoluteNormal.y >= max(absoluteNormal.x, absoluteNormal.z) ? (normal.y > 0.0 ? 1.0 : 0.5)
        : absoluteNormal.x >= absoluteNormal.z ? 0.6 : 0.8;
    vec3 factor = brightnessAt(worldPoint, normal) * face;
    float lum = dot(scene, vec3(0.2126, 0.7152, 0.0722));
    vec3 fallback = scene / max(lum, 0.02) * clamp(lum * 8.0, 0.4, 1.0) * 0.75;
    vec3 albedo = mix(fallback, clamp(scene / max(factor, vec3(0.03)), vec3(0.0), vec3(0.95)),
        smoothstep(vec3(0.0), vec3(0.03), factor));
    vec4 lighting = texture(LightSampler, texCoord) * Params.x;
    vec3 addition = albedo * lighting.rgb + vec3(1.0, 0.86, 0.68) * lighting.a;
    vec3 excess = max(scene + addition - 1.0, vec3(0.0));
    fragColor = vec4(excess / (1.0 + excess), 1.0);
}
