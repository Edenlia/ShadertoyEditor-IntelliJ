package com.github.edenlia.shadertoyeditor.listeners

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.util.messages.Topic

/**
 * Shadertoy项目切换监听器
 * 
 * 当用户选择不同的Shadertoy项目时，通过MessageBus通知所有订阅者
 * 主要用于通知渲染窗口当前选中的项目已变化
 */
interface ShadertoyProjectChangedListener {
    
    /**
     * 当前选中的Shadertoy项目发生变化
     * 
     * @param project 新选中的项目，如果没有选中则为null
     */
    fun onShadertoyProjectChanged(project: ShadertoyProject?)
    
    companion object {
        /**
         * MessageBus Topic用于发布和订阅项目变更事件
         */
        val TOPIC: Topic<ShadertoyProjectChangedListener> = Topic.create(
            "ShadertoyProjectChanged",
            ShadertoyProjectChangedListener::class.java
        )
    }
}

