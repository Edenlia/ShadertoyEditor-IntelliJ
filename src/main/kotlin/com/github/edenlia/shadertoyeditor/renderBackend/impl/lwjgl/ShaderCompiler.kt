package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import org.lwjgl.opengl.GL20.*

/**
 * Shader编译器
 * 
 * 负责：
 * - 编译vertex和fragment shader
 * - 链接shader program
 * - 错误检查和报告
 */
class ShaderCompiler {
    
    companion object {
        /**
         * Vertex Shader - 用于绘制fullscreen quad
         * 将屏幕空间坐标转换为片段坐标
         */
        private val VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()
    }
    
    /**
     * 编译完整的shader program
     * 
     * @param fragmentShaderSource Fragment shader源代码（用户提供的Shadertoy代码）
     * @return Shader program ID
     * @throws ShaderCompilationException 如果编译或链接失败
     */
    fun compile(fragmentShaderSource: String): Int {
        println("[LWJGL] Compiling shader program...")
        
        try {
            // 1. 编译vertex shader
            val vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER)
            
            // 2. 编译fragment shader
            val fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource)
            
            // 3. 链接program
            val program = glCreateProgram()
            glAttachShader(program, vertexShader)
            glAttachShader(program, fragmentShader)
            glLinkProgram(program)
            
            // 4. 检查链接状态
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                val log = glGetProgramInfoLog(program)
                glDeleteProgram(program)
                glDeleteShader(vertexShader)
                glDeleteShader(fragmentShader)
                throw ShaderCompilationException("Shader linking failed:\n$log")
            }
            
            // 5. 清理shader对象（program已经包含编译后的代码）
            glDeleteShader(vertexShader)
            glDeleteShader(fragmentShader)
            
            println("[LWJGL] Shader program compiled successfully (ID: $program)")
            return program
            
        } catch (e: ShaderCompilationException) {
            println("[LWJGL] Shader compilation failed: ${e.message}")
            throw e
        }
    }
    
    /**
     * 编译单个shader
     * 
     * @param type Shader类型 (GL_VERTEX_SHADER 或 GL_FRAGMENT_SHADER)
     * @param source Shader源代码
     * @return Shader ID
     * @throws ShaderCompilationException 如果编译失败
     */
    private fun compileShader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)
        
        // 检查编译状态
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            val log = glGetShaderInfoLog(shader)
            glDeleteShader(shader)
            
            val typeName = when (type) {
                GL_VERTEX_SHADER -> "Vertex"
                GL_FRAGMENT_SHADER -> "Fragment"
                else -> "Unknown"
            }
            
            throw ShaderCompilationException("$typeName shader compilation failed:\n$log")
        }
        
        return shader
    }
}

/**
 * Shader编译异常
 */
class ShaderCompilationException(message: String) : Exception(message)

