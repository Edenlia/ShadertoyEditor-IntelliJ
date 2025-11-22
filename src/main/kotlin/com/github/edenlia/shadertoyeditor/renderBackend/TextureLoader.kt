package com.github.edenlia.shadertoyeditor.renderBackend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Texture加载器
 * 
 * 提供从各种来源创建Texture的工厂方法
 * 这些方法不属于RenderBackend接口，而是独立的工具类
 */
object TextureLoader {
    /**
     * 从项目文件加载图片并创建Texture
     * 
     * 支持格式：PNG, JPG, BMP等（通过ImageIO）
     * 
     * @param project 当前项目
     * @param relativePath 相对于项目根目录的路径（如 "textures/noise.png"）
     * @return Texture对象，如果加载失败抛出异常
     * @throws IllegalStateException 如果项目路径为null
     * @throws IllegalArgumentException 如果文件不存在或格式不支持
     */
    fun loadFromProjectFile(project: Project, relativePath: String): Texture {
        val projectBasePath = project.basePath
            ?: throw IllegalStateException("Project base path is null")
        
        val filePath = "$projectBasePath/$relativePath"
        return loadFromFile(filePath)
    }
    
    /**
     * 从绝对路径加载图片文件
     * 
     * @param filePath 文件的绝对路径
     * @return Texture对象
     * @throws IllegalArgumentException 如果文件不存在或格式不支持
     */
    fun loadFromFile(filePath: String): Texture {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("File not found: $filePath")
        }
        
        return try {
            val image = ImageIO.read(file)
                ?: throw IllegalArgumentException("Unsupported image format: $filePath")
            
            createFromBufferedImage(image)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load image: ${e.message}", e)
        }
    }
    
    /**
     * 从VirtualFile加载图片
     * 
     * @param virtualFile IntelliJ的VirtualFile对象
     * @return Texture对象
     * @throws IllegalArgumentException 如果文件不存在或格式不支持
     */
    fun loadFromVirtualFile(virtualFile: VirtualFile): Texture {
        if (!virtualFile.exists()) {
            throw IllegalArgumentException("VirtualFile does not exist: ${virtualFile.path}")
        }
        
        return try {
            val image = ApplicationManager.getApplication().runReadAction<BufferedImage?> {
                virtualFile.inputStream.use { inputStream ->
                    ImageIO.read(inputStream)
                }
            } ?: throw IllegalArgumentException("Unsupported image format: ${virtualFile.path}")
            
            createFromBufferedImage(image)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load image from VirtualFile: ${e.message}", e)
        }
    }
    
    /**
     * 从VirtualFile URL加载图片
     * 
     * @param fileUrl 文件的URL（如 "file:///path/to/image.png"）
     * @return Texture对象
     * @throws IllegalArgumentException 如果文件不存在或格式不支持
     */
    fun loadFromFileUrl(fileUrl: String): Texture {
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
            ?: throw IllegalArgumentException("File not found: $fileUrl")
        
        return loadFromVirtualFile(virtualFile)
    }
    
    /**
     * 从BufferedImage创建Texture
     * 
     * @param image BufferedImage对象
     * @return Texture对象
     */
    fun createFromBufferedImage(image: BufferedImage): Texture {
        val width = image.width
        val height = image.height
        val pixelData = ByteArray(width * height * 4)
        
        // 转换为RGBA格式
        var index = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = image.getRGB(x, y)
                pixelData[index++] = ((rgb shr 16) and 0xFF).toByte()  // R
                pixelData[index++] = ((rgb shr 8) and 0xFF).toByte()   // G
                pixelData[index++] = (rgb and 0xFF).toByte()           // B
                pixelData[index++] = ((rgb shr 24) and 0xFF).toByte()  // A
            }
        }
        
        return SimpleTexture(pixelData, width, height)
    }
    
    /**
     * 创建指定颜色和大小的纯色纹理
     * 
     * @param width 宽度
     * @param height 高度
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @param a 透明度分量 (0-255)，默认255（不透明）
     * @return Texture对象
     */
    fun createSolidColor(
        width: Int, 
        height: Int, 
        r: Int, 
        g: Int, 
        b: Int, 
        a: Int = 255
    ): Texture {
        require(width > 0 && height > 0) { "Width and height must be positive" }
        require(r in 0..255 && g in 0..255 && b in 0..255 && a in 0..255) {
            "Color components must be in range 0-255"
        }
        
        val pixelData = ByteArray(width * height * 4)
        for (i in pixelData.indices step 4) {
            pixelData[i] = r.toByte()
            pixelData[i + 1] = g.toByte()
            pixelData[i + 2] = b.toByte()
            pixelData[i + 3] = a.toByte()
        }
        
        return SimpleTexture(pixelData, width, height)
    }
}

/**
 * Texture的简单实现（内部使用）
 */
private data class SimpleTexture(
    override val pixelData: ByteArray,
    override val width: Int,
    override val height: Int
) : Texture {
    init {
        require(width > 0 && height > 0) { "Width and height must be positive" }
        require(pixelData.size == width * height * 4) {
            "Pixel data size mismatch: expected ${width * height * 4}, got ${pixelData.size}"
        }
    }
}

