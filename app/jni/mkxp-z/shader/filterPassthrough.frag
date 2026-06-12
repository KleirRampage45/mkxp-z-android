/* Passthrough fragment shader — identity filter.
 * Used when filters are disabled or as a fallback.
 */

uniform sampler2D texture;

varying vec2 v_texCoord;

void main()
{
	gl_FragColor = texture2D(texture, v_texCoord);
}
