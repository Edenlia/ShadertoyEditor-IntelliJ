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
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
            
            // 自定义渲染器 - 激活的项目显示粗体
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
                        
                        // 如果是当前激活的项目，显示粗体
                        val currentProj = projectManager.getCurrentProject()
                        if (currentProj != null && currentProj.name == value.name) {
                            font = font.deriveFont(Font.BOLD)
                        }
                    }
                    return comp
                }
            }
            
            // 双击激活项目
            projectList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {  // 双击
                        val index = projectList.locationToIndex(e.point)
                        if (index >= 0) {
                            val project = projectListModel.getElementAt(index)
                            onProjectActivated(project)
                        }
                    }
                }
            })
        }
        
        /**
         * 加载项目列表
         */
        private fun loadProjects() {
            projectListModel.clear()
            projectManager.getAllProjects().forEach {
                projectListModel.addElement(it)
            }
            
            // 恢复选中状态
            restoreSelection()
        }
        
        /**
         * 恢复选中状态（ToolWindow重新打开时）
         */
        private fun restoreSelection() {
            val currentProj = projectManager.getCurrentProject()
            if (currentProj != null) {
                // 在列表中找到并选中（视觉上高亮）
                for (i in 0 until projectListModel.size()) {
                    if (projectListModel.getElementAt(i).name == currentProj.name) {
                        projectList.setSelectedValue(currentProj, true)
                        break
                    }
                }
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
                    
                    // 自动激活新创建的项目
                    onProjectActivated(newProject)
                    
                    // 成功通知
                    Notifications.Bus.notify(
                        Notification(
                            "Shadertoy Editor",
                            "Project Created",
                            "Created '${newProject.name}' at ${newProject.path}/Image.glsl",
                            NotificationType.INFORMATION
                        )
                    )
                    
                    thisLogger().info("[ShadertoyWindow] Project created and activated: ${newProject.name}")
                    
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
                // 如果删除的是当前激活的项目，设为null
                val currentProj = projectManager.getCurrentProject()
                if (currentProj != null && currentProj.name == selected.name) {
                    projectManager.setCurrentProject(null)
                    thisLogger().info("[ShadertoyWindow] Current active project removed, set to null")
                }
                
                projectManager.removeProject(selected)
                projectListModel.removeElement(selected)
                
                // 刷新列表显示（移除粗体）
                projectList.repaint()
                
                thisLogger().info("[ShadertoyWindow] Project removed: ${selected.name}")
            }
        }
        
        /**
         * 激活项目（双击时）
         */
        private fun onProjectActivated(project: ShadertoyProject) {
            projectManager.setCurrentProject(project)
            
            // 选中该项（视觉高亮）
            projectList.setSelectedValue(project, true)
            
            // 刷新列表显示（更新粗体）
            projectList.repaint()
            
            thisLogger().info("[ShadertoyWindow] Project activated: ${project.name}")
        }
        
        /**
         * 编译按钮点击
         */
        private fun onCompile() {
            shaderCompileService.compileShadertoyProject()
        }
    }
}

