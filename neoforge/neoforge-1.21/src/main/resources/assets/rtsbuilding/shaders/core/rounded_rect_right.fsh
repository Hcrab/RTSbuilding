#version 150

uniform vec2 u_Size;
uniform float u_Radius;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 halfSize = u_Size;
    vec2 pos = texCoord0;
    float dx = abs(pos.x) - halfSize.x;
    float dy = abs(pos.y) - halfSize.y;

    float radius = (pos.x > 0.0) ? u_Radius : 0.0;

    float sdf;
    if (radius > 0.0) {
        vec2 d = vec2(dx + radius, dy + radius);
        sdf = length(max(d, 0.0)) - radius;
    } else {
        sdf = max(dx, dy);
    }

    float alpha = 1.0 - smoothstep(-1.0, 1.0, sdf / length(vec2(dFdx(sdf), dFdy(sdf))));
    fragColor = vertexColor * alpha;
}
