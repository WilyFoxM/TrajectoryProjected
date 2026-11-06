#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// A disc of radius 1 in quad coordinates, with a one-pixel soft edge.
void main() {
    float d = length(discCoord);
    float edge = max(fwidth(d), 1.0e-4);
    float coverage = 1.0 - smoothstep(1.0 - edge, 1.0, d);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage);
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
