package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.edenlia.shadertoyeditor.renderBackend.impl.jcef.JCefBackend
import com.github.edenlia.shadertoyeditor.renderBackend.RenderBackend
import com.github.edenlia.shadertoyeditor.renderBackend.impl.lwjgl.LwjglBackend
import com.github.edenlia.shadertoyeditor.renderBackend.impl.jogl.JoglBackend
import com.github.edenlia.shadertoyeditor.listeners.ResolutionChangedListener
import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings
import javax.swing.JComponent
import javax.swing.SwingUtilities


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
     * Shadertoy输出窗口，使用可配置的渲染后端显示Shader渲染内容
     */
    class ShadertoyOutputWindow(private val project: Project) {
        
        private val renderBackend: RenderBackend
        
        init {
            // 根据配置创建渲染后端
            val config = ShadertoySettings.getInstance().getConfig()
            val backendType = config.backendType.uppercase()
            
            renderBackend = when (backendType) {
                "LWJGL" -> LwjglBackend(project)
                "JOGL" -> JoglBackend(project)
                else -> JCefBackend(project)  // 默认使用JCEF
            }
            
            // 订阅分辨率变更事件
            subscribeToResolutionChanges()
            
            // 初始化时应用当前配置的分辨率
            applyCurrentResolution()
        }
        
        /**
         * 订阅分辨率变更事件
         */
        private fun subscribeToResolutionChanges() {
            ApplicationManager.getApplication().messageBus.connect()
                .subscribe(ResolutionChangedListener.TOPIC, object : ResolutionChangedListener {
                    override fun onResolutionChanged(width: Int, height: Int) {
                        // 在UI线程中更新分辨率
                        SwingUtilities.invokeLater {
                            updateResolution(width, height)
                        }
                    }
                })
        }
        
        /**
         * 应用当前配置的分辨率
         */
        private fun applyCurrentResolution() {
            // 延迟执行，确保浏览器完全加载后再设置分辨率
            SwingUtilities.invokeLater {
                Thread.sleep(500) // 等待浏览器初始化
                
                val config = ShadertoySettings.getInstance().getConfig()
                updateResolution(config.targetWidth, config.targetHeight)
            }
        }
        
        /**
         * 更新分辨率
         */
        private fun updateResolution(width: Int, height: Int) {
            renderBackend.setResolution(width, height)
        }
        
        /**
         * 获取窗口内容组件
         */
        fun getContent(): JComponent {
            return renderBackend.getComponent()
        }
        
        /**
         * 获取渲染后端实例
         */
        fun getRenderBackend(): RenderBackend {
            return renderBackend
        }

        /**
         * 释放资源
         */
        fun dispose() {
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

