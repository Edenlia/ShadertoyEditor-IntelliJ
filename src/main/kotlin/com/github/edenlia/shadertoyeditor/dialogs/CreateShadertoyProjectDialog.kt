package com.github.edenlia.shadertoyeditor.dialogs

import com.github.edenlia.shadertoyeditor.services.ShadertoyProjectManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 创建Shadertoy项目对话框
 * 
 * 用于输入新项目的名称和路径，包含完整的验证逻辑
 */
class CreateShadertoyProjectDialog(
    private val project: Project
) : DialogWrapper(project) {
    
    private val projectManager = project.service<ShadertoyProjectManager>()
    private val nameField = JBTextField()
    private val pathField = JBTextField()
    private val pathPreviewLabel = com.intellij.ui.components.JBLabel()
    
    init {
        title = "Create New Shadertoy Project"
        init()
        
        // 设置默认路径前缀
        pathField.text = "shaders/"
        
        // 监听名称变化，实时更新路径预览
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun removeUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun changedUpdate(e: DocumentEvent?) = updatePathPreview()
        })
        
        pathField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun removeUpdate(e: DocumentEvent?) = updatePathPreview()
            override fun changedUpdate(e: DocumentEvent?) = updatePathPreview()
        })
        
        updatePathPreview()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Project Name:") {
                cell(nameField)
                    .align(AlignX.FILL)
                    .focused()
                    .validationOnApply { field ->
                        validateName(field.text)
                    }
            }
            row {
                comment("Must be unique")
            }
            
            row("Base Path:") {
                cell(pathField)
                    .align(AlignX.FILL)
                    .comment("Relative to project root, e.g. 'shaders/'")
            }
            
            row("Full Path:") {
                cell(pathPreviewLabel)
                    .comment("This is where the project will be created")
            }
        }
    }
    
    /**
     * 更新路径预览
     */
    private fun updatePathPreview() {
        val fullPath = getProjectPath()
        pathPreviewLabel.text = fullPath
    }
    
    /**
     * 验证项目名称
     */
    private fun validateName(name: String): ValidationInfo? {
        if (name.isBlank()) {
            return ValidationInfo("Project name cannot be empty", nameField)
        }
        
        // 检查名称唯一性
        if (!projectManager.isProjectNameUnique(name)) {
            return ValidationInfo("Project name '$name' already exists", nameField)
        }
        
        return null
    }
    
    /**
     * 获取项目名称
     */
    fun getProjectName(): String = nameField.text.trim()
    
    /**
     * 获取项目路径（自动根据名称生成）
     */
    fun getProjectPath(): String {
        val base = pathField.text.trim().removeSuffix("/")
        val name = nameField.text.trim()
            .replace(" ", "_")
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "") // 只保留安全字符
        
        return if (base.isEmpty()) name else "$base/$name"
    }
    
    /**
     * 对话框验证
     */
    override fun doValidate(): ValidationInfo? {
        // 校验名称
        val nameValidation = validateName(nameField.text)
        if (nameValidation != null) {
            return nameValidation
        }
        
        // 校验路径基础部分
        if (pathField.text.isBlank()) {
            return ValidationInfo("Base path cannot be empty", pathField)
        }
        
        // 校验完整路径可用性
        val fullPath = getProjectPath()
        if (!projectManager.isPathAvailable(fullPath)) {
            return ValidationInfo(
                "Path '$fullPath' is not available. Directory must be empty or not exist.",
                pathField
            )
        }
        
        return null
    }
}

