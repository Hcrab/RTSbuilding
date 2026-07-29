#version 150

uniform vec2 u_Size;
uniform float u_Radius;
uniform float u_Thickness;
uniform float u_Gap;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float radius = u_Radius;
    float thick = u_Thickness;
    float gapRad = radians(u_Gap);

    float ring = abs(length(texCoord0) - radius) - thick * 0.5;
    float angle = atan(texCoord0.y, texCoord0.x);
    float gapCenter = -3.316;
    float diff = abs(angle - gapCenter);
    diff = min(diff, 6.28318 - diff);
    float angularSdf = diff - gapRad * 0.5;
    float sdf = max(ring, -angularSdf * radius);

    float alpha = 1.0 - smoothstep(-1.0, 1.0, sdf / fwidth(sdf));
    fragColor = vertexColor * alpha;
}
