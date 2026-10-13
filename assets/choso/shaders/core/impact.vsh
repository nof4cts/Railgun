#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

out vec2 uv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    uv = UV0;
}
