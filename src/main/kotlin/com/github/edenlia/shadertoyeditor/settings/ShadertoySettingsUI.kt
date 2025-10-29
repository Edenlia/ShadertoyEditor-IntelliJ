package com.github.edenlia.shadertoyeditor.settings

import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.github.edenlia.shadertoyeditor.model.ShadertoyConfig
import com.intellij.openapi.ui.DialogPanel
import javax.swing.JComponent

/**
 * Settings UI 界面 - 使用 Kotlin UI DSL
 * 布局：Username 和 Password 在同一行，下方居中显示 Login 按钮
 */
class ShadertoySettingsUI {
    
    // UI 组件
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    
    /**
     * 主面板 - 使用 Kotlin UI DSL 构建
     */
    private val mainPanel: DialogPanel = panel {
        // Row 1: Username 和 Password 在同一行
        row {
            // Username 部分（左半部分）
            label("Username:")
                .gap(RightGap.SMALL)
            cell(usernameField)
                .resizableColumn()
                .align(AlignX.FILL)

            // Password 部分（右半部分）
            label("Password:")
                .gap(RightGap.SMALL)
            cell(passwordField)
                .resizableColumn()
                .align(AlignX.FILL)
        }
        
        // Row 2: 空行（增加间距）
        row { }
        
        // Row 3: Login 按钮（居中）
        row {
            button("Login") {
                onLoginClick()
            }.align(AlignX.CENTER)
        }
    }
    
    /**
     * Login 按钮点击事件
     */
    private fun onLoginClick() {
        val username = usernameField.text
        val password = String(passwordField.password)
        
        println("=================================")
        println("🔐 Login Button Clicked")
        println("---------------------------------")
        println("Username: $username")
        println("Password: ${"*".repeat(password.length)}")
        println("=================================")
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

