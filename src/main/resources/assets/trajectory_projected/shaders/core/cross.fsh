#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// Hit marker on a living target: a diagonal cross with rounded arm ends, in quad coordinates.
const float ARM = 0.62;
const float HALF_WIDTH = 0.19;

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = discCoord;
    float d = min(segment(p, vec2(-ARM, -ARM), vec2(ARM, ARM)), segment(p, vec2(-ARM, ARM), vec2(ARM, -ARM)));
    float edge = max(fwidth(d), 1.0e-4);
    float coverage = 1.0 - smoothstep(HALF_WIDTH - edge, HALF_WIDTH, d);
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage);
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
