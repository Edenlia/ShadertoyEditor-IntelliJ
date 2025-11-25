package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.messages.MessageBusConnection
import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.listeners.STE_IDEAppEventListener
import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.services.RenderBackendService
import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities


class ShadertoyOutputWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyOutputWindow = ShadertoyOutputWindow(project, toolWindow)
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
     * Shadertoy输出窗口，使用JOGL渲染后端显示Shader渲染内容
     */
    class ShadertoyOutputWindow(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) : com.intellij.openapi.Disposable {
        
        private val renderBackend: RenderBackend
        private val messageBusConnection: MessageBusConnection

        init {
            // 从 Service 获取 RenderBackend（懒加载，每个 Project 一个实例）
            renderBackend = project.service<RenderBackendService>().getBackend()
            thisLogger().info("[ShadertoyOutputWindow] Got RenderBackend from service")

            // 订阅参考分辨率变更事件（Settings 修改时）
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)

            // 监听 ToolWindow 尺寸变化（主动调用 Backend）
            subscribeToToolWindowResize()
        }

        /**
         * 监听 ToolWindow 尺寸变化
         */
        private fun subscribeToToolWindowResize() {
            // 监听 ToolWindow component 的尺寸变化，主动通知 Backend
            toolWindow.component.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    val width = toolWindow.component.width
                    val height = toolWindow.component.height
                    
                    thisLogger().info("[ShadertoyOutputWindow] ToolWindow resized to ${width}x${height}, notifying backend")
                    
                    // 主动调用 Backend 的容器尺寸变化通知
                    renderBackend.onContainerResized(width, height)
                }
            })
        }

        /**
         * 获取窗口内容组件
         */
        fun getContent(): JComponent {
            return renderBackend.getRootComponent()
        }
        
        /**
         * 获取渲染后端实例
         */
        fun getRenderBackend(): RenderBackend {
            return renderBackend
        }

        /**
         * 释放资源（实现 Disposable 接口）
         */
        override fun dispose() {
            thisLogger().info("[ShadertoyOutputWindow] Disposing output window")
            
            // 禁用渲染（节省 CPU，但不销毁 Backend）
            renderBackend.enableRendering(false)
            
            // 断开 MessageBus 连接
            messageBusConnection.disconnect()
            
            // 注意：不调用 renderBackend.dispose()
            // Backend 的生命周期由 RenderBackendService 管理，跟随 Project
            thisLogger().info("[ShadertoyOutputWindow] Output window disposed, rendering disabled")
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

