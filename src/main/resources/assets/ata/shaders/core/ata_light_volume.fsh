#version 150

uniform sampler2D DepthSampler;
uniform sampler2D OccupancySampler;

uniform mat4 InvProj;
uniform mat4 Proj;
uniform vec4 LightPos;
uniform vec4 LightColor;
uniform vec4 LightDir;
uniform vec4 LightCone;
uniform vec2 DepthSize;
uniform vec2 FogRange;
uniform float AccScale;
uniform mat4 InvView;
uniform vec4 LightWorld;
uniform vec4 Shadow;
uniform vec4 OccOrigin;
uniform vec4 OccMod;

in vec2 texCoord;

out vec4 fragColor;

float depthOf(vec2 uv) {
    return texture(DepthSampler, uv).r;
}

vec3 viewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 v = InvProj * clip;
    return v.xyz / v.w;
}

vec3 surfaceNormal(vec2 uv, vec3 c) {
    vec2 px = 1.0 / DepthSize;
    vec3 xp = viewPosition(uv + vec2(px.x, 0.0), depthOf(uv + vec2(px.x, 0.0)));
    vec3 xn = viewPosition(uv - vec2(px.x, 0.0), depthOf(uv - vec2(px.x, 0.0)));
    vec3 yp = viewPosition(uv + vec2(0.0, px.y), depthOf(uv + vec2(0.0, px.y)));
    vec3 yn = viewPosition(uv - vec2(0.0, px.y), depthOf(uv - vec2(0.0, px.y)));
    vec3 dx = abs(xp.z - c.z) < abs(c.z - xn.z) ? xp - c : c - xn;
    vec3 dy = abs(yp.z - c.z) < abs(c.z - yn.z) ? yp - c : c - yn;
    vec3 n = cross(dx, dy);
    float len = length(n);
    if (len < 1.0e-9) {
        return vec3(0.0, 0.0, 1.0);
    }
    n /= len;
    if (dot(n, -c) < 0.0) {
        n = -n;
    }
    return n;
}

float contactShadow(vec3 p, vec3 toLight, float d) {
    float reach = min(d, 6.0);
    float blocked = 0.0;
    for (int i = 1; i <= 12; i++) {
        float t = float(i) / 12.0;
        vec3 q = p + toLight * reach * t;
        vec4 clip = Proj * vec4(q, 1.0);
        if (clip.w <= 0.0) {
            continue;
        }
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            continue;
        }
        float sd = depthOf(uv);
        if (sd >= 1.0) {
            continue;
        }
        float sceneZ = viewPosition(uv, sd).z;
        float thickness = mix(0.05, 0.3, t);
        if (sceneZ > q.z + thickness && sceneZ - q.z < 1.5) {
            blocked += 1.0;
        }
    }
    return 1.0 - blocked / 12.0;
}

const int OCC_X = 128;
const int OCC_Y = 64;
const int OCC_Z = 128;
const int MAX_STEPS = 96;
const float MAX_REACH = 64.0;
const float SHUT = 0.02;
const float NORMAL_BIAS = 0.05;

float occupancyAt(ivec3 c) {
    if (c.x < 0 || c.y < 0 || c.z < 0 || c.x >= OCC_X || c.y >= OCC_Y || c.z >= OCC_Z) {
        return 0.0;
    }
    ivec3 r = c + ivec3(OccMod.xyz);
    r.x = r.x & (OCC_X - 1);
    r.y = r.y & (OCC_Y - 1);
    r.z = r.z & (OCC_Z - 1);
    int u = r.x + OCC_X * (r.y & 7);
    int v = r.z + OCC_Z * (r.y >> 3);
    return texelFetch(OccupancySampler, ivec2(u, v), 0).r;
}

float shadowRay(vec3 from, vec3 to) {
    vec3 delta = to - from;
    float len = length(delta);
    float reach = min(len - Shadow.w, MAX_REACH);
    if (reach <= 0.0) {
        return 1.0;
    }
    vec3 dir = delta / len;
    if (abs(dir.x) < 1.0e-6) { dir.x = 1.0e-6; }
    if (abs(dir.y) < 1.0e-6) { dir.y = 1.0e-6; }
    if (abs(dir.z) < 1.0e-6) { dir.z = 1.0e-6; }
    ivec3 cell = ivec3(floor(from));
    ivec3 stepDir = ivec3(dir.x > 0.0 ? 1 : -1, dir.y > 0.0 ? 1 : -1, dir.z > 0.0 ? 1 : -1);
    vec3 tDelta = abs(1.0 / dir);
    vec3 edge = vec3(cell) + vec3(stepDir.x > 0 ? 1.0 : 0.0, stepDir.y > 0 ? 1.0 : 0.0, stepDir.z > 0 ? 1.0 : 0.0);
    vec3 tMax = (edge - from) / dir;
    float transmittance = 1.0;
    float entry = 0.0;
    for (int i = 0; i < MAX_STEPS; i++) {
        if (entry >= reach) {
            break;
        }
        transmittance *= 1.0 - occupancyAt(cell);
        if (transmittance < SHUT) {
            return 0.0;
        }
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            entry = tMax.x;
            tMax.x += tDelta.x;
            cell.x += stepDir.x;
        } else if (tMax.y < tMax.z) {
            entry = tMax.y;
            tMax.y += tDelta.y;
            cell.y += stepDir.y;
        } else {
            entry = tMax.z;
            tMax.z += tDelta.z;
            cell.z += stepDir.z;
        }
    }
    return transmittance;
}

vec3 snapToAxis(vec3 n) {
    vec3 a = abs(n);
    if (a.x >= a.y && a.x >= a.z) {
        return vec3(n.x < 0.0 ? -1.0 : 1.0, 0.0, 0.0);
    }
    if (a.y >= a.z) {
        return vec3(0.0, n.y < 0.0 ? -1.0 : 1.0, 0.0);
    }
    return vec3(0.0, 0.0, n.z < 0.0 ? -1.0 : 1.0);
}

float blockShadow(vec3 p, vec3 n) {
    vec3 world = (InvView * vec4(p, 1.0)).xyz;
    vec3 worldNormal = mat3(InvView) * n;
    vec3 from = world + snapToAxis(worldNormal) * NORMAL_BIAS - OccOrigin.xyz;
    vec3 light = LightWorld.xyz - OccOrigin.xyz;
    if (Shadow.y < 1.5) {
        return shadowRay(from, light);
    }
    vec3 way = normalize(light - from);
    vec3 up = abs(way.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 across = normalize(cross(way, up)) * Shadow.z;
    return 0.5 * (shadowRay(from, light + across) + shadowRay(from, light - across));
}

void main() {
    float depth = depthOf(texCoord);
    if (depth >= 1.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 p = viewPosition(texCoord, depth);
    vec3 toL = LightPos.xyz - p;
    float d = length(toL);
    float radius = LightPos.w;
    if (d >= radius) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 l = toL / max(d, 1.0e-4);

    float x = d / radius;
    float window = clamp(1.0 - x * x * x * x, 0.0, 1.0);
    float k = d / (0.25 * radius);
    float attenuation = window * window / (1.0 + k * k);

    float spot = 1.0;
    if (LightCone.y > 0.5) {
        spot = smoothstep(LightDir.w, LightCone.x, dot(-l, LightDir.xyz));
    }

    vec3 n = vec3(0.0, 0.0, 1.0);
    if (LightCone.w > 0.5 || Shadow.x > 0.5) {
        n = surfaceNormal(texCoord, p);
    }
    float shade = 0.6;
    if (LightCone.w > 0.5) {
        shade = LightColor.w + (1.0 - LightColor.w) * max(dot(n, l), 0.0);
    }

    float base = attenuation * spot * shade;
    if (base * max(max(LightColor.r, LightColor.g), LightColor.b) * AccScale < 0.0005) {
        fragColor = vec4(0.0);
        return;
    }

    float shadow = 1.0;
    if (Shadow.x > 0.5) {
        shadow = blockShadow(p, n);
    }
    if (LightCone.z > 0.5) {
        shadow *= contactShadow(p, l, d);
    }

    float fog = clamp((length(p) - FogRange.x) / max(FogRange.y - FogRange.x, 1.0e-3), 0.0, 1.0);

    vec3 c = LightColor.rgb * attenuation * spot * shade * shadow * (1.0 - fog) * AccScale;
    fragColor = vec4(c, 1.0);
}
