package com.github.edenlia.shadertoyeditor.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import javax.swing.JComponent

/**
 * JCEF浏览器组件，用于在工具窗口中渲染WebGL内容
 * 
 * @param project 当前项目实例
 * @param htmlFile 要加载的HTML文件名（位于resources/webview/目录下）
 * @throws UnsupportedOperationException 当JCEF不被支持时抛出
 */
class JCefBrowserComponent(
    private val project: Project,
    private val htmlFile: String = "shadertoy-renderer.html"
) : Disposable {
    
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
    fun getComponent(): JComponent {
        return browser.component
    }
    
    /**
     * 执行JavaScript代码
     * 
     * @param jsCode 要执行的JavaScript代码
     */
    fun executeJavaScript(jsCode: String) {
        browser.cefBrowser.executeJavaScript(jsCode, browser.cefBrowser.url, 0)
    }
    
    /**
     * 加载shader代码到WebGL渲染器
     * 
     * @param fragmentShaderSource 完整的fragment shader源代码
     */
    fun loadShaderCode(fragmentShaderSource: String) {
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
     * 释放浏览器资源
     */
    override fun dispose() {
        browser.dispose()
    }
}

