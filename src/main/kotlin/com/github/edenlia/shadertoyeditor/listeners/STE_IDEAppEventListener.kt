package com.github.edenlia.shadertoyeditor.listeners

import com.intellij.util.messages.Topic

/**
 * 在IDE应用范围内监听事件的接口
 */
interface STE_IDEAppEventListener {
    
    /**
     * 分辨率变更事件
     * 
     * @param width 新的目标宽度
     * @param height 新的目标高度
     */
    fun onRefCanvasResolutionChanged(width: Int, height: Int)
    
    companion object {
        /**
         * MessageBus Topic
         * 用于发布和订阅分辨率变更事件
         */
        val TOPIC = Topic.create(
            "Shadertoy Resolution Changed",
            STE_IDEAppEventListener::class.java
        )
    }
}

