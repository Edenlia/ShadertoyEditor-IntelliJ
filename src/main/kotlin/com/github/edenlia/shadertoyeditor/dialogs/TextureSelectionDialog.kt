package com.github.edenlia.shadertoyeditor.dialogs

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Texture选择对话框
 * 
 * 提供两种选择方式：
 * 1. 从插件resources/textures选择
 * 2. 从项目本地文件选择（jpg/png）
 */
class TextureSelectionDialog(
    private val project: Project,
    parent: JComponent?
) : DialogWrapper(project) {
    
    private var selectedTexturePath: String? = null
    
    // UI组件 - 延迟初始化（在createCenterPanel中初始化）
    private var pluginTextureList: JBList<String>? = null
    private var pluginTextureModel: DefaultListModel<String>? = null
    
    init {
        title = "Select Texture"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        // 初始化插件texture列表（延迟到createCenterPanel中）
        if (pluginTextureModel == null) {
            val model = DefaultListModel<String>()
            pluginTextureModel = model
            loadPluginTextures()
            
            pluginTextureList = JBList(model).apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                addListSelectionListener { e ->
                    if (!e.valueIsAdjusting && selectedValue != null) {
                        selectedTexturePath = "\$plugin/resources/textures/${selectedValue}"
                    }
                }
            }
        }
        
        val panel = JPanel(BorderLayout())
        
        // 创建TabbedPane
        val tabbedPane = JTabbedPane()
        
        // Tab 1: Plugin Textures
        val pluginPanel = createPluginTexturePanel()
        tabbedPane.addTab("Plugin Textures", pluginPanel)
        
        // Tab 2: Local File
        val localFilePanel = createLocalFilePanel()
        tabbedPane.addTab("Local File", localFilePanel)
        
        panel.add(tabbedPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建插件texture面板
     */
    private fun createPluginTexturePanel(): JComponent {
        val panel = JPanel(BorderLayout())
        
        // 确保pluginTextureList已初始化
        val list = pluginTextureList
        val model = pluginTextureModel
        if (list == null || model == null) {
            thisLogger().error("[TextureSelectionDialog] pluginTextureList or pluginTextureModel not initialized!")
            panel.add(JLabel("No plugin textures available"), BorderLayout.CENTER)
            return panel
        }
        
        val scrollPane = JBScrollPane(list)
        scrollPane.preferredSize = Dimension(400, 300)
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 双击选择
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val index = list.locationToIndex(e.point)
                    if (index >= 0 && index < model.size) {
                        val textureName = model.getElementAt(index)
                        selectedTexturePath = "\$plugin/resources/textures/$textureName"
                        close(OK_EXIT_CODE)
                    }
                }
            }
        })
        
        return panel
    }
    
    /**
     * 创建本地文件选择面板
     */
    private fun createLocalFilePanel(): JComponent {
        val panel = JPanel(java.awt.BorderLayout())
        
        val button = JButton("Browse...").apply {
            addActionListener { selectLocalFile() }
        }
        
        val label = JLabel("Select a JPG or PNG file from your project").apply {
            horizontalAlignment = SwingConstants.CENTER
        }
        
        val buttonPanel = JPanel()
        buttonPanel.add(button)
        
        panel.add(buttonPanel, java.awt.BorderLayout.CENTER)
        panel.add(label, java.awt.BorderLayout.SOUTH)
        
        return panel
    }
    
    /**
     * 选择本地文件
     */
    private fun selectLocalFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false).apply {
            title = "Select Texture File"
            description = "Choose a JPG or PNG image file"
            // 文件过滤器
            withFileFilter { file ->
                val name = file.name.lowercase()
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            }
        }
        
        val projectBasePath = project.basePath
        val baseDir = if (projectBasePath != null) {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(projectBasePath)
        } else {
            null
        }
        
        FileChooser.chooseFiles(descriptor, project, baseDir) { files ->
            if (files.isNotEmpty()) {
                val selectedFile = files[0]
                val projectBasePath = project.basePath
                
                if (projectBasePath != null) {
                    // 计算相对路径
                    val absolutePath = selectedFile.path
                    val relativePath = if (absolutePath.startsWith(projectBasePath)) {
                        absolutePath.removePrefix(projectBasePath).removePrefix("/")
                    } else {
                        // 如果不在项目目录下，不允许选择
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "The selected file must be within the project directory.",
                            "Invalid File Location"
                        )
                        return@chooseFiles
                    }
                    
                    selectedTexturePath = relativePath
                    close(OK_EXIT_CODE)
                } else {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "Project base path is null",
                        "Error"
                    )
                }
            }
        }
    }
    
    /**
     * 加载插件resources/textures下的texture列表
     * 
     * 注意：由于资源在jar中，无法直接列出目录内容
     * 这里使用硬编码列表，后续可以通过配置文件扩展
     */
    private fun loadPluginTextures() {
        thisLogger().info("[TextureSelectionDialog] Loading plugin textures...")
        val resourceNames = mutableListOf<String>()
        
        // 硬编码的texture文件列表
        // 注意：文件名必须与resources/textures目录下的文件完全匹配（包括大小写）
        val knownTextures = listOf(
            "T_RustyMetal.jpg"
            // 可以在这里添加更多texture文件名
        )
        
        // 验证每个texture是否存在
        knownTextures.forEach { textureName ->
            val resourcePath = "/textures/$textureName"
            val resource = javaClass.getResource(resourcePath)
            if (resource != null) {
                resourceNames.add(textureName)
                thisLogger().info("[TextureSelectionDialog] Found plugin texture: $textureName")
            } else {
                thisLogger().warn("[TextureSelectionDialog] Plugin texture not found: $textureName (path: $resourcePath)")
            }
        }
        
        // 尝试通过classloader扫描（这种方法在jar中可能不可靠，但值得尝试）
        try {
            val classLoader = javaClass.classLoader
            val textureDir = "textures"
            
            // 尝试获取资源URL
            val resourceUrl = classLoader.getResource(textureDir)
            if (resourceUrl != null) {
                thisLogger().info("[TextureSelectionDialog] Found texture directory: $resourceUrl")
                
                // 如果是file协议（开发环境），可以列出文件
                if (resourceUrl.protocol == "file") {
                    try {
                        val file = java.io.File(resourceUrl.toURI())
                        if (file.isDirectory) {
                            file.listFiles()?.forEach { file ->
                                if (file.isFile && (file.name.endsWith(".png", ignoreCase = true) || 
                                                    file.name.endsWith(".jpg", ignoreCase = true) ||
                                                    file.name.endsWith(".jpeg", ignoreCase = true))) {
                                    if (!resourceNames.contains(file.name)) {
                                        resourceNames.add(file.name)
                                        thisLogger().info("[TextureSelectionDialog] Found texture via file scan: ${file.name}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        thisLogger().warn("[TextureSelectionDialog] Failed to scan texture directory", e)
                    }
                }
            } else {
                thisLogger().info("[TextureSelectionDialog] Texture directory not found via classloader")
            }
        } catch (e: Exception) {
            thisLogger().warn("[TextureSelectionDialog] Error scanning textures", e)
        }
        
        resourceNames.sort()
        thisLogger().info("[TextureSelectionDialog] Total plugin textures found: ${resourceNames.size}")
        
        pluginTextureModel?.let { model ->
            resourceNames.forEach { 
                model.addElement(it)
                thisLogger().info("[TextureSelectionDialog] Added to list: $it")
            }
        }
    }
    
    /**
     * 获取选择的texture路径
     * @return texture路径，null表示取消
     */
    fun getSelectedTexturePath(): String? {
        return selectedTexturePath
    }
}

