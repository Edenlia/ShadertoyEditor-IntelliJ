package com.github.edenlia.shadertoyeditor.actions

import com.github.edenlia.shadertoyeditor.dialogs.CreateShadertoyProjectDialog
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages

/**
 * 新建 Shadertoy 项目的 Action
 */
class NewProjectAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectManager = ShadertoyProjectManager.getInstance(project)
        
        val dialog = CreateShadertoyProjectDialog(project)
        if (dialog.showAndGet()) {
            try {
                val newProject = projectManager.createShadertoyProject(
                    dialog.getProjectName(),
                    dialog.getProjectPath()
                )
                
                // 自动激活新项目
                projectManager.setCurrentShadertoyProject(newProject)
                
                // 通知用户
                Notifications.Bus.notify(
                    Notification(
                        "Shadertoy Editor",
                        "Project Created",
                        "Created '${newProject.name}' at ${newProject.path}/Image.glsl",
                        NotificationType.INFORMATION
                    )
                )
                
                thisLogger().info("[NewProjectAction] Project created: ${newProject.name}")
                
            } catch (ex: IllegalArgumentException) {
                Messages.showErrorDialog(
                    project,
                    ex.message ?: "Failed to create project",
                    "Validation Error"
                )
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to create project: ${ex.message}",
                    "Error"
                )
                thisLogger().error("[NewProjectAction] Failed to create project", ex)
            }
        }
    }
}

