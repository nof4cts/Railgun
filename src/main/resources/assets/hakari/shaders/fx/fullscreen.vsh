#version 150
// One oversized triangle covering the screen (gl_VertexID 0..2), no vertex buffer needed.
out vec2 uv;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    uv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
