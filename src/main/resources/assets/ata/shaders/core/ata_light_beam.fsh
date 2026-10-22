#version 150
#moj_import <ata:ata_beam.glsl>
#moj_import <ata:ata_beam_medium.glsl>
uniform sampler2D DepthSampler;
uniform sampler2D BoxSampler;
uniform mat4 InvProj;
uniform vec4 LightPos;
uniform vec4 LightDir;
uniform vec4 LightRight;
uniform vec4 LightColor;
uniform vec4 BeamParams;
uniform vec4 Weather;
in vec2 texCoord;
out vec4 fragColor;
vec3 positionAt(vec2 uv, float depth) {
    vec4 point = InvProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return point.xyz / point.w;
}
vec2 interval(vec3 origin, vec3 direction, vec3 lower, vec3 upper) {
    vec3 safeDirection = mix(vec3(-0.000001), vec3(0.000001), step(vec3(0.0), direction));
    safeDirection = mix(safeDirection, direction, step(vec3(0.000001), abs(direction)));
    vec3 first = (lower - origin) / safeDirection;
    vec3 second = (upper - origin) / safeDirection;
    vec3 near = min(first, second);
    vec3 far = max(first, second);
    return vec2(max(max(near.x, near.y), near.z), min(min(far.x, far.y), far.z));
}
float freeRange(vec3 offset) {
    float forward = dot(offset, LightDir.xyz);
    if (forward <= 0.0) return 0.0;
    vec3 up = cross(LightRight.xyz, LightDir.xyz);
    float az = degrees(atan(dot(offset, LightRight.xyz), forward));
    float el = degrees(atan(dot(offset, up), forward));
    bool high = LightRight.w > 1.5;
    float horizontal = high ? 30.0 : 65.0;
    float bottom = high ? -30.0 : -50.0;
    float top = high ? 30.0 : 5.0;
    if (abs(az) > horizontal || el < bottom || el > top) return 0.0;
    ivec2 cell = ivec2(clamp(vec2((az + horizontal) / (2.0 * horizontal), (el - bottom) / (top - bottom))
        * vec2(16.0, 8.0), vec2(0.0), vec2(15.0, 7.0)));
    int sampleIndex = cell.y * 16 + cell.x;
    int pixelIndex = int(BeamParams.y + 0.5) * 32 + sampleIndex / 4;
    vec4 ranges = texelFetch(BoxSampler, ivec2(pixelIndex % 16, 3 + pixelIndex / 16), 0);
    int channel = sampleIndex % 4;
    return channel == 0 ? ranges.r : channel == 1 ? ranges.g : channel == 2 ? ranges.b : ranges.a;
}
void main() {
    float depth = texture(DepthSampler, texCoord).r;
    vec3 surface = positionAt(texCoord, depth);
    vec3 ray = normalize(surface);
    vec3 up = cross(LightRight.xyz, LightDir.xyz);
    vec3 origin = vec3(dot(-LightPos.xyz, LightRight.xyz), dot(-LightPos.xyz, up), dot(-LightPos.xyz, LightDir.xyz));
    vec3 direction = vec3(dot(ray, LightRight.xyz), dot(ray, up), dot(ray, LightDir.xyz));
    bool high = LightRight.w > 1.5;
    float radius = LightPos.w;
    float width = radius * tan(radians(high ? 30.0 : 65.0));
    vec2 segment = interval(origin, direction, vec3(-width, -radius * tan(radians(high ? 30.0 : 50.0)), 0.0),
        vec3(width, radius * tan(radians(high ? 30.0 : 5.0)), radius));
    float start = max(segment.x, 0.0);
    float end = min(segment.y, depth >= 1.0 ? radius + length(LightPos.xyz) : length(surface));
    if (end <= start) {fragColor = vec4(0.0); return;}
    float sigma = beamDensity(Weather.x, Weather.y, Weather.z, Weather.w);
    float stepLength = (end - start) / 16.0;
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy + BeamParams.z, vec2(0.06711056, 0.00583715))));
    vec3 sum = vec3(0.0);
    for (int index = 0; index < 16; index++) {
        float distance = start + (float(index) + jitter) * stepLength;
        vec3 point = ray * distance;
        vec3 offset = point - LightPos.xyz;
        float lightDistance = length(offset);
        if (lightDistance > freeRange(offset) || lightDistance >= radius) continue;
        float pattern = beamPattern(LightRight.w, offset, LightDir.xyz, LightRight.xyz);
        float irradiance = beamIrradiance(LightColor.w, pattern, lightDistance, radius);
        float cosine = dot(offset / max(lightDistance, 0.0001), -ray);
        sum += LightColor.rgb * irradiance * sigma * beamPhase(cosine) * stepLength * exp(-sigma * distance);
    }
    fragColor = vec4(sum * BeamParams.x, 1.0);
}
