#version 150

uniform vec2 u_Size;
uniform float u_Radius;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 halfSize = u_Size;
    vec2 d = abs(texCoord0) - halfSize + u_Radius;
    // IQ 精确圆角矩形 SDF：min 项保证内部梯度连续，radius=0 时退化为普通矩形
    float sdf = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - u_Radius;
    // 梯度归一化抗锯齿：|grad(sdf)| = 1，length 精确等于每像素变化量，
    // 直线与圆弧处过渡带宽度一致，消除衔接处毛刺
    vec2 grad = vec2(dFdx(sdf), dFdy(sdf));
    float alpha = 1.0 - smoothstep(-1.0, 1.0, sdf / length(grad));
    fragColor = vertexColor * alpha;
}
