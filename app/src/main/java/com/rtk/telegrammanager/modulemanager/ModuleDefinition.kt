package com.rtk.telegrammanager.modulemanager

data class ModuleDefinition(
    val id: String,
    val name: String,
    val description: String,
    val defaultEnabled: Boolean = true
)
