package com.github.edenlia.shadertoyeditor.services

import com.github.edenlia.shadertoyeditor.settings.ShadertoySettings

/**
 * 配置使用示例
 * 展示如何在代码中访问用户配置的用户名和密码
 */
object ConfigUsageExample {
    
    /**
     * 获取配置的用户名
     */
    fun getUsername(): String {
        val config = ShadertoySettings.getInstance().getConfig()
        return config.username
    }
    
    /**
     * 获取配置的密码
     */
    fun getPassword(): String {
        val config = ShadertoySettings.getInstance().getConfig()
        return config.password
    }
    
    /**
     * 示例：使用配置进行登录
     */
    fun login() {
        val config = ShadertoySettings.getInstance().getConfig()
        
        if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
            println("正在使用以下凭据登录：")
            println("用户名: ${config.username}")
            println("密码: ${config.password.replace(Regex("."), "*")}") // 隐藏密码
            
            // TODO: 在这里实现实际的登录逻辑
        } else {
            println("请先在 Settings -> Tools -> Shadertoy Editor 中配置用户名和密码")
        }
    }
}

