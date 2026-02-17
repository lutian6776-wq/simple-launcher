package com.launcher.senior.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import com.launcher.senior.data.EmergencyContact

object ContactHelper {
    /**
     * 从通讯录读取所有联系人
     */
    fun getAllContacts(context: Context): List<EmergencyContact> {
        val contacts = mutableListOf<EmergencyContact>()
        
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.SORT_KEY_PRIMARY,
                "phonebook_label"
            ),
            null,
            null,
            ContactsContract.Contacts.SORT_KEY_PRIMARY + " ASC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            val sortKeyIndex = it.getColumnIndex(ContactsContract.Contacts.SORT_KEY_PRIMARY)
            val labelIndex = it.getColumnIndex("phonebook_label")
            
            while (it.moveToNext()) {
                val contactId = it.getLong(idIndex)
                val name = it.getString(nameIndex) ?: "未知"
                val photoUri = it.getString(photoUriIndex)
                val hasPhone = it.getInt(hasPhoneIndex) > 0
                val sortKey = it.getString(sortKeyIndex) ?: name
                var label = it.getString(labelIndex)
                
                if (label.isNullOrEmpty()) {
                    // 如果没有 phonebook_label，尝试从 sort_key 获取首字母
                    label = if (sortKey.isNotEmpty()) {
                        val firstChar = sortKey.first().uppercaseChar()
                        if (firstChar in 'A'..'Z') firstChar.toString() else "#"
                    } else {
                        "#"
                    }
                }
                
                if (hasPhone) {
                    // 获取电话号码
                    val phoneCursor: Cursor? = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        arrayOf(contactId.toString()),
                        null
                    )
                    
                    phoneCursor?.use { phone ->
                        val numberIndex = phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (phone.moveToFirst()) {
                            val phoneNumber = phone.getString(numberIndex) ?: ""
                            if (phoneNumber.isNotEmpty()) {
                                contacts.add(
                                    EmergencyContact(
                                        id = "contact_$contactId",
                                        name = name,
                                        phoneNumber = phoneNumber.replace(" ", "").replace("-", ""),
                                        contactId = contactId,
                                        photoUri = photoUri,
                                        sortKey = sortKey,
                                        initialLetter = label
                                    )
                                )
                            }
                        }
                    }
                    phoneCursor?.close()
                }
            }
        }
        cursor?.close()
        
        return contacts
    }
    
    /**
     * 根据联系人ID获取联系人信息
     */
    fun getContactById(context: Context, contactId: Long): EmergencyContact? {
        val cursor: Cursor? = context.contentResolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.SORT_KEY_PRIMARY,
                "phonebook_label"
            ),
            null,
            null,
            null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val sortKeyIndex = it.getColumnIndex(ContactsContract.Contacts.SORT_KEY_PRIMARY)
                val labelIndex = it.getColumnIndex("phonebook_label")
                
                val name = it.getString(nameIndex) ?: "未知"
                val photoUri = it.getString(photoUriIndex)
                val sortKey = it.getString(sortKeyIndex) ?: name
                var label = it.getString(labelIndex)
                
                if (label.isNullOrEmpty()) {
                    label = if (sortKey.isNotEmpty()) {
                        val firstChar = sortKey.first().uppercaseChar()
                        if (firstChar in 'A'..'Z') firstChar.toString() else "#"
                    } else {
                        "#"
                    }
                }
                
                // 获取电话号码
                val phoneCursor: Cursor? = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId.toString()),
                    null
                )
                
                phoneCursor?.use { phone ->
                    val numberIndex = phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (phone.moveToFirst()) {
                        val phoneNumber = phone.getString(numberIndex) ?: ""
                        phoneCursor.close()
                        cursor.close()
                        return EmergencyContact(
                            id = "contact_$contactId",
                            name = name,
                            phoneNumber = phoneNumber.replace(" ", "").replace("-", ""),
                            contactId = contactId,
                            photoUri = photoUri,
                            sortKey = sortKey,
                            initialLetter = label
                        )
                    }
                }
                phoneCursor?.close()
            }
        }
        cursor?.close()
        
        return null
    }
}
