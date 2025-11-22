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
     * JOGL (Java OpenGL) + GLCanvas
     * 高性能（120fps+），无线程限制，支持所有平台
     * 推荐使用
     */
    JOGL
}

