package com.github.edenlia.shadertoyeditor.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

/**
 * 打开 Shadertoy Editor 设置页面的 Action
 */
class OpenSettingsAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 打开设置对话框，定位到 Shadertoy Editor 页面
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            "Shadertoy Editor"  // 必须与 plugin.xml 中的 displayName 一致
        )
    }
}

