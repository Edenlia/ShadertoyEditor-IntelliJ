package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.LINUX
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.MACOS
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.UNKNOWN
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.WINDOWS
import com.github.edenlia.shadertoyeditor.toolWindow.ShadertoyOutputWindowFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Shader编译服务
 * 负责读取GLSL文件并包装成完整的Fragment Shader
 */
@Service(Service.Level.PROJECT)
class ShaderCompileService(private val project: Project) {
    
    /**
     * 将指定的ShadertoyProject编译为完整的Shader代码
     * 
     * @param shadertoyProject 要编译的Shadertoy项目
     * @return 完整的Fragment Shader源代码
     * @throws IllegalStateException 如果项目路径为null或Image.glsl未找到
     */
    fun compileShaderToCode(shadertoyProject: ShadertoyProject): String {
        thisLogger().info("[ShaderCompileService] Compiling project: ${shadertoyProject.name}")
        val glslContent = readProjectImageGlsl(shadertoyProject)
        return wrapShaderCode(glslContent)
    }
    
    /**
     * 编译当前激活的Shadertoy项目并加载到渲染器
     * 这是一个高级方法，包含完整的编译流程：检查激活项目、DumbService、输出窗口等
     */
    fun compileShadertoyProject() {
        val projectManager = ShadertoyProjectManager.getInstance(project)
        val currentProject = projectManager.getCurrentProject()
        
        // 检查是否有激活的项目
        if (currentProject == null) {
            Messages.showWarningDialog(
                project,
                "Please activate a Shadertoy project first by double-clicking it",
                "No Project Activated"
            )
            return
        }
        
        // 检查是否处于索引构建模式
        if (DumbService.isDumb(project)) {
            thisLogger().info("[ShaderCompileService] Cannot compile shader during indexing, will retry when indexing is complete")
            // 等待索引完成后再执行
            DumbService.getInstance(project).runWhenSmart {
                compileShadertoyProject()
            }
            return
        }
        
        try {
            // 获取 ShadertoyOutput 窗口实例
            val outputWindow = ShadertoyOutputWindowFactory.getInstance(project)
            if (outputWindow == null) {
                thisLogger().warn("[ShaderCompileService] ShadertoyConsole window not found")
                Messages.showWarningDialog(
                    project,
                    "Please open the ShadertoyConsole window first",
                    "Window Not Found"
                )
                return
            }
            
            // 编译shader代码
            val shaderCode = compileShaderToCode(currentProject)
            
            // 加载到渲染后端
            outputWindow.getRenderBackend().loadShader(shaderCode)
            
            thisLogger().info("[ShaderCompileService] Shader compiled and loaded successfully: ${currentProject.name}")
            
        } catch (e: Exception) {
            thisLogger().error("[ShaderCompileService] Failed to compile shader", e)
            Messages.showErrorDialog(
                project,
                "Compilation failed: ${e.message}",
                "Shader Compilation Error"
            )
        }
    }
    
    /**
     * 读取shader模板文件并包装成完整的Fragment Shader
     * 
     * @deprecated 保留用于向后兼容，建议使用 compileShaderToCode(ShadertoyProject)
     * @return 完整的Fragment Shader源代码
     */
    @Deprecated("Use compileShaderToCode(ShadertoyProject) instead", ReplaceWith("compileShaderToCode(project)"))
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
        // 使用全局环境服务获取平台信息
        val globalEnv = GlobalEnvService.getInstance()

        val platformDefinition = when (globalEnv.currentPlatform) {
            WINDOWS -> "#define PLATFORM_WINDOWS"
            MACOS -> "#define PLATFORM_MACOS"
            LINUX -> "#define PLATFORM_LINUX"
            UNKNOWN -> "#define PLATFORM_UNKNOWN"
        }

        return """
#version 330 core

$platformDefinition

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
#ifdef PLATFORM_WINDOWS
    mainImage(fragColor, (gl_FragCoord.xy));
#elif defined(PLATFORM_MACOS)
    mainImage(fragColor, (gl_FragCoord.xy + vec2(1.0)) * 0.5);
#else
    mainImage(fragColor, (gl_FragCoord.xy));
#endif
}
        """.trimIndent()
    }
}

