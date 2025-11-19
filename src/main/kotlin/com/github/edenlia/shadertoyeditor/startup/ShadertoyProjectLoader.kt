package com.github.edenlia.shadertoyeditor.startup

import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Shadertoy项目启动加载器
 * 
 * 在IDE项目打开时自动执行：
 * - 加载.shadertoy-editor.config.yml配置
 * - 确保.gitignore正确配置
 * - 初始化项目管理器
 */
class ShadertoyProjectLoader : ProjectActivity {
    
    override suspend fun execute(project: Project) {
        thisLogger().info("[ShadertoyProjectLoader] Initializing Shadertoy Editor for project: ${project.name}")
        
        try {
            // 获取项目管理器实例（这会触发init块，自动加载配置）
            val projectManager = ShadertoyProjectManager.getInstance(project)
            
            val projectCount = projectManager.getAllProjects().size
            thisLogger().info("[ShadertoyProjectLoader] Loaded $projectCount Shadertoy project(s)")
            
            if (projectCount > 0) {
                val projectNames = projectManager.getAllProjects()
                    .joinToString(", ") { it.name }
                thisLogger().info("[ShadertoyProjectLoader] Projects: $projectNames")
            }
            
        } catch (e: Exception) {
            thisLogger().error("[ShadertoyProjectLoader] Failed to initialize Shadertoy Editor", e)
        }
    }
}

