package com.github.edenlia.shadertoyeditor.renderBackend

/**
 * 渲染后端类型枚举
 */
enum class RenderBackendType {
    /**
     * JCEF (Java Chromium Embedded Framework) + WebGL
     * 稳定但帧率受限（~30fps）
     */
    JCEF,
    
    /**
     * LWJGL3 + Native OpenGL
     * 高性能（120fps+），需要OpenGL 3.3+支持
     */
    LWJGL
}

