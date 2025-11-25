package com.github.edenlia.shadertoyeditor.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

/**
 * ToolWindow 状态监听器实现
 * 
 * 监听 IntelliJ Platform 的 ToolWindow 事件，
 * 过滤 ShadertoyConsole 相关事件并通过 MessageBus 广播
 */
class STE_ToolWindowManagerListener(private val project: Project) : ToolWindowManagerListener {
    
    companion object {
        private const val SHADERTOY_CONSOLE_ID = "ShadertoyConsole"
        private const val SHADERTOY_ID = "Shadertoy"
    }

    /**
     * ToolWindow 状态变化时触发
     * 
     * 在这里统一处理 show 和 hide 事件
     */
    override fun stateChanged(toolWindowManager: ToolWindowManager, toolWindow: ToolWindow, changeType: ToolWindowManagerListener.ToolWindowManagerEventType) {
        // 获取 ShadertoyConsole ToolWindow
        val toolWindow = toolWindowManager.getToolWindow(SHADERTOY_CONSOLE_ID) ?: return

        // 检测状态变化
        when {
            // 从隐藏变为可见 (打开)
            changeType == ToolWindowManagerListener.ToolWindowManagerEventType.ActivateToolWindow -> {
                thisLogger().info("[STE_ToolWindowManagerListener] ShadertoyConsole shown (visible: false -> true)")
                
                // 发布打开事件
                project.messageBus
                    .syncPublisher(STE_IDEProjectEventListener.TOPIC)
                    .onShadertoyConsoleShown(project, toolWindow)
            }
            
            // 从可见变为隐藏 (关闭)
            changeType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow -> {
                thisLogger().info("[STE_ToolWindowManagerListener] ShadertoyConsole hidden (visible: true -> false)")
                
                // 发布关闭事件
                project.messageBus
                    .syncPublisher(STE_IDEProjectEventListener.TOPIC)
                    .onShadertoyConsoleHidden(project, toolWindow)
            }

            else -> {
                // 不输出日志，避免过多噪音
            }
        }
    }
}

