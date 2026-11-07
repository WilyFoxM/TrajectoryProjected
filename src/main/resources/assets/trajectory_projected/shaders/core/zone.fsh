#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// The spread zone on a block face, like the shadow of a round object: a dark translucent fill,
// denser in the core where most shots land, with a thin light rim at the outer edge and a fainter
// one around the core. discCoord holds the zone's ellipse coordinates (edge at length 1);
// vertexColor.r is the core radius, vertexColor.a the overall opacity.
const vec3 SHADE = vec3(0.0824, 0.0863, 0.0824);   // 151615
const vec3 RIM = vec3(0.9647, 0.9686, 0.9216);      // f6f7eb
const float FILL_ALPHA = 0.42;
const float RIM_ALPHA = 0.9;
const float CORE_RIM_ALPHA = 0.4;

out vec4 fragColor;
in vec2 discCoord;
in vec4 vertexColor;

void main() {
    float r = length(discCoord);
    float aa = max(fwidth(r), 1.0e-4);
    if (r > 1.0 + 2.5 * aa) {
        discard;
    }
    float core = vertexColor.r;
    float inside = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, r);
    float density = mix(1.0, 0.35, smoothstep(core * 0.5, 1.0, r));
    float fill = FILL_ALPHA * density * inside;
    float rim = 1.0 - smoothstep(0.4 * aa, 1.1 * aa, abs(r - 1.0));
    float coreRim = 1.0 - smoothstep(0.3 * aa, 0.9 * aa, abs(r - core));
    float light = max(rim * RIM_ALPHA, coreRim * CORE_RIM_ALPHA);
    vec3 rgb = mix(SHADE, RIM, light / max(light + fill, 1.0e-4));
    float alpha = (fill + light - fill * light) * vertexColor.a;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
