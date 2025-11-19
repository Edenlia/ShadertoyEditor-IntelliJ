package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Shader编译服务
 * 负责读取GLSL文件并包装成完整的Fragment Shader
 */
@Service(Service.Level.PROJECT)
class ShaderCompileService(private val project: Project) {
    
    /**
     * 编译指定的ShadertoyProject
     * 
     * @param shadertoyProject 要编译的Shadertoy项目
     * @return 完整的Fragment Shader源代码
     * @throws IllegalStateException 如果项目路径为null或Image.glsl未找到
     */
    fun compileProject(shadertoyProject: ShadertoyProject): String {
        thisLogger().info("[ShaderCompileService] Compiling project: ${shadertoyProject.name}")
        val glslContent = readProjectImageGlsl(shadertoyProject)
        return wrapShaderCode(glslContent)
    }
    
    /**
     * 读取shader模板文件并包装成完整的Fragment Shader
     * 
     * @deprecated 保留用于向后兼容，建议使用 compileProject(ShadertoyProject)
     * @return 完整的Fragment Shader源代码
     */
    @Deprecated("Use compileProject(ShadertoyProject) instead", ReplaceWith("compileProject(project)"))
    fun compileShaderFromTemplate(): String {
        val glslContent = readImageGlslFile()
        return wrapShaderCode(glslContent)
    }
    
    /**
     * 读取指定ShadertoyProject的Image.glsl文件
     * 
     * @param proj Shadertoy项目
     * @return Image.glsl文件内容
     */
    private fun readProjectImageGlsl(proj: ShadertoyProject): String {
        val projectBasePath = project.basePath 
            ?: throw IllegalStateException("Project base path is null")
        
        val imagePath = proj.getImageGlslPath(projectBasePath)
        thisLogger().info("[ShaderCompileService] Reading Image.glsl from: $imagePath")
        
        val virtualFile = VirtualFileManager.getInstance()
            .findFileByUrl("file://$imagePath")
            ?: throw IllegalStateException("Image.glsl not found: $imagePath")
        
        return String(virtualFile.contentsToByteArray())
    }
    
    /**
     * 读取 Image.glsl 文件内容（模板方式）
     * 使用 VirtualFileSystem 读取项目中的源文件
     * 
     * @deprecated 保留用于向后兼容
     */
    @Deprecated("Use readProjectImageGlsl(ShadertoyProject) instead")
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
#version 330 core
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
    mainImage(fragColor, (gl_FragCoord.xy + vec2(1.0))*0.5);
}
        """.trimIndent()
    }
}

