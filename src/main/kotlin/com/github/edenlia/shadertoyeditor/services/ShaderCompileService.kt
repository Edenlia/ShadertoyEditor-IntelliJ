package com.github.edenlia.shadertoyeditor.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Shader编译服务
 * 负责读取GLSL文件并包装成完整的Fragment Shader
 */
@Service(Service.Level.PROJECT)
class ShaderCompileService(private val project: Project) {
    
    /**
     * 读取shader模板文件并包装成完整的Fragment Shader
     * 
     * @return 完整的Fragment Shader源代码
     */
    fun compileShaderFromTemplate(): String {
        val glslContent = readImageGlslFile()
        return wrapShaderCode(glslContent)
    }
    
    /**
     * 读取 Image.glsl 文件内容
     * 使用 VirtualFileSystem 读取项目中的源文件
     */
    private fun readImageGlslFile(): String {
        val projectBasePath = project.basePath 
            ?: throw IllegalStateException("Project base path is null")
        
        // 构建文件路径
        val filePath = "$projectBasePath/src/main/resources/shaderTemplate/Image.glsl"
        
        // 使用 VirtualFileManager 查找文件
        val virtualFile = VirtualFileManager.getInstance()
            .findFileByUrl("file://$filePath")
            ?: throw IllegalStateException("Image.glsl not found at: $filePath")
        
        // 读取文件内容
        return String(virtualFile.contentsToByteArray())
    }
    
    /**
     * 将用户的mainImage函数包装成完整的Fragment Shader
     * 
     * @param userGlslCode 用户编写的GLSL代码（只包含mainImage函数）
     * @return 完整的Fragment Shader源代码
     */
    private fun wrapShaderCode(userGlslCode: String): String {
        return """
#version 300 es
precision highp float;

uniform vec3 iResolution;
uniform float iTime;
uniform float iTimeDelta;
uniform int iFrame;
uniform vec4 iMouse;
uniform vec4 iDate;

out vec4 fragColor;

$userGlslCode

void main() {
    mainImage(fragColor, gl_FragCoord.xy);
}
        """.trimIndent()
    }
}

