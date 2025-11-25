package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.model.ShadertoyProjectConfig
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.inspector.TagInspector
import java.io.File

/**
 * Shadertoy项目管理服务
 * 
 * 负责:
 * - 加载和保存项目配置文件(.shadertoy-editor.config.yml)
 * - 管理所有Shadertoy项目的CRUD操作
 * - 维护当前选中的项目
 * - 通过MessageBus通知项目切换事件
 */
@Service(Service.Level.PROJECT)
class ShadertoyProjectManager(private val project: Project) {
    
    companion object {
        private const val CONFIG_FILE_NAME = ".shadertoy-editor.config.yml"
        
        /**
         * 获取项目的ShadertoyProjectManager实例
         */
        fun getInstance(project: Project): ShadertoyProjectManager {
            return project.getService(ShadertoyProjectManager::class.java)
        }
    }
    
    private var config: ShadertoyProjectConfig = ShadertoyProjectConfig()
    private var currentProject: ShadertoyProject? = null
    
    init {
        thisLogger().info("[ShadertoyProjectManager] Initializing...")
        loadConfig()
        thisLogger().info("[ShadertoyProjectManager] Loaded ${config.projects.size} projects")
    }
    
    /**
     * 加载配置文件
     */
    fun loadConfig() {
        val configFile = getShadertoyEditorConfigFile()
        if (configFile.exists()) {
            try {
                // SnakeYAML 2.x 需要 LoaderOptions，并允许加载特定类型
                val loaderOptions = LoaderOptions().apply {
                    // 允许加载我们的配置类
                    tagInspector = TagInspector { tag ->
                        // 允许标准YAML类型和我们的配置类
                        tag.value.startsWith("tag:yaml.org,2002:") ||
                        tag.className == ShadertoyProjectConfig::class.java.name ||
                        tag.className == ShadertoyProject::class.java.name
                    }
                }
                val constructor = Constructor(ShadertoyProjectConfig::class.java, loaderOptions)
                val yaml = Yaml(constructor)
                configFile.inputStream().use { inputStream ->
                    config = yaml.load(inputStream) ?: ShadertoyProjectConfig()
                }
                thisLogger().info("[ShadertoyProjectManager] Config loaded from ${configFile.absolutePath}")
            } catch (e: Exception) {
                thisLogger().error("[ShadertoyProjectManager] Failed to load config", e)
                config = ShadertoyProjectConfig()
            }
        } else {
            thisLogger().info("[ShadertoyProjectManager] No config file found, starting with empty config")
            config = ShadertoyProjectConfig()
        }
    }
    
    /**
     * 保存配置到yml文件
     */
    fun saveConfig() {
        try {
            val configFile = getShadertoyEditorConfigFile()
            
            // 配置YAML输出格式
            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                isPrettyFlow = true
            }
            
            // 配置Representer，避免输出类型标签
            val representer = Representer(options).apply {
                // 不输出根对象的类型标签
                propertyUtils.isSkipMissingProperties = true
            }
            
            val yaml = Yaml(representer, options)
            
            configFile.writeText(yaml.dump(config))
            thisLogger().info("[ShadertoyProjectManager] Config saved to ${configFile.absolutePath}")
            
            // 刷新VFS
            ApplicationManager.getApplication().invokeLater {
                VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
            }
        } catch (e: Exception) {
            thisLogger().error("[ShadertoyProjectManager] Failed to save config", e)
        }
    }
    
    /**
     * 校验项目名称是否唯一
     */
    fun isProjectNameUnique(name: String): Boolean {
        return config.projects.none { it.name.equals(name, ignoreCase = true) }
    }
    
    /**
     * 校验路径是否可用（不存在或为空文件夹）
     */
    fun isPathAvailable(relativePath: String): Boolean {
        val projectBasePath = project.basePath ?: return false
        val projectDir = File("$projectBasePath/$relativePath")
        
        // 不存在 - OK
        if (!projectDir.exists()) {
            return true
        }
        
        // 存在但不是目录 - 不OK
        if (!projectDir.isDirectory) {
            return false
        }
        
        // 是目录但非空 - 不OK
        val files = projectDir.listFiles() ?: return false
        return files.isEmpty()
    }
    
    /**
     * 创建新的ShadertoyProj
     * 
     * @param name 项目名称（必须唯一）
     * @param relativePath 相对于IDE项目根目录的路径
     * @return 创建的ShadertoyProject对象
     * @throws IllegalArgumentException 如果名称不唯一或路径不可用
     * @throws IllegalStateException 如果模板文件未找到或项目路径为null
     */
    fun createShadertoyProject(name: String, relativePath: String): ShadertoyProject {
        // 1. 校验名称唯一性
        if (!isProjectNameUnique(name)) {
            throw IllegalArgumentException("Project name '$name' already exists")
        }
        
        // 2. 校验路径可用性
        if (!isPathAvailable(relativePath)) {
            throw IllegalArgumentException("Path '$relativePath' is not available (must be empty or not exist)")
        }
        
        val projectBasePath = project.basePath 
            ?: throw IllegalStateException("Project base path is null")
        
        // 3. 创建文件夹
        val projectDir = File("$projectBasePath/$relativePath")
        if (!projectDir.exists()) {
            projectDir.mkdirs()
            thisLogger().info("[ShadertoyProjectManager] Created directory: ${projectDir.absolutePath}")
        }
        
        // 4. 复制模板到Image.glsl
        val template = javaClass.getResource("/shaderTemplate/Image.template.glsl")?.readText()
            ?: throw IllegalStateException("Template not found: /shaderTemplate/Image.template.glsl")
        
        val imageGlsl = File(projectDir, "Image.glsl")
        imageGlsl.writeText(template)
        thisLogger().info("[ShadertoyProjectManager] Created Image.glsl: ${imageGlsl.absolutePath}")
        
        // 5. 添加到配置
        val shadertoyProject = ShadertoyProject(name, relativePath)
        config.projects.add(shadertoyProject)
        saveConfig()
        
        // 6. 刷新VFS（让IDE感知到新文件）
        ApplicationManager.getApplication().invokeLater {
            VirtualFileManager.getInstance().refreshWithoutFileWatcher(false)
        }
        
        thisLogger().info("[ShadertoyProjectManager] Project created: $name at $relativePath")
        
        return shadertoyProject
    }
    
    /**
     * 删除项目（仅从配置移除，不删除文件夹）
     */
    fun removeShadertoyProject(proj: ShadertoyProject) {
        config.projects.remove(proj)
        saveConfig()
        
        if (currentProject == proj) {
            setCurrentShadertoyProject(null)
        }
        
        thisLogger().info("[ShadertoyProjectManager] Project removed: ${proj.name}")
    }
    
    /**
     * 获取所有项目
     */
    fun getAllProjects(): List<ShadertoyProject> {
        return config.projects.toList()
    }
    
    /**
     * 设置当前选中的项目
     * 
     * 会通过MessageBus通知所有订阅者
     */
    fun setCurrentShadertoyProject(proj: ShadertoyProject?) {
        currentProject = proj
        
        // 发送通知
        ApplicationManager.getApplication().messageBus
            .syncPublisher(STE_IDEProjectEventListener.TOPIC)
            .onShadertoyProjectChanged(proj)
        
        val projectName = proj?.name ?: "None"
        thisLogger().info("[ShadertoyProjectManager] Current project changed to: $projectName")
    }
    
    /**
     * 获取当前选中的项目
     */
    fun getCurrentShadertoyProject(): ShadertoyProject? {
        return currentProject
    }
    
    /**
     * 获取配置文件对象
     */
    private fun getShadertoyEditorConfigFile(): File {
        val projectBasePath = project.basePath 
            ?: throw IllegalStateException("Project base path is null")
        return File("$projectBasePath/$CONFIG_FILE_NAME")
    }
}

