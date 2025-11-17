package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.edenlia.shadertoyeditor.browser.JCefBrowserComponent
import javax.swing.JComponent


class ShadertoyOutputWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyOutputWindow = ShadertoyOutputWindow(project)
        val content = ContentFactory.getInstance()
            .createContent(shadertoyOutputWindow.getContent(), null, false)
        
        // 注册生命周期管理：当Content被dispose时，也dispose浏览器组件
        Disposer.register(content) {
            shadertoyOutputWindow.dispose()
        }
        
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Shadertoy输出窗口，使用JCEF浏览器显示WebGL渲染内容
     */
    class ShadertoyOutputWindow(private val project: Project) {
        
        private val browserComponent: JCefBrowserComponent
        
        init {
            browserComponent = JCefBrowserComponent(project)
        }
        
        /**
         * 获取窗口内容组件
         */
        fun getContent(): JComponent {
            return browserComponent.getComponent()
        }
        
        /**
         * 释放资源
         */
        fun dispose() {
            browserComponent.dispose()
        }
    }
}

