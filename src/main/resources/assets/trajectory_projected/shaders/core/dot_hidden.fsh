#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 discCoord;
in vec4 vertexColor;

out vec4 fragColor;

// A dot seen through terrain: a fainter core inside a dark outline, so the dotted line stays
// readable on any surface in front of it. The outline is 060410 from the palette.
const vec3 OUTLINE = vec3(0.0235, 0.0157, 0.0627);
const float CORE = 0.6;
const float CORE_ALPHA = 0.45;
const float OUTLINE_ALPHA = 0.8;

void main() {
    float d = length(discCoord);
    float edge = max(fwidth(d), 1.0e-4);
    float disc = 1.0 - smoothstep(1.0 - edge, 1.0, d);
    float core = 1.0 - smoothstep(CORE - edge, CORE, d);
    vec3 rgb = mix(OUTLINE, vertexColor.rgb, core);
    float alpha = vertexColor.a * disc * mix(OUTLINE_ALPHA, CORE_ALPHA, core);
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
