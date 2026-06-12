/* Sharp-bilinear scaling shader.
 *
 * Prescales as close to integer as possible, then applies bilinear
 * for the final non-integer stretch. Keeps pixels sharp while
 * reducing shimmer at non-integer scale factors.
 *
 * Uniforms:
 *   sharpness   — blend between nearest (1.0) and bilinear (0.0)
 *   sourceSize  — game's internal resolution (e.g. 640x480)
 *   outputSize  — rendered output resolution
 */

uniform sampler2D texture;

varying v_texCoord;

uniform float sharpness;
uniform vec2 sourceSize;
uniform vec2 outputSize;

void main()
{
	vec2 texCoord = v_texCoord;

	// Calculate the scale factor
	vec2 scale = outputSize / sourceSize;

	// Find the nearest integer scale
	vec2 intScale = max(floor(scale), vec2(1.0));

	// Calculate the size of a texel in the prescaled space
	vec2 texelSize = 1.0 / sourceSize;

	// In the prescaled region, use nearest neighbor
	// In the final stretch region, use bilinear with sharpness bias
	vec2 texel = texCoord * sourceSize;
	vec2 nearestTexel = floor(texel) + 0.5;
	vec2 bilinearOffset = fract(texel);

	// Sharpness: 0.0 = pure bilinear, 1.0 = nearest with slight bilinear blend
	vec2 sharpOffset = mix(bilinearOffset, round(bilinearOffset), sharpness);

	// Final coordinate
	vec2 finalCoord = (nearestTexel + sharpOffset - bilinearOffset + bilinearOffset) / sourceSize;

	// Clamp to valid range
	finalCoord = clamp(finalCoord, texelSize * 0.5, vec2(1.0) - texelSize * 0.5);

	gl_FragColor = texture2D(texture, finalCoord);
}
