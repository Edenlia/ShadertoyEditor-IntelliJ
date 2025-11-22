package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.messages.MessageBusConnection
import com.github.edenlia.shadertoyeditor.renderBackend.impl.jcef.JCefBackend
import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.renderBackend.impl.jogl.JoglBackend
import com.github.edenlia.shadertoyeditor.listeners.RefCanvasResolutionChangedListener
import com.github.edenlia.shadertoyeditor.listeners.ShadertoyProjectChangedListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings
import com.intellij.openapi.diagnostic.thisLogger
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
     * Shadertoy输出窗口，使用可配置的渲染后端显示Shader渲染内容
     */
    class ShadertoyOutputWindow(
        private val project: Project,
        private val toolWindow: ToolWindow
    ) : com.intellij.openapi.Disposable {
        
        private val renderBackend: RenderBackend
        private val messageBusConnection: MessageBusConnection
        
        init {
            // 根据配置创建渲染后端
            val config = ShadertoySettings.getInstance().getConfig()
            val backendType = config.backendType.uppercase()
            
            renderBackend = when (backendType) {
                "JOGL" -> JoglBackend(project, toolWindow.component)
                else -> JCefBackend(project, toolWindow.component)  // 默认使用JCEF
            }
            
            // 订阅参考分辨率变更事件（Settings 修改时）
            messageBusConnection = subscribeToRefCanvasResolutionChanges()
            
            // 订阅项目切换事件
            subscribeToProjectChanges()
            
            // 监听 ToolWindow 尺寸变化
            subscribeToToolWindowResize()
            
            // 初始化分辨率
            applyInitialResolution()
        }
        
        /**
         * 订阅分辨率变更事件
         * @return MessageBusConnection 连接对象，用于后续手动断开
         */
        private fun subscribeToRefCanvasResolutionChanges(): MessageBusConnection {
            val connection = ApplicationManager.getApplication().messageBus.connect(this)
            connection.subscribe(RefCanvasResolutionChangedListener.TOPIC, object : RefCanvasResolutionChangedListener {
                override fun onRefCanvasResolutionChanged(width: Int, height: Int) {
                    // 在UI线程中更新分辨率
                    SwingUtilities.invokeLater {
                        updateRefCanvasResolution(width, height)
                    }
                }
            })
            return connection
        }


        /**
         * 更新参考分辨率（来自 Settings）
         */
        private fun updateRefCanvasResolution(width: Int, height: Int) {
            renderBackend.updateRefCanvasResolution(width, height)
        }
        
        /**
         * 订阅项目切换事件
         */
        private fun subscribeToProjectChanges() {
            messageBusConnection.subscribe(
                ShadertoyProjectChangedListener.TOPIC,
                object : ShadertoyProjectChangedListener {
                    override fun onProjectChanged(project: ShadertoyProject?) {
                        if (project == null) {
                            // 清空渲染 - 显示空白
                            clearRender()
                            thisLogger().info("[ShadertoyOutputWindow] Project cleared, showing blank")
                        } else {
                            thisLogger().info("[ShadertoyOutputWindow] Project changed to: ${project.name}")
                            // 有项目选中时，不做任何操作，等待用户点击Compile
                        }
                    }
                }
            )
        }
        
        /**
         * 清空渲染内容
         */
        private fun clearRender() {
            // 加载一个空shader，显示深灰色背景
            val emptyShader = """
                #version 330 core
                precision highp float;
                out vec4 fragColor;
                
                void main() {
                    fragColor = vec4(0.15, 0.15, 0.15, 1.0); // 深灰色背景
                }
            """.trimIndent()
            
            try {
                renderBackend.loadShader(emptyShader)
            } catch (e: Exception) {
                thisLogger().warn("[ShadertoyOutputWindow] Failed to load empty shader", e)
            }
        }
        
        /**
         * 监听 ToolWindow 尺寸变化
         */
        private fun subscribeToToolWindowResize() {
            // 监听 ToolWindow component 的尺寸变化
            toolWindow.component.addComponentListener(object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent?) {
                    // 当 ToolWindow 大小改变时，重新计算渲染分辨率
                    setToolWindowResolution()
                }
            })
        }
        
        /**
         * 处理 ToolWindow 尺寸变化
         * 使用当前的参考分辨率，根据新的 ToolWindow 大小重新计算真实渲染分辨率
         */
        private fun setToolWindowResolution() {
            val config = ShadertoySettings.getInstance().getConfig()
            renderBackend.updateRefCanvasResolution(config.canvasRefWidth, config.canvasRefHeight)
        }
        
        /**
         * 初始化分辨率（ToolWindow 首次打开时）
         */
        private fun applyInitialResolution() {
            SwingUtilities.invokeLater {
                // 延迟执行，确保 ToolWindow 完全初始化
                Thread.sleep(500)
                setToolWindowResolution()
            }
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
            // 断开 MessageBus 连接
            messageBusConnection.disconnect()
            // 释放渲染后端资源
            renderBackend.dispose()
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

