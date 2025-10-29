package com.github.edenlia.shadertoyeditor.settings

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.github.edenlia.shadertoyeditor.model.ShadertoyConfig
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI 界面
 * 提供用户名和密码的输入界面
 */
class ShadertoySettingsUI {
    
    private val mainPanel: JPanel
    
    // UI 组件
    private val usernameField: JBTextField
    private val passwordField: JBPasswordField
    
    init {
        mainPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL
        
        // 初始化组件
        usernameField = JBTextField()
        passwordField = JBPasswordField()
        
        // 布局组件
        var row = 0
        
        // 用户名
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.WEST
        mainPanel.add(JBLabel("Username:"), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        mainPanel.add(usernameField, gbc)
        row++
        
        // 密码
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        mainPanel.add(JBLabel("Password:"), gbc)
        
        gbc.gridx = 1
        gbc.weightx = 1.0
        mainPanel.add(passwordField, gbc)
        row++
        
        // 填充剩余空间
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        mainPanel.add(JPanel(), gbc)
    }
    
    /**
     * 获取主面板
     */
    fun getPanel(): JComponent = mainPanel
    
    /**
     * 检查配置是否被修改
     */
    fun isModified(config: ShadertoyConfig): Boolean {
        return usernameField.text != config.username ||
                String(passwordField.password) != config.password
    }
    
    /**
     * 应用配置（从 UI 保存到 Config）
     */
    fun apply(config: ShadertoyConfig) {
        config.username = usernameField.text
        config.password = String(passwordField.password)
    }
    
    /**
     * 重置配置（从 Config 加载到 UI）
     */
    fun reset(config: ShadertoyConfig) {
        usernameField.text = config.username
        passwordField.text = config.password
    }
}

