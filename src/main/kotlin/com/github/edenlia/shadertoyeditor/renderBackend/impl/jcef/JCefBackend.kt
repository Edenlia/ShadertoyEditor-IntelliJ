package com.github.edenlia.shadertoyeditor.renderBackend.impl.jcef

import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JComponent

/**
 * JCEF WebGL 渲染后端
 *
 * 使用Chromium Embedded Framework进行WebGL渲染
 *
 * @param project 当前项目实例
 * @param htmlFile 要加载的HTML文件名（位于resources/webview/目录下）
 * @throws UnsupportedOperationException 当JCEF不被支持时抛出
 */
class JCefBackend(
    private val project: Project,
    private val outerComponent: JComponent,
    private val htmlFile: String = "shadertoy-renderer.html"
) : RenderBackend {
    
    private val browser: JBCefBrowser

    init {
        // 检查JCEF是否被支持
        if (!JBCefApp.isSupported()) {
            throw UnsupportedOperationException(
                "JCEF is not supported in this IDE. " +
                "Please upgrade to IntelliJ IDEA 2020.1 or later."
            )
        }

        // 创建浏览器实例
        browser = JBCefBrowser()

        // 启用开发者工具（用于调试）
        // 右键点击网页 -> "Open DevTools" 可以查看控制台日志
        browser.jbCefClient.setProperty("remote_debugging_port", "9222")

        // 设置生命周期管理
        Disposer.register(project, this)

        // 加载初始HTML内容
        loadInitialContent()
    }

    /**
     * 加载初始HTML内容
     */
    private fun loadInitialContent() {
        val htmlContent = javaClass.getResource("/webview/$htmlFile")?.readText()
            ?: throw IllegalStateException(
                "$htmlFile not found in resources/webview/"
            )

        browser.loadHTML(htmlContent)
    }

    /**
     * 动态加载指定的HTML文件
     *
     * @param fileName HTML文件名（位于resources/webview/目录下）
     */
    fun loadHTMLFile(fileName: String) {
        val htmlContent = javaClass.getResource("/webview/$fileName")?.readText()
            ?: throw IllegalStateException(
                "$fileName not found in resources/webview/"
            )

        browser.loadHTML(htmlContent)
    }

    /**
     * 获取浏览器的Swing组件
     *
     * @return JComponent 可以添加到Swing容器中的组件
     */
    override fun getRootComponent(): JComponent {
        return browser.component
    }

    override fun getOuterComponent(): JComponent {
        return outerComponent
    }

    /**
     * 执行JavaScript代码
     *
     * @param jsCode 要执行的JavaScript代码
     */
    fun executeJavaScript(jsCode: String) {
        browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
    }

    // ===== RenderBackend接口实现 =====

    /**
     * 加载Shader代码（RenderBackend接口方法）
     *
     * @param fragmentShaderSource 完整的fragment shader源代码
     */
    override fun loadShader(fragmentShaderSource: String) {
        // 转义特殊字符，使用模板字符串
        val escapedCode = fragmentShaderSource
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

        // 调用网页中的 window.loadShader 函数
        // 使用 setTimeout 确保在浏览器完全加载后执行
        val jsCode = """
            (function() {
                console.log('[Shadertoy] Attempting to load shader...');
                
                function tryLoadShader() {
                    if (typeof window.loadShader === 'function') {
                        console.log('[Shadertoy] window.loadShader found, loading shader...');
                        try {
                            window.loadShader(`$escapedCode`);
                            console.log('[Shadertoy] Shader loaded and compiled successfully!');
                        } catch (e) {
                            console.error('[Shadertoy] Failed to load shader:', e);
                        }
                    } else {
                        console.warn('[Shadertoy] window.loadShader not ready, retrying in 100ms...');
                        setTimeout(tryLoadShader, 100);
                    }
                }
                
                tryLoadShader();
            })();
        """.trimIndent()

        executeJavaScript(jsCode)
    }

    /**
     * 更新渲染分辨率（RenderBackend接口方法）
     * 通过JavaScript调用HTML端的setTargetResolution函数
     *
     * @param width 目标宽度
     * @param height 目标高度
     */
    override fun updateRefCanvasResolution(width: Int, height: Int) {
        val jsCode = """
            (function() {
                console.log('[Shadertoy] Updating target resolution to ${width}x${height}');
                
                function tryUpdateResolution() {
                    if (typeof window.setTargetResolution === 'function') {
                        window.setTargetResolution($width, $height);
                        console.log('[Shadertoy] Resolution updated successfully!');
                    } else {
                        console.warn('[Shadertoy] window.setTargetResolution not ready, retrying in 100ms...');
                        setTimeout(tryUpdateResolution, 100);
                    }
                }
                
                tryUpdateResolution();
            })();
        """.trimIndent()

        executeJavaScript(jsCode)
    }

    override fun updateOuterResolution(width: Int, height: Int) {
        TODO("Not yet implemented")
    }

    /**
     * 释放浏览器资源
     */
    override fun dispose() {
        browser.dispose()
    }

    // ===== 向后兼容的方法（可选，如果有其他地方还在使用旧方法名）=====

    /**
     * @deprecated 使用 loadShader() 代替
     */
    @Deprecated("Use loadShader() instead", ReplaceWith("loadShader(fragmentShaderSource)"))
    fun loadShaderCode(fragmentShaderSource: String) = loadShader(fragmentShaderSource)

    /**
     * @deprecated 使用 setResolution() 代替
     */
    @Deprecated("Use setResolution() instead", ReplaceWith("setResolution(width, height)"))
    fun updateTargetResolution(width: Int, height: Int) = updateRefCanvasResolution(width, height)
}