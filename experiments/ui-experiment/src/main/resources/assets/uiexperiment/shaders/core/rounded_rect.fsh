#version 150

uniform vec2 u_Size;
uniform float u_Radius;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 halfSize = u_Size;
    vec2 d = abs(texCoord0) - halfSize + u_Radius;
    float sdf = length(max(d, 0.0)) - u_Radius;
    float alpha = 1.0 - smoothstep(-1.0, 1.0, sdf / fwidth(sdf));
    fragColor = vertexColor * alpha;
}
