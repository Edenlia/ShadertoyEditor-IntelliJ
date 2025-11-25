package com.github.edenlia.shadertoyeditor.listeners

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.util.messages.Topic

/**
 * 在IDE Project范围内监听事件的接口
 */
interface STE_IDEProjectEventListener {
    
    /**
     * 当前选中的Shadertoy项目发生变化
     * 
     * @param shadertoyProject 新选中的项目，如果没有选中则为null
     */
    fun onShadertoyProjectChanged(shadertoyProject: ShadertoyProject?)
    
    /**
     * ShadertoyConsole ToolWindow 显示时触发
     * 
     * @param project 当前IDE项目
     * @param toolWindow ToolWindow 实例
     */
    fun onShadertoyConsoleShown(project: Project, toolWindow: ToolWindow) {}
    
    /**
     * ShadertoyConsole ToolWindow 隐藏时触发
     * 
     * @param project 当前IDE项目
     * @param toolWindow ToolWindow 实例
     */
    fun onShadertoyConsoleHidden(project: Project, toolWindow: ToolWindow) {}
    
    companion object {
        /**
         * MessageBus Topic用于发布和订阅项目级事件
         * 包括：Shadertoy项目变更、ToolWindow状态变化等
         */
        val TOPIC: Topic<STE_IDEProjectEventListener> = Topic.create(
            "ShadertoyIDEProjectEvents",
            STE_IDEProjectEventListener::class.java
        )
    }
}

