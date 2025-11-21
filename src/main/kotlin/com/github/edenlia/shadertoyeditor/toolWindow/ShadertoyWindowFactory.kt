package com.github.edenlia.shadertoyeditor.toolWindow

import com.github.edenlia.shadertoyeditor.listeners.ShadertoyProjectChangedListener
import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
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
        
        private val projectListModel = DefaultListModel<ShadertoyProject>()
        private val projectList = JBList(projectListModel)
        
        init {
            // 订阅项目变更事件，自动刷新列表
            subscribeToProjectChanges()
        }

        /**
         * 创建窗口内容
         */
        fun getContent(): JComponent {
            val mainPanel = JPanel(BorderLayout())
            
            // 顶部工具栏（使用 ActionToolbar）
            val toolbar = createActionToolbar()
            
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
         * 创建 ActionToolbar (使用 IntelliJ Action 系统)
         */
        private fun createActionToolbar(): JComponent {
            val actionManager = ActionManager.getInstance()
            
            // 创建主工具栏面板
            val toolbarPanel = JPanel(BorderLayout())
            
            // 左侧工具栏：加号、减号、刷新、分隔符、设置
            val leftGroup = actionManager.getAction("ShadertoyEditor.ToolbarActions") as DefaultActionGroup
            val leftToolbar = actionManager.createActionToolbar(
                "ShadertoyEditor.Toolbar.Left",
                leftGroup,
                true  // horizontal
            )
            leftToolbar.targetComponent = projectList
            
            // 右侧工具栏：播放按钮
            val rightGroup = actionManager.getAction("ShadertoyEditor.ToolbarActionsRight") as DefaultActionGroup
            val rightToolbar = actionManager.createActionToolbar(
                "ShadertoyEditor.Toolbar.Right",
                rightGroup,
                true  // horizontal
            )
            rightToolbar.targetComponent = projectList
            
            // 布局：左对齐 + 右对齐
            toolbarPanel.add(leftToolbar.component, BorderLayout.WEST)
            toolbarPanel.add(rightToolbar.component, BorderLayout.EAST)
            
            return toolbarPanel
        }
        
        /**
         * 订阅项目变更事件
         */
        private fun subscribeToProjectChanges() {
            ApplicationManager.getApplication().messageBus
                .connect()
                .subscribe(
                    ShadertoyProjectChangedListener.TOPIC,
                    object : ShadertoyProjectChangedListener {
                        override fun onProjectChanged(project: ShadertoyProject?) {
                            // 项目变更时刷新列表
                            SwingUtilities.invokeLater {
                                loadProjects()
                                
                                // 如果有新激活的项目，自动打开 Image.glsl
                                if (project != null) {
                                    openImageGlslFile(project)
                                }
                            }
                        }
                    }
                )
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
         * 打开 Image.glsl 文件
         * 
         * @param shadertoyProject 要打开的项目
         */
        private fun openImageGlslFile(shadertoyProject: ShadertoyProject) {
            val projectBasePath = project.basePath
            if (projectBasePath == null) {
                thisLogger().warn("[ShadertoyWindow] Cannot open file: project base path is null")
                return
            }
            
            try {
                // 1. 获取 Image.glsl 的完整路径
                val imageGlslPath = shadertoyProject.getImageGlslPath(projectBasePath)
                
                // 2. 使用 ReadAction 获取 VirtualFile（VFS 访问需要读锁）
                val virtualFile = ApplicationManager.getApplication().runReadAction<VirtualFile?> {
                    LocalFileSystem.getInstance().findFileByPath(imageGlslPath)
                }
                
                // 3. 检查文件是否存在
                if (virtualFile == null || !virtualFile.exists()) {
                    handleMissingFile(shadertoyProject, imageGlslPath)
                    return
                }
                
                // 4. 打开文件并聚焦
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
                thisLogger().info("[ShadertoyWindow] Opened Image.glsl: $imageGlslPath")
                
            } catch (e: Exception) {
                thisLogger().error("[ShadertoyWindow] Failed to open Image.glsl", e)
                Messages.showErrorDialog(
                    project,
                    "Failed to open Image.glsl: ${e.message}",
                    "File Open Error"
                )
            }
        }
        
        /**
         * 处理文件丢失的情况
         * 
         * @param shadertoyProject 文件丢失的项目
         * @param filePath 丢失的文件路径
         */
        private fun handleMissingFile(shadertoyProject: ShadertoyProject, filePath: String) {
            thisLogger().warn("[ShadertoyWindow] Image.glsl not found: $filePath")
            
            // 1. 弹窗报错
            Messages.showErrorDialog(
                project,
                "Image.glsl not found at:\n$filePath\n\nThe project '${shadertoyProject.name}' will be removed from the list.",
                "File Not Found"
            )
            
            // 2. 从配置中删除该项目
            // removeProject 会触发项目变更事件，列表会自动刷新
            projectManager.removeProject(shadertoyProject)
            
            thisLogger().info("[ShadertoyWindow] Removed project due to missing file: ${shadertoyProject.name}")
        }
    }
}

