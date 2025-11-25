package com.github.edenlia.shadertoyeditor.startup

import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.services.ShaderCompileService
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow

/**
 * Shadertoy项目启动加载器
 * 
 * 在IDE项目打开时自动执行：
 * - 加载.shadertoy-editor.config.yml配置
 * - 初始化项目管理器
 * - 初始化编译服务（用于监听文件保存事件）
 */
class ShadertoyProjectLoader : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        thisLogger().info("[ShadertoyProjectLoader] Initializing Shadertoy Editor for project: ${project.name}")
        
        try {
            // 1. 获取项目管理器实例（这会触发init块，自动加载配置）
            val projectManager = ShadertoyProjectManager.getInstance(project)
            
            val projectCount = projectManager.getAllProjects().size
            thisLogger().info("[ShadertoyProjectLoader] Loaded $projectCount Shadertoy project(s)")
            
            if (projectCount > 0) {
                val projectNames = projectManager.getAllProjects()
                    .joinToString(", ") { it.name }
                thisLogger().info("[ShadertoyProjectLoader] Projects: $projectNames")
            }
            
            // 2. 初始化编译服务（触发 init 块，订阅项目变更和文件保存事件）
            val compileService = project.service<ShaderCompileService>()
            thisLogger().info("[ShadertoyProjectLoader] ShaderCompileService initialized")
            
            // 3. 订阅 ToolWindow 状态变化事件（示例：输出日志）
            subscribeToToolWindowEvents(project)
            
        } catch (e: Exception) {
            thisLogger().error("[ShadertoyProjectLoader] Failed to initialize Shadertoy Editor", e)
        }
    }
    
    /**
     * 订阅 IDE Project 级别事件
     * 
     * 示例：监听 ShadertoyConsole 的打开和关闭，输出日志
     */
    private fun subscribeToToolWindowEvents(project: Project) {
        project.messageBus.connect().subscribe(
            STE_IDEProjectEventListener.TOPIC,
            object : STE_IDEProjectEventListener {
                override fun onShadertoyProjectChanged(project: ShadertoyProject?) {
                    // 不处理项目变更事件，仅订阅 ToolWindow 事件
                }
                
                override fun onShadertoyConsoleShown(project: Project, toolWindow: ToolWindow) {
                    thisLogger().info("✅ [ToolWindow Event] ShadertoyConsole 已打开 - Project: ${project.name}, ToolWindow ID: ${toolWindow.id}")
                }
                
                override fun onShadertoyConsoleHidden(project: Project, toolWindow: ToolWindow) {
                    thisLogger().info("❌ [ToolWindow Event] ShadertoyConsole 已关闭 - Project: ${project.name}, ToolWindow ID: ${toolWindow.id}")
                }
            }
        )
        thisLogger().info("[ShadertoyProjectLoader] IDE Project event listener registered")
    }
}

