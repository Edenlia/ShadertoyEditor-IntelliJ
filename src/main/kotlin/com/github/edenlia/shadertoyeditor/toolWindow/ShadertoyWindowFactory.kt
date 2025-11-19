package com.github.edenlia.shadertoyeditor.toolWindow

import com.github.edenlia.shadertoyeditor.dialogs.CreateShadertoyProjectDialog
import com.github.edenlia.shadertoyeditor.services.ShaderCompileService
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*

/**
 * Shadertoy主窗口工厂
 * 
 * 提供项目列表和管理功能
 */
class ShadertoyWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val shadertoyWindow = ShadertoyWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(shadertoyWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Shadertoy主窗口
     * 
     * 显示项目列表，提供项目管理和编译功能
     */
    class ShadertoyWindow(private val toolWindow: ToolWindow) {

        private val project = toolWindow.project
        private val projectManager = ShadertoyProjectManager.getInstance(project)
        private val shaderCompileService = project.service<ShaderCompileService>()
        
        private val projectListModel = DefaultListModel<ShadertoyProject>()
        private val projectList = JBList(projectListModel)

        /**
         * 创建窗口内容
         */
        fun getContent(): JComponent {
            val mainPanel = JPanel(BorderLayout())
            
            // 顶部工具栏
            val toolbar = createToolbar()
            
            // 项目列表
            setupProjectList()
            val scrollPane = JBScrollPane(projectList)
            
            mainPanel.add(toolbar, BorderLayout.NORTH)
            mainPanel.add(scrollPane, BorderLayout.CENTER)
            
            // 加载项目列表
            loadProjects()
            
            return mainPanel
        }
        
        /**
         * 创建工具栏
         */
        private fun createToolbar(): JPanel {
            val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
            
            // New Project 按钮
            toolbar.add(JButton("New Project").apply {
                addActionListener { onNewProject() }
            })
            
            // Remove Project 按钮
            toolbar.add(JButton("Remove Project").apply {
                addActionListener { onRemoveProject() }
            })
            
            // Compile 按钮
            toolbar.add(JButton("Compile").apply {
                addActionListener { onCompile() }
            })
            
            return toolbar
        }
        
        /**
         * 设置项目列表
         */
        private fun setupProjectList() {
            projectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            
            // 自定义渲染器
            projectList.cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val comp = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus
                    )
                    if (value is ShadertoyProject) {
                        text = "${value.name} (${value.path})"
                    }
                    return comp
                }
            }
            
            // 监听选择变化
            projectList.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    onProjectSelected()
                }
            }
        }
        
        /**
         * 加载项目列表
         */
        private fun loadProjects() {
            projectListModel.clear()
            projectManager.getAllProjects().forEach {
                projectListModel.addElement(it)
            }
        }
        
        /**
         * 新建项目
         */
        private fun onNewProject() {
            val dialog = CreateShadertoyProjectDialog(project)
            if (dialog.showAndGet()) {
                try {
                    val newProject = projectManager.createProject(
                        dialog.getProjectName(),
                        dialog.getProjectPath()
                    )
                    projectListModel.addElement(newProject)
                    projectList.setSelectedValue(newProject, true)
                    
                    // 成功通知
                    Notifications.Bus.notify(
                        Notification(
                            "Shadertoy Editor",
                            "Project Created",
                            "Created '${newProject.name}' at ${newProject.path}/Image.glsl",
                            NotificationType.INFORMATION
                        )
                    )
                    
                    thisLogger().info("[ShadertoyWindow] Project created: ${newProject.name}")
                    
                } catch (e: IllegalArgumentException) {
                    // 校验错误
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Failed to create project",
                        "Validation Error"
                    )
                } catch (e: Exception) {
                    // 其他错误
                    Messages.showErrorDialog(
                        project,
                        "Failed to create project: ${e.message}",
                        "Error"
                    )
                    thisLogger().error("[ShadertoyWindow] Failed to create project", e)
                }
            }
        }
        
        /**
         * 移除项目
         */
        private fun onRemoveProject() {
            val selected = projectList.selectedValue
            if (selected == null) {
                Messages.showWarningDialog(
                    project,
                    "Please select a project to remove",
                    "No Selection"
                )
                return
            }
            
            val result = Messages.showYesNoDialog(
                project,
                "Remove project '${selected.name}'?\n(Files will NOT be deleted)",
                "Confirm Remove",
                Messages.getQuestionIcon()
            )
            
            if (result == Messages.YES) {
                projectManager.removeProject(selected)
                projectListModel.removeElement(selected)
                
                thisLogger().info("[ShadertoyWindow] Project removed: ${selected.name}")
            }
        }
        
        /**
         * 项目选择变化
         */
        private fun onProjectSelected() {
            val selected = projectList.selectedValue
            projectManager.setCurrentProject(selected)
            
            if (selected != null) {
                thisLogger().info("[ShadertoyWindow] Project selected: ${selected.name}")
            }
        }
        
        /**
         * 编译按钮点击
         */
        private fun onCompile() {
            val currentProject = projectManager.getCurrentProject()
            if (currentProject == null) {
                // 友好提示
                Notifications.Bus.notify(
                    Notification(
                        "Shadertoy Editor",
                        "No Project Selected",
                        "Please select a Shadertoy project from the list first",
                        NotificationType.WARNING
                    )
                )
                return
            }
            
            compileShader()
        }
        
        /**
         * 编译并加载shader到渲染器
         */
        private fun compileShader() {
            val currentProject = projectManager.getCurrentProject()
            if (currentProject == null) {
                return
            }
            
            // 检查是否处于索引构建模式
            if (DumbService.isDumb(project)) {
                thisLogger().info("[ShadertoyWindow] Cannot compile shader during indexing, will retry when indexing is complete")
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
                    thisLogger().warn("[ShadertoyWindow] ShadertoyConsole window not found")
                    Messages.showWarningDialog(
                        project,
                        "Please open the ShadertoyConsole window first",
                        "Window Not Found"
                    )
                    return
                }
                
                // 编译shader代码
                val shaderCode = shaderCompileService.compileProject(currentProject)
                
                // 加载到渲染后端
                outputWindow.getRenderBackend().loadShader(shaderCode)
                
                thisLogger().info("[ShadertoyWindow] Shader compiled and loaded successfully: ${currentProject.name}")
                
            } catch (e: Exception) {
                thisLogger().error("[ShadertoyWindow] Failed to compile shader", e)
                Messages.showErrorDialog(
                    project,
                    "Compilation failed: ${e.message}",
                    "Shader Compilation Error"
                )
            }
        }
    }
}

