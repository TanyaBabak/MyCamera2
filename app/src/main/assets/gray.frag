#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;
uniform samplerExternalOES sTexture;
in vec2 TexCoord;// the camera bg texture coordinates
out vec4 FragColor;
uniform vec3 uColor;

void main() {
    vec3 color = texture(sTexture, TexCoord).rgb * uColor;
    float gray = (color.r + color.g + color.b) / 3.0;
    FragColor = vec4(gray, gray, gray, 1.0);
}
