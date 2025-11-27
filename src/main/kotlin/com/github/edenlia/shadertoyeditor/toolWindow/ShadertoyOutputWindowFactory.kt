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
import com.github.edenlia.shadertoyeditor.services.RenderBackendService
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*


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
        private val messageBusConnection: MessageBusConnection
        
        // 容器和错误显示组件
        private val containerPanel: JLayeredPane = JLayeredPane()
        private val errorOverlay: ErrorOverlayPanel
        private val renderComponent: JComponent

        init {
            // 从 Service 获取 RenderBackend（懒加载，每个 Project 一个实例）
            renderBackend = project.service<RenderBackendService>().getBackend()
            thisLogger().info("[ShadertoyOutputWindow] Got RenderBackend from service")
            
            // 获取渲染组件
            renderComponent = renderBackend.getRootComponent()
            
            // 创建错误显示面板
            errorOverlay = ErrorOverlayPanel()
            errorOverlay.isVisible = false
            
            // 设置容器布局
            setupContainer()

            // 订阅参考分辨率变更事件（Settings 修改时）
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)

            // 监听 ToolWindow 尺寸变化
            subscribeToToolWindowResize()


            
            // 监听编译事件
            subscribeToSTE_IDEProjectEvent()
        }
        
        /**
         * 设置容器面板和组件层级
         */
        private fun setupContainer() {
            // 将渲染组件添加到默认层
            containerPanel.add(renderComponent, JLayeredPane.PALETTE_LAYER)
            
            // 将错误面板添加到调色板层（更高层级）
            containerPanel.add(errorOverlay, JLayeredPane.DEFAULT_LAYER)
            
            // 监听容器尺寸变化，同步调整子组件
            containerPanel.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    val width = containerPanel.width
                    val height = containerPanel.height
                    
                    // 渲染组件铺满整个容器
                    renderComponent.setBounds(0, 0, width, height)
                    
                    // 错误面板也铺满（内部会居中显示内容）
                    errorOverlay.setBounds(0, 300, width, height)
                    
                    // 通知后端容器尺寸变化
                    renderBackend.onContainerResized(width, height)
                }
            })
        }

        /**
         * 监听 ToolWindow 尺寸变化（已移至 setupContainer 中统一处理）
         */
        private fun subscribeToToolWindowResize() {
            // 尺寸变化的监听已经在 setupContainer() 的 containerPanel.addComponentListener 中处理
            // 保留此方法以便将来扩展
        }
        
        /**
         * 监听编译事件，显示/隐藏错误信息
         */
        private fun subscribeToSTE_IDEProjectEvent() {
            project.messageBus.connect(this).subscribe(
                STE_IDEProjectEventListener.TOPIC,
                object : STE_IDEProjectEventListener {
                    override fun onShaderCompiled(
                        shadertoyProject: ShadertoyProject?,
                        success: Boolean,
                        message: String?
                    ) {
                        if (shadertoyProject == null) return
                        handleCompileResult(shadertoyProject, success, message?: "Unknown error")
                    }
                }
            )
        }
        
        /**
         * 处理编译结果
         */
        private fun handleCompileResult(
            shadertoyProject: ShadertoyProject,
            success: Boolean,
            message: String
        ) {
            // 获取当前显示的项目
            val projectManager = project.service<ShadertoyProjectManager>()
            val currentProject = projectManager.getCurrentShadertoyProject()
            
            // 只处理当前项目的编译结果
            if (currentProject != shadertoyProject) {
                thisLogger().info("[ShadertoyOutputWindow] Ignoring compile result for non-current project: ${shadertoyProject.name}")
                return
            }
            
            SwingUtilities.invokeLater {
                if (success) {
                    // 编译成功：隐藏错误面板，显示渲染组件
                    errorOverlay.isVisible = false
                } else {
//                    if (renderComponent.parent != null) {
//                        containerPanel.remove(renderComponent)
//                    }

                    errorOverlay.setErrorMessage(message)
                    errorOverlay.isVisible = true
                    thisLogger().info("[ShadertoyOutputWindow] Compilation failed, showing error overlay")
                }
                containerPanel.revalidate()
                containerPanel.repaint()
            }
        }

        /**
         * 获取窗口内容组件
         */
        fun getContent(): JComponent {
            return containerPanel
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
    
    /**
     * 半透明错误显示面板
     * 固定尺寸 400x200，居中显示错误信息
     */
    private class ErrorOverlayPanel : JPanel() {
        private var errorMessage: String = ""
        
        // 错误信息显示区域的固定尺寸
        private val errorBoxWidth = 400
        private val errorBoxHeight = 200
        
        init {
            isOpaque = true  // 关键：允许透明背景
            layout = null     // 使用绝对定位
        }
        
        /**
         * 设置错误信息
         */
        fun setErrorMessage(message: String) {
            this.errorMessage = message
            repaint()
        }
        
        /**
         * 自定义绘制：半透明背景 + 居中文字
         */
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            
            if (!isVisible || errorMessage.isEmpty()) {
                return
            }
            
            val g2d = g as Graphics2D
            
            // 启用抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            
            // 计算居中位置
            val x = (width - errorBoxWidth) / 2
            val y = (height - errorBoxHeight) / 2
            
            // 绘制半透明黑色背景（70% 不透明度）
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f)
            g2d.color = Color(30, 30, 30)  // 深灰色背景
            g2d.fillRoundRect(x, y, errorBoxWidth, errorBoxHeight, 10, 10)
            
            // 绘制边框
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
            g2d.color = Color(200, 80, 80)  // 红色边框
            g2d.stroke = BasicStroke(2f)
            g2d.drawRoundRect(x, y, errorBoxWidth, errorBoxHeight, 10, 10)
            
            // 恢复完全不透明来绘制文字
            g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)
            
            // 绘制标题
            g2d.color = Color(255, 100, 100)  // 红色标题
            g2d.font = Font("Sans-Serif", Font.BOLD, 16)
            val title = "Compilation Error"
            val titleMetrics = g2d.fontMetrics
            val titleX = x + (errorBoxWidth - titleMetrics.stringWidth(title)) / 2
            g2d.drawString(title, titleX, y + 35)
            
            // 绘制错误信息（居中对齐，支持多行）
            g2d.color = Color.WHITE
            g2d.font = Font("Monospaced", Font.PLAIN, 12)
            
            // 截断长文本并支持简单的多行显示
            val maxCharsPerLine = 45
            val lines = mutableListOf<String>()
            var remainingText = errorMessage
            
            // 简单的文本换行
            while (remainingText.isNotEmpty()) {
                if (remainingText.length <= maxCharsPerLine) {
                    lines.add(remainingText)
                    break
                }
                // 尝试在空格处断行
                var breakPos = remainingText.lastIndexOf(' ', maxCharsPerLine)
                if (breakPos <= 0) {
                    breakPos = maxCharsPerLine
                }
                lines.add(remainingText.substring(0, breakPos))
                remainingText = remainingText.substring(breakPos).trim()
                
                // 最多显示5行
                if (lines.size >= 5) {
                    if (remainingText.isNotEmpty()) {
                        lines[4] = lines[4].take(maxCharsPerLine - 3) + "..."
                    }
                    break
                }
            }
            
            // 绘制每一行（居中对齐）
            val lineHeight = g2d.fontMetrics.height
            val totalTextHeight = lines.size * lineHeight
            var textY = y + (errorBoxHeight - totalTextHeight) / 2 + 20
            
            for (line in lines) {
                val lineWidth = g2d.fontMetrics.stringWidth(line)
                val lineX = x + (errorBoxWidth - lineWidth) / 2
                g2d.drawString(line, lineX, textY)
                textY += lineHeight
            }
        }
    }
}

