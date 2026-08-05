#version 150

uniform sampler2D Sampler0;
uniform vec2 u_Size;
uniform vec4 u_TexBounds;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = (texCoord0 / u_Size + 1.0) * 0.5;
    uv = u_TexBounds.xy + uv * (u_TexBounds.zw - u_TexBounds.xy);
    vec4 texColor = texture(Sampler0, uv);
    fragColor = texColor * vertexColor;
}
