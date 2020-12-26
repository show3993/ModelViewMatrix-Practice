#version 300 es

layout(location = 0) uniform mat4 uMVPMatrix;
layout(location = 0) in vec4 vPosition;
layout(location = 1) in vec4 vColor;
out vec4 v_Color;

void main()
{
    gl_Position = uMVPMatrix * vPosition;
    v_Color = vColor;
}