#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 objPos;
out vec3 viewN;
out vec3 viewP;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    objPos = Position;
    viewN = mat3(ModelViewMat) * Normal;
    viewP = vp.xyz;
}
