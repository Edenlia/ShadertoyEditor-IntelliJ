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
     * 高性能（120fps+），但macOS有线程限制问题
     */
    LWJGL,
    
    /**
     * JOGL (Java OpenGL) + GLCanvas
     * 高性能（120fps+），无线程限制，支持所有平台
     * 推荐使用
     */
    JOGL
}

