#version 150

in vec3 Position;

uniform vec4 Rect;

out vec2 local;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    local = (Position.xy - Rect.xy) / max(Rect.zw, vec2(1.0e-5));
}
