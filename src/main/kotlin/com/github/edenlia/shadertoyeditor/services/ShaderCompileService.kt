package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.listeners.ShadertoyProjectChangedListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.LINUX
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.MACOS
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.UNKNOWN
import com.github.edenlia.shadertoyeditor.services.GlobalEnvService.Platform.WINDOWS
import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings
import com.github.edenlia.shadertoyeditor.toolWindow.ShadertoyOutputWindowFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.messages.MessageBusConnection
import javax.swing.SwingUtilities

/**
 * Shader编译服务
 * 
 * 职责：
 * - 读取GLSL文件并包装成完整的Fragment Shader
 * - 管理自动编译功能（监听文件保存事件）
 */
@Service(Service.Level.PROJECT)
class ShaderCompileService(private val project: Project) : Disposable {
    
    // ========== 自动编译相关字段 ==========
    
    /**
     * MessageBus 连接（用于订阅文件保存事件）
     */
    private var fileSaveConnection: MessageBusConnection? = null
    
    /**
     * 当前监听的文件（激活项目的 Image.glsl）
     */
    private var currentMonitoredFile: VirtualFile? = null
    
    /**
     * 文件保存监听器
     */
    private val fileListener = object : FileDocumentManagerListener {
        override fun beforeDocumentSaving(document: Document) {
            handleFileSaving(document)
        }
    }
    
    // ========== 初始化 ==========
    
    init {
        // 订阅项目变更事件
        subscribeToProjectChanges()
    }
    
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
     * 优先读取编辑器中的内容（包括未保存的修改），
     * 如果文件未在编辑器中打开，则读取磁盘文件
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
        
        // 尝试从 Document 读取（包括未保存的修改）
        return ApplicationManager.getApplication().runReadAction<String> {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                thisLogger().info("[ShaderCompileService] Reading from Document (includes unsaved changes)")
                document.text
            } else {
                thisLogger().info("[ShaderCompileService] Reading from VirtualFile (disk content)")
                String(virtualFile.contentsToByteArray())
            }
        }
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
    mainImage(fragColor, (gl_FragCoord.xy));
}
        """.trimIndent()
    }
    
    // ========== 自动编译功能实现 ==========
    
    /**
     * 订阅项目变更事件
     * 当用户激活/切换/取消激活项目时，动态绑定/解绑文件监听器
     */
    private fun subscribeToProjectChanges() {
        ApplicationManager.getApplication().messageBus
            .connect(this)  // 使用 this 作为 parentDisposable，自动管理生命周期
            .subscribe(
                ShadertoyProjectChangedListener.TOPIC,
                object : ShadertoyProjectChangedListener {
                    override fun onProjectChanged(project: ShadertoyProject?) {
                        handleProjectChanged(project)
                    }
                }
            )
    }
    
    /**
     * 处理项目变更
     * 
     * @param shadertoyProject 新激活的项目（null 表示取消激活）
     */
    private fun handleProjectChanged(shadertoyProject: ShadertoyProject?) {
        // 先解绑旧的监听器
        unbindFileListener()
        
        // 如果有新项目，绑定监听器
        if (shadertoyProject != null) {
            bindFileListener(shadertoyProject)
        }
    }
    
    /**
     * 绑定文件监听器
     * 精确监听指定项目的 Image.glsl 文件
     * 
     * @param shadertoyProject 要监听的 Shadertoy 项目
     */
    private fun bindFileListener(shadertoyProject: ShadertoyProject) {
        val projectBasePath = project.basePath ?: return
        val imagePath = shadertoyProject.getImageGlslPath(projectBasePath)
        
        // 获取 VirtualFile
        currentMonitoredFile = VirtualFileManager.getInstance()
            .findFileByUrl("file://$imagePath")
        
        if (currentMonitoredFile == null) {
            thisLogger().warn("[ShaderCompileService] Cannot bind listener: Image.glsl not found at $imagePath")
            return
        }
        
        // 订阅文件保存事件（全局监听，但通过 currentMonitoredFile 快速过滤）
        fileSaveConnection = ApplicationManager.getApplication().messageBus.connect(this)
        fileSaveConnection?.subscribe(
            FileDocumentManagerListener.TOPIC,
            fileListener
        )
        
        thisLogger().info("[ShaderCompileService] Bound file save listener to: ${shadertoyProject.name}/Image.glsl")
    }
    
    /**
     * 解绑文件监听器
     */
    private fun unbindFileListener() {
        fileSaveConnection?.disconnect()
        fileSaveConnection = null
        currentMonitoredFile = null
        
        thisLogger().info("[ShaderCompileService] Unbound file save listener")
    }
    
    /**
     * 处理文件保存事件
     * 
     * @param document 被保存的文档
     */
    private fun handleFileSaving(document: Document) {
        // 1. 快速过滤：检查是否是我们监听的文件
        val file = FileDocumentManager.getInstance().getFile(document)
        if (file != currentMonitoredFile) {
            return  // 不是我们关心的文件，立即返回
        }
        
        // 2. 检查配置：是否开启了自动编译
        val config = ShadertoySettings.getInstance().getConfig()
        if (!config.autoCompileOnSave) {
            thisLogger().info("[ShaderCompileService] Auto-compile disabled in settings, skip")
            return
        }
        
        // 3. 触发自动编译
        thisLogger().info("[ShaderCompileService] Auto-compiling on save: ${file?.name}")
        
        // 延迟一点确保文件已完全保存到磁盘（虽然我们读取的是 Document，但保险起见）
        SwingUtilities.invokeLater {
            compileShadertoyProject()
        }
    }
    
    /**
     * Service dispose 时清理资源
     */
    override fun dispose() {
        unbindFileListener()
        thisLogger().info("[ShaderCompileService] Service disposed")
    }
}

