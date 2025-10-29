package com.github.edenlia.shadertoyeditor.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.github.edenlia.shadertoyeditor.model.ShadertoyConfig
import javax.swing.JComponent

/**
 * Settings 配置页面入口
 * 在 Settings -> Tools -> Shadertoy Editor 中显示
 */
class ShadertoyConfigurable : SearchableConfigurable {
    
    private var settingsUI: ShadertoySettingsUI? = null
    
    companion object {
        const val ID = "shadertoy.editor.settings"
        const val DISPLAY_NAME = "Shadertoy Editor"
    }
    
    /**
     * 获取配置页面的唯一 ID
     */
    override fun getId(): String = ID
    
    /**
     * 获取在 Settings 中显示的名称
     */
    override fun getDisplayName(): String = DISPLAY_NAME
    
    /**
     * 创建 UI 组件
     */
    override fun createComponent(): JComponent? {
        if (settingsUI == null) {
            settingsUI = ShadertoySettingsUI()
        }
        // 加载当前配置到 UI
        settingsUI?.reset(ShadertoySettings.getInstance().getConfig())
        return settingsUI?.getPanel()
    }
    
    /**
     * 检查配置是否被修改
     */
    override fun isModified(): Boolean {
        val config = ShadertoySettings.getInstance().getConfig()
        return settingsUI?.isModified(config) ?: false
    }
    
    /**
     * 应用配置更改
     */
    override fun apply() {
        val settings = ShadertoySettings.getInstance()
        val config = settings.getConfig().clone() // 克隆避免直接修改
        settingsUI?.apply(config)
        settings.setConfig(config)
    }
    
    /**
     * 重置配置到上次保存的状态
     */
    override fun reset() {
        val config = ShadertoySettings.getInstance().getConfig()
        settingsUI?.reset(config)
    }
    
    /**
     * 释放 UI 资源
     */
    override fun disposeUIResources() {
        settingsUI = null
    }
}

