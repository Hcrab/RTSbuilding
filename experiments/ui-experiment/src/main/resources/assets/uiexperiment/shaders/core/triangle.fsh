#version 150

uniform vec2 u_TriA;
uniform vec2 u_TriB;
uniform vec2 u_TriC;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

float sdTriangle(vec2 p, vec2 p0, vec2 p1, vec2 p2) {
    vec2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
    vec2 v0 = p - p0, v1 = p - p1, v2 = p - p2;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = e0.x * e2.y - e0.y * e2.x;
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

void main() {
    float sdf = sdTriangle(texCoord0, u_TriA, u_TriB, u_TriC);
    float alpha = 1.0 - smoothstep(-1.0, 1.0, sdf / fwidth(sdf));
    fragColor = vertexColor * alpha;
}
