package com.github.edenlia.shadertoyeditor.toolWindow

import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
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

        // 动态获取，不缓存，确保在IDE项目切换时能获取到正确的project
        private fun getIDEProject(): Project = toolWindow.project
        private fun getShadertoyProjectManager(): ShadertoyProjectManager =
            ShadertoyProjectManager.getInstance(getIDEProject())
        
        private val shadertoyProjectListModel = DefaultListModel<ShadertoyProject>()
        private val shadertoyProjectList = JBList(shadertoyProjectListModel)
        private var texturePanel: TextureChannelPanel? = null
        private var contentPanel: JPanel? = null  // 保存contentPanel引用
        private var isRestoringSelection = false  // 标记是否正在恢复选择（避免触发事件）
        
        init {
            // 订阅项目变更事件，自动刷新列表
            subscribeToShadertoyProjectChanges()
            thisLogger().info("[ShadertoyWindow] Initialized")
        }

        /**
         * 创建窗口内容
         */
        fun getContent(): JComponent {
            thisLogger().info("[ShadertoyWindow] Creating content...")
            
            val mainPanel = JPanel(BorderLayout())
            
            // 顶部工具栏（使用 ActionToolbar）
            val toolbar = createActionToolbar()
            thisLogger().info("[ShadertoyWindow] Toolbar created")
            
            // 项目列表
            setupShadertoyProjectList()
            val scrollPane = JBScrollPane(shadertoyProjectList)
            thisLogger().info("[ShadertoyWindow] Project list setup complete")
            
            // 创建内容面板（项目列表 + texture面板）
            contentPanel = JPanel(BorderLayout())
            contentPanel!!.add(scrollPane, BorderLayout.CENTER)
            thisLogger().info("[ShadertoyWindow] Content panel created and scrollPane added")
            
            mainPanel.add(toolbar, BorderLayout.NORTH)
            mainPanel.add(contentPanel, BorderLayout.CENTER)
            thisLogger().info("[ShadertoyWindow] Main panel layout complete")
            
            // 加载项目列表
            loadShadertoyProjects()
            thisLogger().info("[ShadertoyWindow] Projects loaded: ${shadertoyProjectListModel.size()}")
            
            // 延迟初始化texture面板（确保组件已完全添加到父容器）
            SwingUtilities.invokeLater {
                thisLogger().info("[ShadertoyWindow] Invoking updateTexturePanel in EDT")
                updateTexturePanel()
            }
            
            thisLogger().info("[ShadertoyWindow] Content creation complete")
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
            leftToolbar.targetComponent = shadertoyProjectList
            
            // 右侧工具栏：播放按钮
            val rightGroup = actionManager.getAction("ShadertoyEditor.ToolbarActionsRight") as DefaultActionGroup
            val rightToolbar = actionManager.createActionToolbar(
                "ShadertoyEditor.Toolbar.Right",
                rightGroup,
                true  // horizontal
            )
            rightToolbar.targetComponent = shadertoyProjectList
            
            // 布局：左对齐 + 右对齐
            toolbarPanel.add(leftToolbar.component, BorderLayout.WEST)
            toolbarPanel.add(rightToolbar.component, BorderLayout.EAST)
            
            return toolbarPanel
        }
        
        /**
         * 订阅项目变更事件
         */
        private fun subscribeToShadertoyProjectChanges() {
            ApplicationManager.getApplication().messageBus
                .connect()
                .subscribe(
                    STE_IDEProjectEventListener.TOPIC,
                    object : STE_IDEProjectEventListener {
                        override fun onShadertoyProjectChanged(shadertoyProject: ShadertoyProject?) {
                            thisLogger().info("[ShadertoyWindow] Project changed event received: ${shadertoyProject?.name ?: "null"}")
                            // 项目变更时刷新列表
                            SwingUtilities.invokeLater {
                                thisLogger().info("[ShadertoyWindow] Processing project change in EDT")
                                loadShadertoyProjects()
                                
                                // 更新texture面板
                                thisLogger().info("[ShadertoyWindow] Updating texture panel after project change")
                                updateTexturePanel()
                                
                                // 如果有新激活的项目，自动打开 Image.glsl
                                if (shadertoyProject != null) {
                                    openImageGlslFile(shadertoyProject)
                                }
                            }
                        }
                    }
                )
        }
        
        /**
         * 设置项目列表
         */
        private fun setupShadertoyProjectList() {
            shadertoyProjectList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            
            // 自定义渲染器 - 激活的项目显示粗体
            shadertoyProjectList.cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): java.awt.Component {
                    val                     comp = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus
                    )
                    if (value is ShadertoyProject) {
                        text = "${value.name} (${value.path})"
                        
                        // 如果是当前激活的项目，显示粗体
                        val currentProj = getShadertoyProjectManager().getCurrentShadertoyProject()
                        if (currentProj != null && currentProj.name == value.name) {
                            font = font.deriveFont(Font.BOLD)
                        }
                    }
                    return comp
                }
            }
            
            // 双击激活项目
            shadertoyProjectList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {  // 双击
                        val index = shadertoyProjectList.locationToIndex(e.point)
                        if (index >= 0) {
                            val project = shadertoyProjectListModel.getElementAt(index)
                            onShadertoyProjectActivated(project)
                        }
                    }
                }
            })
            
            // 监听选择变化，更新texture面板
            // 注意：只在用户主动选择时更新，不响应程序化的选择（如restoreSelection）
            shadertoyProjectList.addListSelectionListener { e ->
                if (!e.valueIsAdjusting && !isRestoringSelection) {
                    thisLogger().info("[ShadertoyWindow] List selection changed (user action), updating texture panel")
                    updateTexturePanel()
                } else if (isRestoringSelection) {
                    thisLogger().info("[ShadertoyWindow] List selection changed (programmatic), ignoring")
                }
            }
        }
        
        /**
         * 加载项目列表
         */
        private fun loadShadertoyProjects() {
            thisLogger().info("[ShadertoyWindow] Loading projects...")
            shadertoyProjectListModel.clear()
            val projects = getShadertoyProjectManager().getAllProjects()
            thisLogger().info("[ShadertoyWindow] Found ${projects.size} projects")
            projects.forEach {
                shadertoyProjectListModel.addElement(it)
                thisLogger().info("[ShadertoyWindow] Added project to list: ${it.name}")
            }
            
            // 恢复选中状态
            restoreSelection()
            thisLogger().info("[ShadertoyWindow] Projects loaded, list size: ${shadertoyProjectListModel.size()}")
        }
        
        /**
         * 恢复选中状态（ToolWindow重新打开时）
         * 
         * 注意：这里只恢复视觉选中状态，不触发选择事件
         * 实际的texture面板显示由updateTexturePanel()根据currentProject决定
         */
        private fun restoreSelection() {
            val currentProj = getShadertoyProjectManager().getCurrentShadertoyProject()
            thisLogger().info("[ShadertoyWindow] Restoring selection, current project: ${currentProj?.name ?: "null"}")
            
            isRestoringSelection = true
            try {
                if (currentProj != null) {
                    // 在列表中找到并选中（视觉上高亮）
                    for (i in 0 until shadertoyProjectListModel.size()) {
                        if (shadertoyProjectListModel.getElementAt(i).name == currentProj.name) {
                            shadertoyProjectList.selectedIndex = i
                            thisLogger().info("[ShadertoyWindow] Selected project in list (visual only): ${currentProj.name}")
                            break
                        }
                    }
                } else {
                    thisLogger().info("[ShadertoyWindow] No current project to restore")
                    // 确保没有选中任何项目
                    shadertoyProjectList.clearSelection()
                }
            } finally {
                isRestoringSelection = false
            }
        }
        
        /**
         * 激活项目（双击时）
         */
        private fun onShadertoyProjectActivated(project: ShadertoyProject) {
            thisLogger().info("[ShadertoyWindow] Project activated: ${project.name}")
            getShadertoyProjectManager().setCurrentShadertoyProject(project)
            thisLogger().info("[ShadertoyWindow] Current project set in manager")
            
            // 选中该项（视觉高亮）
            shadertoyProjectList.setSelectedValue(project, true)
            thisLogger().info("[ShadertoyWindow] Project selected in list")
            
            // 刷新列表显示（更新粗体）
            shadertoyProjectList.repaint()
            
            // 更新texture面板
            thisLogger().info("[ShadertoyWindow] Updating texture panel after activation")
            updateTexturePanel()
            
            thisLogger().info("[ShadertoyWindow] Project activation complete: ${project.name}")
        }
        
        /**
         * 更新texture面板显示
         * 当项目被选中或激活时调用
         * 
         * 规则：只有当有项目被激活（currentProject不为null）时才显示texture面板
         * 仅选中但未激活的项目不显示texture面板
         */
        private fun updateTexturePanel() {
            thisLogger().info("[ShadertoyWindow] ========== updateTexturePanel called ==========")
            
            val selectedProject = shadertoyProjectList.selectedValue as? ShadertoyProject
            val currentProject = getShadertoyProjectManager().getCurrentShadertoyProject()
            
            thisLogger().info("[ShadertoyWindow] Selected project: ${selectedProject?.name ?: "null"}")
            thisLogger().info("[ShadertoyWindow] Current project: ${currentProject?.name ?: "null"}")
            
            // 只使用当前激活的项目，不显示仅选中但未激活的项目
            val targetProject = currentProject
            
            thisLogger().info("[ShadertoyWindow] Target project (only active): ${targetProject?.name ?: "null"}")
            
            // 检查contentPanel是否可用
            if (contentPanel == null) {
                thisLogger().warn("[ShadertoyWindow] contentPanel is null! Cannot add texture panel.")
                // 尝试通过projectList查找
                val foundPanel = shadertoyProjectList.parent?.parent as? JPanel
                if (foundPanel != null) {
                    thisLogger().info("[ShadertoyWindow] Found contentPanel via projectList.parent.parent")
                    contentPanel = foundPanel
                } else {
                    thisLogger().error("[ShadertoyWindow] Cannot find contentPanel, aborting texture panel update")
                    return
                }
            }
            
            thisLogger().info("[ShadertoyWindow] contentPanel is available: ${contentPanel != null}")
            
            // 移除旧的texture面板
            texturePanel?.let { oldPanel ->
                thisLogger().info("[ShadertoyWindow] Removing old texture panel")
                val parent = oldPanel.parent
                if (parent != null) {
                    parent.remove(oldPanel)
                    parent.revalidate()
                    parent.repaint()
                    thisLogger().info("[ShadertoyWindow] Old texture panel removed")
                } else {
                    thisLogger().warn("[ShadertoyWindow] Old texture panel has no parent")
                }
            }
            texturePanel = null
            
            // 如果有目标项目，创建新的texture面板
            if (targetProject != null) {
                thisLogger().info("[ShadertoyWindow] Creating new TextureChannelPanel for project: ${targetProject.name}")
                
                try {
                    texturePanel = TextureChannelPanel(getIDEProject(), targetProject)
                    thisLogger().info("[ShadertoyWindow] TextureChannelPanel created successfully")
                    
                    // 添加到内容面板的底部
                    contentPanel?.let { panel ->
                        panel.add(texturePanel, BorderLayout.SOUTH)
                        thisLogger().info("[ShadertoyWindow] Texture panel added to contentPanel")
                        
                        panel.revalidate()
                        thisLogger().info("[ShadertoyWindow] contentPanel revalidated")
                        
                        panel.repaint()
                        thisLogger().info("[ShadertoyWindow] contentPanel repainted")
                        
                        thisLogger().info("[ShadertoyWindow] ✅ Texture panel shown for project: ${targetProject.name}")
                    } ?: run {
                        thisLogger().error("[ShadertoyWindow] ❌ contentPanel is null, cannot add texture panel")
                    }
                } catch (e: Exception) {
                    thisLogger().error("[ShadertoyWindow] ❌ Failed to create TextureChannelPanel", e)
                }
            } else {
                thisLogger().info("[ShadertoyWindow] No project selected, texture panel hidden")
            }
            
            thisLogger().info("[ShadertoyWindow] ========== updateTexturePanel finished ==========")
        }
        
        /**
         * 打开 Image.glsl 文件
         * 
         * @param shadertoyProject 要打开的项目
         */
        private fun openImageGlslFile(shadertoyProject: ShadertoyProject) {
            val projectBasePath = getIDEProject().basePath
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
                FileEditorManager.getInstance(getIDEProject()).openFile(virtualFile, true)
                thisLogger().info("[ShadertoyWindow] Opened Image.glsl: $imageGlslPath")
                
            } catch (e: Exception) {
                thisLogger().error("[ShadertoyWindow] Failed to open Image.glsl", e)
                Messages.showErrorDialog(
                    getIDEProject(),
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
                getIDEProject(),
                "Image.glsl not found at:\n$filePath\n\nThe project '${shadertoyProject.name}' will be removed from the list.",
                "File Not Found"
            )
            
            // 2. 从配置中删除该项目
            // removeProject 会触发项目变更事件，列表会自动刷新
            getShadertoyProjectManager().removeShadertoyProject(shadertoyProject)
            
            thisLogger().info("[ShadertoyWindow] Removed project due to missing file: ${shadertoyProject.name}")
        }
    }
}

