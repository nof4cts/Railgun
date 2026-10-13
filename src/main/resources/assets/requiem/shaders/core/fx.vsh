#version 150
// Requiem Rose world effects: positions arrive camera-relative; colour carries per-shape parameters.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vColor;
out vec2 vUV;
out float vDepth;
void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * v;
    vColor = Color;
    vUV = UV0;
    vDepth = -v.z;
}
