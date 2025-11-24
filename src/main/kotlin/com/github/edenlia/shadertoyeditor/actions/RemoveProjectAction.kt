package com.github.edenlia.shadertoyeditor.actions

import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages

/**
 * 删除 Shadertoy 项目的 Action
 * （只从配置中移除，不删除文件）
 */
class RemoveProjectAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectManager = ShadertoyProjectManager.getInstance(project)
        
        val currentProj = projectManager.getCurrentShadertoyProject()
        if (currentProj == null) {
            Messages.showWarningDialog(
                project,
                "Please activate a project first (double-click in the list)",
                "No Project Selected"
            )
            return
        }
        
        val result = Messages.showYesNoDialog(
            project,
            "Remove project '${currentProj.name}'?\n(Files will NOT be deleted)",
            "Confirm Remove",
            Messages.getQuestionIcon()
        )
        
        if (result == Messages.YES) {
            projectManager.removeShadertoyProject(currentProj)
            thisLogger().info("[RemoveProjectAction] Project removed: ${currentProj.name}")
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val projectManager = ShadertoyProjectManager.getInstance(project)
        
        // 只有选中项目时才启用按钮
        e.presentation.isEnabled = projectManager.getCurrentShadertoyProject() != null
    }
}

