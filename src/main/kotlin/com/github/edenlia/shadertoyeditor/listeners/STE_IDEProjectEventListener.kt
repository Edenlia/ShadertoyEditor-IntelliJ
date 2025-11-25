package com.github.edenlia.shadertoyeditor.listeners

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.util.messages.Topic

/**
 * 在IDE Project范围内监听事件的接口
 */
interface STE_IDEProjectEventListener {
    
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
        val TOPIC: Topic<STE_IDEProjectEventListener> = Topic.create(
            "ShadertoyProjectChanged",
            STE_IDEProjectEventListener::class.java
        )
    }
}

