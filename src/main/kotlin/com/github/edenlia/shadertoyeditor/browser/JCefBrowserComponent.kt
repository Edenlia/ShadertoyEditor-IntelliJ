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
    private val htmlFile: String = "cube-preview.html"
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
     * 释放浏览器资源
     */
    override fun dispose() {
        browser.dispose()
    }
}

