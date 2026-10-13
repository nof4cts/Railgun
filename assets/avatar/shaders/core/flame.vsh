#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUV;
out vec4 vColor;
out vec3 vAux;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUV = UV0;
    vColor = Color;
    vAux = Normal;
}
