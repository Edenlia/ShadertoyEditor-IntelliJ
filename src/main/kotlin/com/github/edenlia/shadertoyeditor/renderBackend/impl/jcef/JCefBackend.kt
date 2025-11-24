package com.github.edenlia.shadertoyeditor.renderBackend.impl.jcef

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.renderBackend.Texture
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JComponent

/**
 * JCEF WebGL 渲染后端
 *
 * ⚠️ 已弃用：此后端不再维护，请使用 JOGL 后端
 *
 * @param project 当前项目实例
 * @throws UnsupportedOperationException 总是抛出，因为此后端已被禁用
 */
class JCefBackend(
    private val project: Project
) : RenderBackend {
    
    init {
        throw UnsupportedOperationException(
            "JCEF backend is deprecated and no longer supported. " +
            "Please use JOGL backend instead. " +
            "You can change this in Settings > Tools > Shadertoy Editor."
        )
    }

    // ===== RenderBackend 接口实现（全部抛异常）=====
    
    override fun getRootComponent(): JComponent {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun loadShader(fragmentShaderSource: String) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun updateRefCanvasResolution(width: Int, height: Int) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun onContainerResized(width: Int, height: Int) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun enableRendering(enable: Boolean) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun setFPSLimit(fps: Int) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun setChannelTexture(channelIndex: Int, texture: Texture?) {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun clearAllChannels() {
        throw UnsupportedOperationException("JCEF backend is not supported")
    }

    override fun dispose() {
        // 空实现，因为没有资源需要释放
    }
}