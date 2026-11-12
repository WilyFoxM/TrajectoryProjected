#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// The ghost of a fishing bobber where the hook will land: a round float with a short stem on top,
// in quad coordinates (y up). The upper half is solid and the lower half lighter, like the two
// colours of the real bobber, in the line's colour.
const vec2 BODY = vec2(0.0, -0.18);
const float BODY_RADIUS = 0.46;
const float STEM_WIDTH = 0.075;
const float LOWER_ALPHA = 0.55;

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = discCoord;
    float body = length(p - BODY) - BODY_RADIUS;
    float stem = segment(p, vec2(0.0, 0.2), vec2(0.0, 0.82)) - STEM_WIDTH;
    float d = min(body, stem);
    float edge = max(fwidth(d), 1.0e-4);
    float coverage = 1.0 - smoothstep(-edge, 0.0, d);
    float upper = smoothstep(-edge, edge, p.y - BODY.y);
    float alpha = vertexColor.a * coverage * mix(LOWER_ALPHA, 1.0, upper);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
