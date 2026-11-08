#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// The spread zone on a block face, like the shadow of a round object: a dark translucent fill,
// denser in the core where most shots land, with a thin light rim at its edge and fainter rings
// inside. discCoord holds the zone's ellipse coordinates (full spread at length 1). vertexColor: r
// is the radius holding half of the shots, g the one holding 90 %, b the parts shown (bits: 1 full
// spread, 2 half ring, 4 wide ring), a the overall opacity.
const vec3 SHADE = vec3(0.0824, 0.0863, 0.0824);   // 151615
const vec3 RIM = vec3(0.9647, 0.9686, 0.9216);      // f6f7eb
const float FILL_ALPHA = 0.42;
const float RIM_ALPHA = 0.9;
const float RING_ALPHA = 0.4;

out vec4 fragColor;
in vec2 discCoord;
in vec4 vertexColor;

void main() {
    float r = length(discCoord);
    float aa = max(fwidth(r), 1.0e-4);
    int parts = int(round(vertexColor.b * 255.0));
    bool full = (parts & 1) != 0;
    bool halfRing = (parts & 2) != 0;
    bool wideRing = (parts & 4) != 0;
    float core = vertexColor.r;
    float wide = vertexColor.g;
    // The zone ends at the widest part that is on.
    float edge = full ? 1.0 : wideRing ? wide : core;
    if (r > edge + 2.5 * aa) {
        discard;
    }
    float inside = 1.0 - smoothstep(edge - aa, edge + aa, r);
    float density = mix(1.0, 0.35, smoothstep(core * 0.5, 1.0, r));
    float fill = FILL_ALPHA * density * inside;
    float rim = 1.0 - smoothstep(0.4 * aa, 1.1 * aa, abs(r - edge));
    float light = rim * RIM_ALPHA;
    if (halfRing) {
        float ring = 1.0 - smoothstep(0.3 * aa, 0.9 * aa, abs(r - core));
        light = max(light, ring * RING_ALPHA);
    }
    if (wideRing) {
        float ring = 1.0 - smoothstep(0.3 * aa, 0.9 * aa, abs(r - wide));
        light = max(light, ring * RING_ALPHA);
    }
    vec3 rgb = mix(SHADE, RIM, light / max(light + fill, 1.0e-4));
    float alpha = (fill + light - fill * light) * vertexColor.a;
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(rgb, alpha) * ColorModulator;
}
