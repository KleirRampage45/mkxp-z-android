/* Unsharp mask sharpening shader.
 *
 * Applies a simple 3x3 sharpening kernel to improve text clarity.
 *
 * Uniforms:
 *   strength  — sharpening intensity (0.0 = off, 0.15 = mild)
 *   texelSize — 1.0 / texture resolution
 */

uniform sampler2D texture;

varying vec2 v_texCoord;

uniform float strength;
uniform vec2 texelSize;

void main()
{
	vec4 center = texture2D(texture, v_texCoord);

	if (strength <= 0.0) {
		gl_FragColor = center;
		return;
	}

	// Sample 4 neighbors (cross pattern)
	vec4 top    = texture2D(texture, v_texCoord + vec2(0.0, texelSize.y));
	vec4 bottom = texture2D(texture, v_texCoord - vec2(0.0, texelSize.y));
	vec4 left   = texture2D(texture, v_texCoord - vec2(texelSize.x, 0.0));
	vec4 right  = texture2D(texture, v_texCoord + vec2(texelSize.x, 0.0));

	// Unsharp mask: original + strength * (original - blurred)
	vec4 blurred = (top + bottom + left + right) * 0.25;
	vec4 sharp = center + (center - blurred) * strength * 4.0;

	gl_FragColor = vec4(clamp(sharp.rgb, 0.0, 1.0), center.a);
}
