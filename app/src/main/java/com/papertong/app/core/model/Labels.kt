package com.papertong.app.core.model

/**
 * 把持久化的 id 还原成展示用的中文标签。
 *
 * 历史记录里只存 id（标签将来可能改），展示时才查表；
 * 查不到就退回原始 id —— 显示一个陌生字符串，
 * 好过显示"未知"让用户以为记录坏了。
 */
fun styleLabel(id: String): String =
    TranslateStyle.entries.firstOrNull { it.id == id }?.label ?: id

fun modelLabel(id: String): String =
    ModelOption.entries.firstOrNull { it.id == id }?.label ?: id
