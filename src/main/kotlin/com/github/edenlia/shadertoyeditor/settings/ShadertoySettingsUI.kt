package com.github.edenlia.shadertoyeditor.settings

import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.github.edenlia.shadertoyeditor.model.ShadertoyConfig
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ComboBox
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Settings UI 界面 - 使用 Kotlin UI DSL
 * 布局：Username 和 Password 在同一行，下方居中显示 Login 按钮
 */
class ShadertoySettingsUI {
    
    // UI 组件 - Login部分
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    
    // UI 组件 - 分辨率设置
    private val targetWidthField = JBTextField()
    private val targetHeightField = JBTextField()
    
    // UI 组件 - Backend选择
    private val backendComboBox = ComboBox(arrayOf("JCEF", "LWJGL", "JOGL"))
    
    // UI 组件 - 自动编译
    private val autoCompileCheckBox = JCheckBox("Auto-compile on save (Image.glsl)")
    
    /**
     * 主面板 - 使用 Kotlin UI DSL 构建
     */
    private val mainPanel: DialogPanel = panel {
        // ===== Login Section =====
        group("Login Settings (暂不维护)") {
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
        
        // ===== 分隔空间 =====
        row { }
        
        // ===== Target Resolution Section =====
        group("Target Resolution") {
            row {
                label("设置目标渲染分辨率（Apply后立即生效）")
                    .bold()
            }
            
            row {
                label("Width:")
                    .gap(RightGap.SMALL)
                cell(targetWidthField)
                    .columns(10)
                    .validationOnApply { field ->
                        validateResolutionField(field.text, "宽度")
                    }
                
                label("Height:")
                    .gap(RightGap.SMALL)
                cell(targetHeightField)
                    .columns(10)
                    .validationOnApply { field ->
                        validateResolutionField(field.text, "高度")
                    }
            }
            
            row {
                comment("范围：64-4096，默认：1280x720")
            }
        }
        
        // ===== 分隔空间 =====
        row { }
        
        // ===== Render Backend Section =====
        group("Render Backend") {
            row {
                label("渲染后端选择（需要重启IDE生效）")
                    .bold()
            }
            
            row {
                label("Backend Type:")
                    .gap(RightGap.SMALL)
                cell(backendComboBox)
                    .comment("JCEF: 稳定 (~30fps)")
            }
            
            row {
                comment("修改后需要重新打开Tool Window才能生效")
            }
        }
        
        // ===== 分隔空间 =====
        row { }
        
        // ===== Auto Compilation Section =====
        group("Auto Compilation") {
            row {
                label("编译行为设置")
                    .bold()
            }
            
            row {
                cell(autoCompileCheckBox)
                    .comment("保存激活项目的 Image.glsl 时自动编译并刷新渲染")
            }
            
            row {
                comment("提示：关闭自动编译后，需要手动点击 Compile 按钮")
            }
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
     * 验证分辨率字段
     */
    private fun validateResolutionField(text: String, fieldName: String): com.intellij.openapi.ui.ValidationInfo? {
        // 检查是否为空
        if (text.isBlank()) {
            return com.intellij.openapi.ui.ValidationInfo("${fieldName}不能为空")
        }
        
        // 检查是否为数字
        val value = text.toIntOrNull()
        if (value == null) {
            return com.intellij.openapi.ui.ValidationInfo("${fieldName}必须是整数")
        }
        
        // 检查范围
        if (value <= 0) {
            return com.intellij.openapi.ui.ValidationInfo("${fieldName}必须大于0")
        }
        
        if (value < 64) {
            return com.intellij.openapi.ui.ValidationInfo("${fieldName}不能小于64")
        }
        
        if (value > 4096) {
            return com.intellij.openapi.ui.ValidationInfo("${fieldName}不能大于4096")
        }
        
        return null
    }
    
    /**
     * 获取主面板
     */
    fun getPanel(): JComponent = mainPanel
    
    /**
     * 检查配置是否被修改
     */
    fun isModified(config: ShadertoyConfig): Boolean {
        val widthModified = targetWidthField.text.toIntOrNull() != config.canvasRefWidth
        val heightModified = targetHeightField.text.toIntOrNull() != config.canvasRefHeight
        val backendModified = (backendComboBox.selectedItem as? String) != config.backendType
        val autoCompileModified = autoCompileCheckBox.isSelected != config.autoCompileOnSave
        
        return usernameField.text != config.username ||
                String(passwordField.password) != config.password ||
                widthModified ||
                heightModified ||
                backendModified ||
                autoCompileModified
    }
    
    /**
     * 应用配置（从 UI 保存到 Config）
     */
    fun apply(config: ShadertoyConfig) {
        config.username = usernameField.text
        config.password = String(passwordField.password)
        
        // 保存分辨率（已通过验证）
        config.canvasRefWidth = targetWidthField.text.toIntOrNull() ?: 1280
        config.canvasRefHeight = targetHeightField.text.toIntOrNull() ?: 720
        
        // 保存Backend类型
        config.backendType = (backendComboBox.selectedItem as? String) ?: "JCEF"
        
        // 保存自动编译配置
        config.autoCompileOnSave = autoCompileCheckBox.isSelected
    }
    
    /**
     * 重置配置（从 Config 加载到 UI）
     */
    fun reset(config: ShadertoyConfig) {
        usernameField.text = config.username
        passwordField.text = config.password
        
        // 加载分辨率
        targetWidthField.text = config.canvasRefWidth.toString()
        targetHeightField.text = config.canvasRefHeight.toString()
        
        // 加载Backend类型
        backendComboBox.selectedItem = config.backendType
        
        // 加载自动编译配置
        autoCompileCheckBox.isSelected = config.autoCompileOnSave
    }
}

