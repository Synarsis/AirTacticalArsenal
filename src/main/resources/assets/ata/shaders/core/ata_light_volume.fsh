#version 150

#moj_import <ata:ata_beam.glsl>

uniform sampler2D DepthSampler;
uniform sampler2D OccupancySampler;
uniform sampler2D BoxSampler;

uniform mat4 InvProj;
uniform mat4 Proj;
uniform vec4 LightPos;
uniform vec4 LightColor;
uniform vec4 LightDir;
uniform vec4 LightRight;
uniform vec4 LightCone;
uniform vec2 DepthSize;
uniform vec2 FogRange;
uniform float AccScale;
uniform mat4 InvView;
uniform vec4 LightWorld;
uniform vec4 Shadow;
uniform vec4 OccOrigin;
uniform vec4 OccMod;
uniform vec4 Boxes;
uniform vec4 Scatter;
uniform vec2 WetRoad;

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

const int MAX_BOXES = 16;
const float TINY = 1.0e-6;

vec3 boxLocal(vec3 w, vec3 centre, vec2 cs) {
    vec3 r = w - centre;
    return vec3(r.x * cs.x + r.z * cs.y, r.y, cs.x * r.z - cs.y * r.x);
}

vec2 slab(vec3 o, vec3 d, vec3 h) {
    vec3 inv = 1.0 / d;
    vec3 t0 = (-h - o) * inv;
    vec3 t1 = (h - o) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float tn = max(max(tmin.x, tmin.y), max(tmin.z, 0.0));
    float tf = min(min(tmax.x, tmax.y), min(tmax.z, 1.0));
    return vec2(tn, tf);
}

float boxTransmittance(vec3 from, vec3 to, vec3 centre, vec3 half, vec2 cs) {
    vec3 a = boxLocal(from, centre, cs);
    vec3 b = boxLocal(to, centre, cs);
    vec3 d = b - a;
    d.x = abs(d.x) < TINY ? TINY : d.x;
    d.y = abs(d.y) < TINY ? TINY : d.y;
    d.z = abs(d.z) < TINY ? TINY : d.z;
    vec2 outer = slab(a, d, half + vec3(Boxes.w));
    if (outer.x > outer.y) {
        return 1.0;
    }
    vec2 inner = slab(a, d, half);
    if (inner.x <= inner.y) {
        return 0.0;
    }
    vec3 mid = a + d * (0.5 * (outer.x + outer.y));
    vec3 q = abs(mid) - half;
    float sdf = length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
    return smoothstep(0.0, Boxes.w, sdf);
}

bool boxMayTouch(vec3 from, vec3 to, vec3 centre, vec3 half) {
    vec3 seg = to - from;
    float len2 = dot(seg, seg);
    float t = len2 < 1.0e-12 ? 0.0 : clamp(dot(centre - from, seg) / len2, 0.0, 1.0);
    vec3 off = from + seg * t - centre;
    float reach = length(half) + Boxes.w;
    return dot(off, off) <= reach * reach;
}

float boxShadow(vec3 from, vec3 to) {
    float transmitted = 1.0;
    int n = int(Boxes.x + 0.5);
    for (int i = 0; i < MAX_BOXES; i++) {
        if (i >= n) {
            break;
        }
        vec4 a = texelFetch(BoxSampler, ivec2(i, 0), 0);
        vec4 b = texelFetch(BoxSampler, ivec2(i, 1), 0);
        vec3 end = to;
        if (abs(a.w - Boxes.y) < 0.5) {
            vec3 delta = to - from;
            float len = length(delta);
            end = len <= Boxes.z ? from : from + delta * ((len - Boxes.z) / len);
        }
        if (!boxMayTouch(from, end, a.xyz, b.xyz)) {
            continue;
        }
        vec4 c = texelFetch(BoxSampler, ivec2(i, 2), 0);
        transmitted *= boxTransmittance(from, end, a.xyz, b.xyz, c.xy);
        if (transmitted < SHUT) {
            return 0.0;
        }
    }
    return transmitted;
}

float rayShadow(vec3 from, vec3 to) {
    float s = 1.0;
    if (Shadow.x > 0.5) {
        s = shadowRay(from, to);
    }
    if (s > SHUT && Boxes.x > 0.5) {
        s *= boxShadow(from, to);
    }
    return s;
}

float noiseAt(vec2 frag) {
    return fract(52.9829189 * fract(dot(frag, vec2(0.06711056, 0.00583715))));
}

float blockShadow(vec3 p, vec3 n) {
    vec3 world = (InvView * vec4(p, 1.0)).xyz;
    vec3 worldNormal = mat3(InvView) * n;
    vec3 from = world + snapToAxis(worldNormal) * NORMAL_BIAS - OccOrigin.xyz;
    vec3 light = LightWorld.xyz - OccOrigin.xyz;
    int count = int(Shadow.y + 0.5);
    if (count < 2 || Shadow.z <= 0.0) {
        return rayShadow(from, light);
    }
    vec3 way = normalize(light - from);
    vec3 up = abs(way.y) > 0.95 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
    vec3 across = normalize(cross(way, up));
    vec3 lift = cross(way, across);
    float turn = noiseAt(gl_FragCoord.xy);
    float sum = 0.0;
    for (int i = 0; i < 4; i++) {
        if (i >= count) {
            break;
        }
        float angle = 6.2831853 * (turn + float(i) / float(count));
        float r = Shadow.z * sqrt((float(i) + 0.5) / float(count));
        sum += rayShadow(from, light + across * (cos(angle) * r) + lift * (sin(angle) * r));
    }
    return sum / float(count);
}

float wetReflection(vec3 point, vec3 normal, vec3 light, float irradiance) {
    if (WetRoad.x <= 0.0 || WetRoad.y < 0.5 || LightRight.w < 0.5) return 0.0;
    vec3 worldNormal = mat3(InvView) * normal;
    if (worldNormal.y <= 0.7) return 0.0;
    vec3 world = (InvView * vec4(point, 1.0)).xyz - OccOrigin.xyz;
    ivec3 cell = ivec3(floor(world + vec3(0.0, 0.5, 0.0)));
    for (int index = 0; index < 6; index++) {
        if (occupancyAt(cell + ivec3(0, index, 0)) > 0.0) return 0.0;
    }
    vec3 view = normalize(-point);
    vec3 halfVector = normalize(view + light);
    float fresnel = 0.02 + 0.98 * pow(1.0 - clamp(dot(view, halfVector), 0.0, 1.0), 5.0);
    return 0.6 * WetRoad.x * irradiance * pow(max(dot(normal, halfVector), 0.0), 60.0) * fresnel;
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
    float axisDot = 1.0;
    if (LightCone.y > 0.5) {
        axisDot = dot(-l, LightDir.xyz);
        float inside = smoothstep(LightDir.w, LightCone.x, axisDot);
        inside *= inside;
        float spill = Scatter.y * smoothstep(Scatter.z, LightDir.w, axisDot);
        spot = inside + spill * (1.0 - inside);
    }
    float rf = radius * Scatter.w;
    float xf = d / rf;
    float windowFill = clamp(1.0 - xf * xf * xf * xf, 0.0, 1.0);
    float kf = d / (0.25 * rf);
    float front = LightCone.y > 0.5 ? smoothstep(0.0, 0.2, axisDot) : 1.0;
    float fill = Scatter.x * windowFill * windowFill / (1.0 + kf * kf) * front;

    if (LightRight.w > 0.5) {
        spot = beamPattern(LightRight.w, -toL, LightDir.xyz, LightRight.xyz);
        attenuation = beamIrradiance(1.0, 1.0, d, radius);
        fill = 0.07 * attenuation * spot;
    }

    vec3 n = vec3(0.0, 0.0, 1.0);
    if (LightCone.w > 0.5 || Shadow.x > 0.5) {
        n = surfaceNormal(texCoord, p);
    }
    float shade = 0.6;
    if (LightCone.w > 0.5) {
        shade = LightColor.w + (1.0 - LightColor.w) * max(dot(n, l), 0.0);
    }

    if (LightRight.w > 0.5) shade = max(dot(n, l), 0.0);
    float base = attenuation * spot * shade;
    if (LightRight.w < 0.5 && (base + fill) * max(max(LightColor.r, LightColor.g), LightColor.b) * AccScale < 0.0005) {
        fragColor = vec4(0.0);
        return;
    }

    float shadow = 1.0;
    if (Shadow.x > 0.5 || Boxes.x > 0.5) {
        shadow = blockShadow(p, n);
    }
    if (LightCone.z > 0.5) {
        shadow *= contactShadow(p, l, d);
    }

    float fog = clamp((length(p) - FogRange.x) / max(FogRange.y - FogRange.x, 1.0e-3), 0.0, 1.0);

    vec3 c = LightColor.rgb * (attenuation * spot * shade * shadow + fill) * (1.0 - fog) * AccScale;
    float reflection = wetReflection(p, n, l, attenuation * spot) * LightColor.r * shadow * (1.0 - fog) * AccScale;
    fragColor = vec4(c, reflection);
}
