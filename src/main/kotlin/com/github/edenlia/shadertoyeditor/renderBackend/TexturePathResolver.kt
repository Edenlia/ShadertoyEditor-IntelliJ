package com.github.edenlia.shadertoyeditor.renderBackend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Texture路径解析器
 * 
 * 负责解析不同类型的texture路径并加载Texture对象
 * 
 * 支持的路径格式：
 * - 插件资源：$plugin/resources/textures/xxx.png
 * - 本地文件：相对于项目根目录的路径（如 textures/noise.png）
 */
object TexturePathResolver {
    
    /**
     * 解析texture路径并加载Texture对象
     * 
     * @param project 当前项目
     * @param texturePath texture路径，null表示使用默认texture
     * @return Texture对象，如果路径无效或加载失败返回null
     */
    fun resolveTexture(project: Project, texturePath: String?): Texture? {
        if (texturePath == null) {
            return null
        }
        
        return try {
            when {
                texturePath.startsWith("\$plugin/resources/textures/") -> {
                    // 插件资源路径
                    loadFromPluginResources(texturePath)
                }
                else -> {
                    // 本地文件路径（相对于项目根目录）
                    loadFromProjectFile(project, texturePath)
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("[TexturePathResolver] Failed to load texture: $texturePath", e)
            null
        }
    }
    
    /**
     * 从插件resources加载texture
     * 
     * @param resourcePath 资源路径，格式：$plugin/resources/textures/xxx.png
     * @return Texture对象
     */
    private fun loadFromPluginResources(resourcePath: String): Texture {
        // 提取资源文件名
        val resourceName = resourcePath.removePrefix("\$plugin/resources/textures/")
        val resourcePathInJar = "/textures/$resourceName"
        
        // 从classpath加载资源
        val inputStream = TexturePathResolver::class.java.getResourceAsStream(resourcePathInJar)
            ?: throw IllegalArgumentException("Plugin texture not found: $resourcePath")
        
        return try {
            val image = javax.imageio.ImageIO.read(inputStream)
                ?: throw IllegalArgumentException("Failed to read image from resource: $resourcePath")
            
            TextureLoader.createFromBufferedImage(image)
        } finally {
            inputStream.close()
        }
    }
    
    /**
     * 从项目文件加载texture
     * 
     * @param project 当前项目
     * @param relativePath 相对于项目根目录的路径
     * @return Texture对象
     */
    private fun loadFromProjectFile(project: Project, relativePath: String): Texture {
        val projectBasePath = project.basePath
            ?: throw IllegalStateException("Project base path is null")
        
        val filePath = "$projectBasePath/$relativePath"
        return TextureLoader.loadFromFile(filePath)
    }
    
    /**
     * 检查texture路径是否有效
     * 
     * @param project 当前项目
     * @param texturePath texture路径
     * @return true如果路径有效，false如果无效
     */
    fun isValidTexturePath(project: Project, texturePath: String?): Boolean {
        if (texturePath == null) {
            return true  // null是有效的（表示使用默认texture）
        }
        
        return try {
            when {
                texturePath.startsWith("\$plugin/resources/textures/") -> {
                    // 检查插件资源是否存在
                    val resourceName = texturePath.removePrefix("\$plugin/resources/textures/")
                    val resourcePathInJar = "/textures/$resourceName"
                    TexturePathResolver::class.java.getResource(resourcePathInJar) != null
                }
                else -> {
                    // 检查本地文件是否存在
                    val projectBasePath = project.basePath ?: return false
                    val filePath = "$projectBasePath/$texturePath"
                    File(filePath).exists()
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}

