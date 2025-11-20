package com.github.edenlia.shadertoyeditor.actions

import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger

/**
 * 刷新 Shadertoy 项目列表的 Action
 * 重新加载 .shadertoy-editor.config.yml
 */
class RefreshProjectsAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectManager = ShadertoyProjectManager.getInstance(project)
        
        try {
            // 重新加载配置
            projectManager.loadConfig()
            
            val count = projectManager.getAllProjects().size
            
            Notifications.Bus.notify(
                Notification(
                    "Shadertoy Editor",
                    "Projects Refreshed",
                    "Loaded $count project(s) from config file",
                    NotificationType.INFORMATION
                )
            )
            
            thisLogger().info("[RefreshProjectsAction] Config reloaded, $count projects")
            
        } catch (ex: Exception) {
            Notifications.Bus.notify(
                Notification(
                    "Shadertoy Editor",
                    "Refresh Failed",
                    "Failed to reload config: ${ex.message}",
                    NotificationType.ERROR
                )
            )
            thisLogger().error("[RefreshProjectsAction] Failed to reload config", ex)
        }
    }
}

