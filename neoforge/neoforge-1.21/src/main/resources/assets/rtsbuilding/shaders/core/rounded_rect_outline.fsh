#version 150

uniform vec2 u_Size;
uniform float u_Radius;
uniform float u_Thickness;
uniform vec4 u_FillColor;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 halfSize = u_Size;
    vec2 d = abs(texCoord0) - halfSize + u_Radius;
    float sdf = length(max(d, 0.0)) - u_Radius;

    float outlineSdf = abs(sdf + u_Thickness * 0.5) - u_Thickness * 0.5;
    vec2 od = vec2(dFdx(outlineSdf), dFdy(outlineSdf));
    float outlineAlpha = 1.0 - smoothstep(-1.0, 1.0, outlineSdf / length(od));

    vec2 fd = vec2(dFdx(sdf), dFdy(sdf));
    float outsideAlpha = smoothstep(0.0, length(fd), sdf + u_Thickness);

    vec4 outsideColor = u_FillColor * outsideAlpha;
    vec4 outlineColor = vertexColor * outlineAlpha;
    fragColor = outsideColor + outlineColor;
}
