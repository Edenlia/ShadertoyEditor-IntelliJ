package com.github.edenlia.shadertoyeditor.actions

import com.github.edenlia.shadertoyeditor.services.RenderBackendService
import com.github.edenlia.shadertoyeditor.services.ShaderCompileService
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service

/**
 * 编译Shadertoy项目的Action
 * 可通过快捷键或菜单调用
 */
class CompileShadertoyAction : AnAction() {
    
    /**
     * 执行编译操作
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val shadertoyProjectManager = project.service<ShadertoyProjectManager>()
        val renderBackendService = project.service<RenderBackendService>()
        val compileService = project.service<ShaderCompileService>()
        
        val currentProject = shadertoyProjectManager.getCurrentShadertoyProject()
        renderBackendService.getBackend().loadProjectTextures(currentProject)
        compileService.compileShadertoyProject(currentProject)
    }
}

