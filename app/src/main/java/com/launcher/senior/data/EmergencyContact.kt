package com.launcher.senior.data

import android.net.Uri

data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val contactId: Long? = null, // 通讯录中的联系人ID
    val photoUri: String? = null // 头像URI
) {
    fun getPhotoUri(): Uri? {
        return photoUri?.let { Uri.parse(it) }
    }
}
