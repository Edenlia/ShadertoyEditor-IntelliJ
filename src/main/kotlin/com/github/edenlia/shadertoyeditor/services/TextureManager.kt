package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.github.edenlia.shadertoyeditor.renderBackend.DefaultBlackTexture
import com.github.edenlia.shadertoyeditor.renderBackend.Texture
import com.github.edenlia.shadertoyeditor.renderBackend.TexturePathResolver
import com.github.edenlia.shadertoyeditor.toolWindow.ShadertoyOutputWindowFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Texture管理服务
 * 
 * 负责将项目的texture配置加载到RenderBackend
 */
object TextureManager {
    
    /**
     * 为指定项目加载所有texture到RenderBackend
     * 
     * @param project 当前项目
     * @param shadertoyProject Shadertoy项目配置
     */
    fun loadProjectTextures(project: Project, shadertoyProject: ShadertoyProject) {
        val outputWindow = ShadertoyOutputWindowFactory.getInstance(project)
        val renderBackend = outputWindow?.getRenderBackend() ?: run {
            thisLogger().warn("[TextureManager] RenderBackend not available")
            return
        }
        
        thisLogger().info("[TextureManager] Loading textures for project: ${shadertoyProject.name}")
        
        for (i in 0 until 4) {
            val texturePath = shadertoyProject.getChannelTexture(i)
            
            try {
                val texture = if (texturePath != null) {
                    TexturePathResolver.resolveTexture(project, texturePath)
                } else {
                    null  // 使用默认黑色texture
                }
                
                renderBackend.setChannelTexture(i, texture)
                
                if (texturePath != null) {
                    thisLogger().info("[TextureManager] Channel $i loaded: $texturePath")
                } else {
                    thisLogger().info("[TextureManager] Channel $i using default texture")
                }
            } catch (e: Exception) {
                thisLogger().warn("[TextureManager] Failed to load texture for channel $i: $texturePath", e)
                
                // 显示错误提示
                Messages.showWarningDialog(
                    project,
                    "Failed to load texture for iChannel$i: ${e.message}\n" +
                    "Falling back to default black texture.",
                    "Texture Load Error"
                )
                
                // 回退到默认texture
                renderBackend.setChannelTexture(i, null)
            }
        }
        
        thisLogger().info("[TextureManager] All textures loaded")
    }
    
    /**
     * 清除所有channel的texture（恢复到默认）
     */
    fun clearAllTextures(project: Project) {
        val outputWindow = ShadertoyOutputWindowFactory.getInstance(project)
        val renderBackend = outputWindow?.getRenderBackend() ?: return
        
        renderBackend.clearAllChannels()
        thisLogger().info("[TextureManager] All textures cleared")
    }
}

