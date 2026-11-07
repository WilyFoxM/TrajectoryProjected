#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// The cross seen through terrain: a fainter core inside a dark outline (060410), like hidden dots.
const vec3 OUTLINE = vec3(0.0235, 0.0157, 0.0627);
const float ARM = 0.62;
const float OUTER = 0.34;
const float INNER = 0.11;
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
    float d = min(segment(p, vec2(-ARM, -ARM), vec2(ARM, ARM)), segment(p, vec2(-ARM, ARM), vec2(ARM, -ARM)));
    float edge = max(fwidth(d), 1.0e-4);
    float shape = 1.0 - smoothstep(OUTER - edge, OUTER, d);
    float core = 1.0 - smoothstep(INNER - edge, INNER, d);
    vec3 rgb = mix(OUTLINE, vertexColor.rgb, core);
    float alpha = vertexColor.a * shape * mix(OUTLINE_ALPHA, CORE_ALPHA, core);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
