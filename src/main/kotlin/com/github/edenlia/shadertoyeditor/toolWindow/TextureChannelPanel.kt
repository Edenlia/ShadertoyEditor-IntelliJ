package com.github.edenlia.shadertoyeditor.toolWindow

import com.github.edenlia.shadertoyeditor.model.ShadertoyProject
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.awt.GridLayout
import javax.swing.JPanel

/**
 * Texture Channel选择面板
 * 
 * 以2x2网格布局显示4个channel的texture选择器
 */
class TextureChannelPanel(
    private val project: Project,
    private val shadertoyProject: ShadertoyProject
) : JPanel(GridLayout(2, 2, 10, 10)) {
    
    private val selectors = Array(4) { index ->
        TextureSelector(
            index,
            project,
            shadertoyProject
        )
    }
    
    init {
        thisLogger().info("[TextureChannelPanel] Creating panel for project: ${shadertoyProject.name}")
        
        // 添加4个选择器
        selectors.forEachIndexed { index, selector ->
            add(selector)
            thisLogger().info("[TextureChannelPanel] Added selector for channel $index")
        }
        
        // 设置边框
        border = javax.swing.BorderFactory.createTitledBorder(
            javax.swing.BorderFactory.createEtchedBorder(),
            "Texture Channels"
        )
        thisLogger().info("[TextureChannelPanel] Panel creation complete")
    }
    
    /**
     * 刷新所有texture选择器
     */
    fun refresh() {
        selectors.forEach { it.loadCurrentTexture() }
    }
}

