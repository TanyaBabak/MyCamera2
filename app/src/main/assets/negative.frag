#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;
uniform samplerExternalOES sTexture;
in vec2 TexCoord;// the camera bg texture coordinates
out vec4 FragColor;
uniform vec3 uColor;

void main() {
    vec3 color = texture(sTexture, TexCoord).rgb;
    FragColor = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, 1.0);
}