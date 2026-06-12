/*
** filter_chain.h
**
** Visual filter post-processing chain for Runestone.
** Reads filter config JSON and executes shader passes on the game's output.
*/

#ifndef FILTER_CHAIN_H
#define FILTER_CHAIN_H

#include "gl-util.h"
#include "gl-fun.h"
#include "util/json5pp.hpp"

#include <string>
#include <vector>
#include <map>

struct FilterPassConfig {
    std::string shader;
    std::map<std::string, float> params;
};

struct FilterConfig {
    bool enabled;
    std::string preset;
    std::string aspectMode;
    std::vector<FilterPassConfig> passes;
};

class FilterShader {
public:
    FilterShader();
    ~FilterShader();

    bool compile(const char *vertSrc, int vertLen,
                 const char *fragSrc, int fragLen,
                 const char *name);

    void bind();
    void setUniform(const char *name, float value);
    void setUniform(const char *name, float x, float y);
    void setUniform(const char *name, int value);
    void applyViewportProj(int width, int height);

    GLuint program;

private:
    GLuint vertShader, fragShader;
    std::map<std::string, GLint> uniformCache;

    GLint getUniformLoc(const char *name);
};

class FilterChain {
public:
    FilterChain();
    ~FilterChain();

    // Load/reload filter config from JSON file
    bool loadConfig(const char *path);

    // Execute the filter chain on the given source texture.
    // Returns the TEXFBO containing the filtered output.
    // sourceSize = game's internal resolution
    // outputSize = screen/viewport size
    TEXFBO &execute(TEXFBO &source, int sourceW, int sourceH,
                    int outputW, int outputH);

    bool isEnabled() const { return config.enabled && !config.passes.empty(); }
    const FilterConfig &getConfig() const { return config; }

    // Check if config file has been modified since last load
    bool checkForUpdate(const char *path);

private:
    FilterConfig config;
    std::map<std::string, FilterShader> shaders;
    TEXFBO pingPong[2];
    bool initialized;
    int bufferW, bufferH;
    long lastModTime;

    // Fullscreen quad geometry
    GLuint quadVBO;

    void ensureBuffers(int w, int h);
    void initQuad();
    void drawQuad();
    bool compileShader(FilterShader &shader, const char *name);
    FilterConfig parseConfig(const json5pp::value &json);
};

#endif // FILTER_CHAIN_H
