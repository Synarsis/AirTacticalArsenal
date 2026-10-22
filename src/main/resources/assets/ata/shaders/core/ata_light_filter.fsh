#version 150
uniform sampler2D SourceSampler;
uniform vec2 PixelSize;
uniform float Upsample;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 pixel = PixelSize;
    vec3 result;
    if (Upsample < 0.5) {
        result = texture(SourceSampler, texCoord).rgb * 4.0;
        result += texture(SourceSampler, texCoord + pixel * vec2(-1.0, -1.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(1.0, -1.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(-1.0, 1.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(1.0, 1.0)).rgb;
        result /= 8.0;
    } else {
        result = texture(SourceSampler, texCoord + pixel * vec2(-2.0, 0.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(-1.0, 1.0)).rgb * 2.0;
        result += texture(SourceSampler, texCoord + pixel * vec2(0.0, 2.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(1.0, 1.0)).rgb * 2.0;
        result += texture(SourceSampler, texCoord + pixel * vec2(2.0, 0.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(1.0, -1.0)).rgb * 2.0;
        result += texture(SourceSampler, texCoord + pixel * vec2(0.0, -2.0)).rgb;
        result += texture(SourceSampler, texCoord + pixel * vec2(-1.0, -1.0)).rgb * 2.0;
        result /= 12.0;
    }
    fragColor = vec4(result, 1.0);
}
