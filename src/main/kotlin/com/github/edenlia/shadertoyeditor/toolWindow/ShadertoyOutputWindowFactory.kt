package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.messages.MessageBusConnection
import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.services.RenderBackendService
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.JBColor
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.SwingUtilities


class ShadertoyOutputWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyConsoleWindow = ShadertoyConsoleWindow(project, toolWindow)
        val content = ContentFactory.getInstance()
            .createContent(shadertoyConsoleWindow.getContent(), null, false)
        
        // 将实例保存到project的userData中，方便其他组件访问
        project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, shadertoyConsoleWindow)
        
        // 注册生命周期管理：当Content被dispose时，也dispose浏览器组件
        Disposer.register(content) {
            project.putUserData(SHADERTOY_OUTPUT_WINDOW_KEY, null)
            shadertoyConsoleWindow.dispose()
        }
        
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Shadertoy输出窗口，使用JOGL渲染后端显示Shader渲染内容
     */
    class ShadertoyConsoleWindow(
        private val project: Project,
        private val _toolWindow: ToolWindow
    ) : com.intellij.openapi.Disposable {
        
        private val renderBackend: RenderBackend

        init {
            // 从 Service 获取 RenderBackend（懒加载，每个 Project 一个实例）
            renderBackend = project.service<RenderBackendService>().getBackend()
            thisLogger().info("[ShadertoyOutputWindow] Got RenderBackend from service")


            // 监听 ToolWindow 尺寸变化（主动调用 Backend）
            subscribeToToolWindowResize()
            subscribeToSTE_IDEProjectEvent()
        }

        /**
         * 监听 ToolWindow 尺寸变化
         */
        private fun subscribeToToolWindowResize() {
            // 监听 ToolWindow component 的尺寸变化，主动通知 Backend
            _toolWindow.component.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    val width = _toolWindow.component.width
                    val height = _toolWindow.component.height
                    
                    thisLogger().info("[ShadertoyOutputWindow] ToolWindow resized to ${width}x${height}, notifying backend")
                    
                    // 主动调用 Backend 的容器尺寸变化通知
                    renderBackend.onContainerResized(width, height)
                }
            })
        }

        private fun subscribeToSTE_IDEProjectEvent() {
            project.messageBus.connect(this).subscribe(
                STE_IDEProjectEventListener.TOPIC,
                object : STE_IDEProjectEventListener {
                    override fun onShadertoyConsoleShown(project: Project, toolWindow: ToolWindow) {
                        if (toolWindow == _toolWindow) {

                        }
                    }

                    override fun onShadertoyConsoleHidden(project: Project, toolWindow: ToolWindow) {

                    }

                    override fun onShaderCompiled(
                        shadertoyProject: ShadertoyProject?,
                        success: Boolean,
                        message: String?
                    ) {
                        val shadertoyProjectManager = project.service<ShadertoyProjectManager>()

                        if (shadertoyProject != null &&
                            shadertoyProject == shadertoyProjectManager.getCurrentShadertoyProject()) {
                            if (success) {

                            }
                            else {
                                SwingUtilities.invokeLater {
//                                    statusLabel.text = "Shader compilation failed"
//                                    statusLabel.foreground = JBColor.RED

                                    JOptionPane.showMessageDialog(
                                        renderBackend.getRootComponent(),
                                        message,
                                        "Shader Compilation Error",
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            }
                        }
                    }
                }
            )
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

            // 注意：不调用 renderBackend.dispose()
            // Backend 的生命周期由 RenderBackendService 管理，跟随 Project
            thisLogger().info("[ShadertoyOutputWindow] Output window disposed, rendering disabled")
        }
    }
    
    companion object {
        private val SHADERTOY_OUTPUT_WINDOW_KEY = 
            com.intellij.openapi.util.Key.create<ShadertoyConsoleWindow>("SHADERTOY_OUTPUT_WINDOW")
        
        /**
         * 获取项目的ShadertoyOutputWindow实例
         */
        fun getInstance(project: Project): ShadertoyConsoleWindow? {
            return project.getUserData(SHADERTOY_OUTPUT_WINDOW_KEY)
        }
    }
}

