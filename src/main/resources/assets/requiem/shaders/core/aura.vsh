#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec3 vNormalWorld;
out vec3 v_viewDir;
out vec2 v_UV;

uniform mat4 ModelViewMat;
uniform mat4 viewMatrix;
uniform mat4 ProjMat;
uniform mat3 normalMatrix;

uniform vec3 view;
uniform vec3 sizes;
uniform float time;
uniform float auraPhase;
uniform float flyboost;
uniform float auravar;

uniform float freqY;
uniform float speed;
uniform float amp;
uniform float fineDetail;
uniform float upDir;

uniform sampler2D NoiseTex;

float triangleWave(float x) {
    return abs(fract(x/1.0) * 2.0 - 1.0);
}

vec3 rotateX(vec3 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c);
}

void main() {
    float flipAngle = flyboost * 3.14159265;
    vec3 basePos = rotateX(Position, flipAngle);

    vec4 pos = vec4(basePos, 1.0);
    pos.xyz *= sizes.xyz;

    float localY = basePos.y;

    float falloff2 = smoothstep(-1.0, -0.8, localY) * (1.0 - smoothstep(0.0, 5.0, localY));
    float posX = basePos.x == 0.0 ? 0.00001 : basePos.x;
    float posZ = basePos.z == 0.0 ? 0.00001 : basePos.z;
    float numPeaks = 12.0; 
    float angle = atan(posZ, posX);
    float angularWave = triangleWave((angle * numPeaks) / 6.2831853);
    
    vec2 noiseUV = vec2((angle / 6.2831853) * 1.1, localY * 1.1);
    noiseUV.y -= (time * 1.0); 

    float noiseValue = texture(NoiseTex, noiseUV).r * 1.5 - 0.5; 
    
    float layer = triangleWave((localY * freqY) - auraPhase);
    float totalWave = (layer * angularWave) + (noiseValue * fineDetail * 1.2) * 0.9;
    float falloff = smoothstep(-1.5, -0.2, localY) * (1.0 - smoothstep(0.0, 2.3, localY));

    vec3 dir = normalize(pos.xyz);
    vec3 upwardDir = mix(vec3(0.0, 1.0, 0.0), vec3(0.0, -1.0, 0.0), flyboost);
    vec3 finalDir = normalize(mix(dir, upwardDir, mix(upDir, upDir * 0.9, flyboost)));
    
    float smoothAura = auravar * auravar * (3.0 - 2.0 * auravar);
    pos.xz *= smoothAura;
    pos.xyz += finalDir * (totalWave * amp * falloff) * pow(auravar, 6.0);
    
    v_UV = UV0;
    
    vec4 worldPos = ModelViewMat * vec4(Position, 1.0);
    vec4 viewPos = ModelViewMat * vec4(pos.xyz, 1.0);
    
    vNormalWorld = normalize(normalMatrix * pos.xyz);
    v_viewDir = -viewPos.xyz;
    vec4 clipPos = ProjMat * worldPos;
    
    gl_Position = ProjMat * viewPos;
}