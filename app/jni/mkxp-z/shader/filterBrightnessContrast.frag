/* Brightness, contrast, gamma, and saturation adjustment.
 *
 * Uniforms:
 *   brightness  — additive offset (0.0 = no change)
 *   contrast    — multiplicative around 0.5 (1.0 = no change)
 *   gamma       — gamma curve (1.0 = no change)
 *   saturation  — color saturation (1.0 = no change, 0.0 = grayscale)
 */

uniform sampler2D texture;

varying vec2 v_texCoord;

uniform float brightness;
uniform float contrast;
uniform float gamma;
uniform float saturation;

const vec3 grayscale = vec3(0.2126, 0.7152, 0.0722);

void main()
{
	vec4 color = texture2D(texture, v_texCoord);

	// Brightness
	color.rgb += brightness;

	// Contrast (around mid-gray)
	color.rgb = (color.rgb - 0.5) * contrast + 0.5;

	// Gamma
	color.rgb = pow(max(color.rgb, vec3(0.0)), vec3(1.0 / gamma));

	// Saturation
	float lum = dot(color.rgb, grayscale);
	color.rgb = mix(vec3(lum), color.rgb, saturation);

	gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a);
}
