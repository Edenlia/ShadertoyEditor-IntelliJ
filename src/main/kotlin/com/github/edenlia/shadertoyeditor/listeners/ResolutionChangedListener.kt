package com.github.edenlia.shadertoyeditor.listeners

import com.intellij.util.messages.Topic

/**
 * 分辨率变更监听器
 * 当用户在Settings中修改目标分辨率并Apply时触发
 */
interface ResolutionChangedListener {
    
    /**
     * 分辨率变更事件
     * 
     * @param width 新的目标宽度
     * @param height 新的目标高度
     */
    fun onResolutionChanged(width: Int, height: Int)
    
    companion object {
        /**
         * MessageBus Topic
         * 用于发布和订阅分辨率变更事件
         */
        val TOPIC = Topic.create(
            "Shadertoy Resolution Changed",
            ResolutionChangedListener::class.java
        )
    }
}

