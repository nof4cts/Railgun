#version 150
#extension GL_ARB_explicit_attrib_location : enable

in vec3 vNormalWorld;
in vec3 v_viewDir;
in vec2 v_UV;

uniform vec3 color1;
uniform vec3 color2;
uniform float alp1;
uniform float alp2;
uniform float power;
uniform float divis;
uniform vec3 bloomColor;

uniform float time;
uniform float speed;
uniform sampler2D NoiseTex;

uniform vec3 colorNoise;
uniform float noiseFactor;

uniform float fakeBloom;
uniform float realBloom;
uniform float noiseBloom;

layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outBloomColor;
layout(location = 2) out vec4 outBloomControl;

void main(void) {
    vec3 N = normalize(vNormalWorld);
    vec3 V = normalize(v_viewDir);

    float facingRaw = dot(V, N);
    if (facingRaw < 0.0) {
        N = -N;
    }
    
    float facingAbs = abs(dot(V, N));
    float edgeFactor = pow(1.0 - facingAbs, power) / divis;
    float blendFactor = clamp(edgeFactor, 0.0, 1.0); 

    vec3 colorNoNoise = mix(color1, color2, blendFactor);
    float alphaNoNoise = mix(alp1, alp2, blendFactor);

    vec2 proceduralUV = vec2(vNormalWorld.x * 0.5 + 0.5, vNormalWorld.y * 0.5 + 0.5);
    vec2 scaledUV = vec2(proceduralUV.x * 0.5, proceduralUV.y * 0.25);
    vec2 scrollingUV = vec2(scaledUV.x, scaledUV.y - (time * 1 * 0.25));
    
    float noiseValue = texture(NoiseTex, scrollingUV).r;

    vec3 color3 = colorNoNoise * mix(0.8, 1.0, noiseValue);
    vec3 colorWithNoise = mix(colorNoise, color3, noiseValue);
    
    float alphaWithNoise = clamp(alphaNoNoise * 1.0, 0.0, 1.0);

    vec3 finalColor = mix(colorNoNoise, colorWithNoise, noiseFactor);
    float finalAlpha = mix(alphaNoNoise, alphaWithNoise, noiseFactor);
    
    if (facingRaw < 0.0) {
        finalAlpha = 0.01;
    }

    //fragData[0] = vec4(finalColor, finalAlpha);
    //fragData[1] = vec4(finalColor, finalAlpha);
	//fragData[1] = vec4(vec3(1.0, 0.0, 0.0), finalAlpha);
	//fragData[2] = vec4(2.0, 2.0, 0.0, finalAlpha);
	outColor = vec4(finalColor, finalAlpha);
    outBloomColor = vec4(finalColor, finalAlpha);
    outBloomControl = vec4(fakeBloom, realBloom, noiseBloom, 1.0);
}