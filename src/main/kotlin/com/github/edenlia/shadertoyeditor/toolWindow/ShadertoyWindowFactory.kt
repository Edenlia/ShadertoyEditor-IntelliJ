package com.github.edenlia.shadertoyeditor.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.github.edenlia.shadertoyeditor.MyBundle
import com.github.edenlia.shadertoyeditor.services.MyProjectService
import com.github.edenlia.shadertoyeditor.services.ShaderCompileService
import javax.swing.JButton
import javax.swing.SwingUtilities


class ShadertoyWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyWindow = ShadertoyWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(shadertoyWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
        
        // 等待索引构建完成后再自动触发第一次编译
        DumbService.getInstance(project).runWhenSmart {
            SwingUtilities.invokeLater {
                shadertoyWindow.compileShader()
            }
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    class ShadertoyWindow(private val toolWindow: ToolWindow) {

        private val project = toolWindow.project
        private val service = project.service<MyProjectService>()
        private val shaderCompileService = project.service<ShaderCompileService>()

        fun getContent() = JBPanel<JBPanel<*>>().apply {
            val label = JBLabel(MyBundle.message("randomLabel", "?"))

            add(label)
            add(JButton(MyBundle.message("shuffle")).apply {
                addActionListener {
                    label.text = MyBundle.message("randomLabel", service.getRandomNumber())
                }
            })
            
            // 添加 Compile 按钮
            add(JButton("Compile").apply {
                addActionListener {
                    compileShader()
                }
            })
        }
        
        /**
         * 编译并加载shader到渲染器
         */
        fun compileShader() {
            // 检查是否处于索引构建模式
            if (DumbService.isDumb(project)) {
                thisLogger().info("Cannot compile shader during indexing, will retry when indexing is complete")
                // 等待索引完成后再执行
                DumbService.getInstance(project).runWhenSmart {
                    compileShader()
                }
                return
            }
            
            try {
                // 获取 ShadertoyOutput 窗口实例
                val outputWindow = ShadertoyOutputWindowFactory.getInstance(project)
                if (outputWindow == null) {
                    thisLogger().warn("ShadertoyConsole window not found")
                    return
                }
                
                // 编译shader代码
                val shaderCode = shaderCompileService.compileShaderFromTemplate()
                
                // 加载到浏览器渲染器
                outputWindow.getBrowserComponent().loadShaderCode(shaderCode)
                
                thisLogger().info("Shader compiled and loaded successfully")
            } catch (e: Exception) {
                thisLogger().error("Failed to compile shader", e)
            }
        }
    }
}

