package com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * LWJGL3 Native OpenGL 渲染后端
 *
 * 使用LWJGL3进行原生OpenGL渲染，可以达到高帧率（120fps+）
 *
 * @param project 当前项目实例
 */
class LwjglBackend(private val project: Project) : RenderBackend {

    private val renderPanel: JPanel

    init {
        // TODO: 初始化LWJGL和OpenGL上下文
        // TODO: 创建FBO (Framebuffer Object)
        // TODO: 设置渲染循环

        // 临时占位UI
        renderPanel = JPanel(BorderLayout())
        renderPanel.add(
            JLabel("LWJGL Backend - Coming Soon (High Performance 120fps+)"),
            BorderLayout.CENTER
        )
    }

    override fun getComponent(): JComponent {
        return renderPanel
    }

    override fun loadShader(fragmentShaderSource: String) {
        // TODO: 编译OpenGL Fragment Shader
        // TODO: 创建Shader Program
        // TODO: 链接Vertex Shader和Fragment Shader
        // TODO: 检查编译错误
        throw UnsupportedOperationException("LWJGL backend not yet implemented")
    }

    override fun setResolution(width: Int, height: Int) {
        // TODO: 更新OpenGL Viewport
        // TODO: 调整FBO大小
        // TODO: 更新uniform变量
    }

    override fun dispose() {
        // TODO: 释放OpenGL资源（Shader, VBO, FBO等）
        // TODO: 销毁OpenGL上下文
    }
}