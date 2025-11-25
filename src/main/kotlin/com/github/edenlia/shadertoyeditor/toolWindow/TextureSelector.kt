package com.github.edenlia.shadertoyeditor.toolWindow

import com.github.edenlia.shadertoyeditor.dialogs.TextureSelectionDialog
import com.github.edenlia.shadertoyeditor.listeners.STE_IDEProjectEventListener
import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.renderBackend.DefaultBlackTexture
import com.github.edenlia.shadertoyeditor.renderBackend.Texture
import com.github.edenlia.shadertoyeditor.renderBackend.TexturePathResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import javax.swing.*

/**
 * 单个Channel的Texture选择器
 * 
 * 显示texture预览图（64x64）和channel标签（如"iChannel0"）
 * 点击预览图可以打开texture选择对话框
 */
class TextureSelector(
    private val channelIndex: Int,
    private val project: Project,
    private val shadertoyProject: ShadertoyProject
) : JPanel(BorderLayout()) {
    
    private val previewLabel: JLabel
    private val channelLabel: JLabel
    
    init {
        thisLogger().info("[TextureSelector] Creating selector for channel $channelIndex")
        preferredSize = Dimension(100, 100)
        
        // 创建预览标签（64x64）
        previewLabel = JLabel().apply {
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            preferredSize = Dimension(64, 64)
            border = BorderFactory.createLoweredBevelBorder()
            isOpaque = true
            background = java.awt.Color.DARK_GRAY
            
            // 点击打开选择对话框
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    openTextureSelectionDialog()
                }
            })
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        }
        
        // 创建channel标签
        channelLabel = JLabel("iChannel$channelIndex").apply {
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(java.awt.Font.BOLD)
        }
        
        add(previewLabel, BorderLayout.CENTER)
        add(channelLabel, BorderLayout.SOUTH)
        
        thisLogger().info("[TextureSelector] UI components added for channel $channelIndex")
        
        // 加载当前texture
        thisLogger().info("[TextureSelector] Loading current texture for channel $channelIndex")
        loadCurrentTexture()
        thisLogger().info("[TextureSelector] Selector creation complete for channel $channelIndex")
    }
    
    /**
     * 打开texture选择对话框
     */
    private fun openTextureSelectionDialog() {
        val dialog = TextureSelectionDialog(project, this)
        if (dialog.showAndGet()) {
            val selectedPath = dialog.getSelectedTexturePath()

            // 发送texture变更事件
            ApplicationManager.getApplication().messageBus
                .syncPublisher(STE_IDEProjectEventListener.TOPIC)
                .onTextureChannelChanged(shadertoyProject, channelIndex, selectedPath)

            // 重新加载预览
            loadCurrentTexture()

            thisLogger().info("[TextureSelector] Channel $channelIndex texture changed, event sent: $selectedPath")
        }
    }
    
    /**
     * 加载当前texture并显示预览
     */
    fun loadCurrentTexture() {
        val texturePath = shadertoyProject.getChannelTexture(channelIndex)
        
        try {
            val texture = if (texturePath != null) {
                TexturePathResolver.resolveTexture(project, texturePath)
            } else {
                null
            }
            
            displayTexture(texture)
        } catch (e: Exception) {
            thisLogger().warn("[TextureSelector] Failed to load texture: $texturePath", e)
            Messages.showWarningDialog(
                project,
                "Failed to load texture: ${e.message}\nFalling back to default black texture.",
                "Texture Load Error"
            )
            // 回退到默认texture
            displayTexture(null)
        }
    }
    
    /**
     * 显示texture预览图
     */
    private fun displayTexture(texture: Texture?) {
        val targetTexture = texture ?: DefaultBlackTexture
        
        // 将texture转换为BufferedImage用于显示
        val image = createPreviewImage(targetTexture)
        
        // 缩放为64x64
        val scaledImage = image.getScaledInstance(64, 64, Image.SCALE_SMOOTH)
        val icon = ImageIcon(scaledImage)
        
        previewLabel.icon = icon
        previewLabel.toolTipText = if (texture != null) {
            shadertoyProject.getChannelTexture(channelIndex) ?: "Default"
        } else {
            "Default (Black)"
        }
    }
    
    /**
     * 将Texture转换为BufferedImage
     */
    private fun createPreviewImage(texture: Texture): BufferedImage {
        val image = BufferedImage(texture.width, texture.height, BufferedImage.TYPE_INT_ARGB)
        
        var index = 0
        for (y in 0 until texture.height) {
            for (x in 0 until texture.width) {
                val r = texture.pixelData[index++].toInt() and 0xFF
                val g = texture.pixelData[index++].toInt() and 0xFF
                val b = texture.pixelData[index++].toInt() and 0xFF
                val a = texture.pixelData[index++].toInt() and 0xFF
                
                val rgb = (a shl 24) or (r shl 16) or (g shl 8) or b
                image.setRGB(x, y, rgb)
            }
        }
        
        return image
    }
}

