package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.github.edenlia.shadertoyeditor.browser.JCefBrowserComponent
import javax.swing.JComponent


class ShadertoyOutputWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyOutputWindow = ShadertoyOutputWindow(project)
        val content = ContentFactory.getInstance()
            .createContent(shadertoyOutputWindow.getContent(), null, false)
        
        // 将实例保存到project的userData中，方便其他组件访问
        project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, shadertoyOutputWindow)
        
        // 注册生命周期管理：当Content被dispose时，也dispose浏览器组件
        Disposer.register(content) {
            project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, null)
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
         * 获取浏览器组件，用于执行JavaScript
         */
        fun getBrowserComponent(): JCefBrowserComponent {
            return browserComponent
        }
        
        /**
         * 释放资源
         */
        fun dispose() {
            browserComponent.dispose()
        }
    }
    
    companion object {
        private val SHADERTOY_OUTPUT_WINDOW_KEY = 
            com.intellij.openapi.util.Key.create<ShadertoyOutputWindow>("SHADERTOY_OUTPUT_WINDOW")
        
        /**
         * 获取项目的ShadertoyOutputWindow实例
         */
        fun getInstance(project: Project): ShadertoyOutputWindow? {
            return project.getUserData(SHADERTOY_OUTPUT_WINDOW_KEY)
        }
    }
}

