#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// The ghost bobber seen through terrain: a fainter core inside a dark outline (151615), like
// hidden dots.
const vec3 OUTLINE = vec3(0.0824, 0.0863, 0.0824);
const vec2 BODY = vec2(0.0, -0.18);
const float BODY_RADIUS = 0.46;
const float STEM_WIDTH = 0.075;
const float OUTLINE_WIDTH = 0.12;
const float CORE_ALPHA = 0.45;
const float OUTLINE_ALPHA = 0.9;

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
    float shape = 1.0 - smoothstep(OUTLINE_WIDTH - edge, OUTLINE_WIDTH, d);
    float core = 1.0 - smoothstep(-edge, 0.0, d);
    vec3 rgb = mix(OUTLINE, vertexColor.rgb, core);
    float alpha = vertexColor.a * shape * mix(OUTLINE_ALPHA, CORE_ALPHA, core);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
