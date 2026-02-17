package com.launcher.senior.data

import android.net.Uri

data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val contactId: Long? = null, // 通讯录中的联系人ID
    val photoUri: String? = null, // 头像URI
    val sortKey: String? = null, // 排序键
    val initialLetter: String? = null, // 首字母
    val category: String = "other" // 类别: "family" 或 "other"
) {
    fun getPhotoUri(): Uri? {
        return photoUri?.let { Uri.parse(it) }
    }
}
