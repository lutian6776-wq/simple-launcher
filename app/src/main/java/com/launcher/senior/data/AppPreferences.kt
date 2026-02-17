package com.launcher.senior.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.launcher.senior.data.EmergencyContact

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context? = null) {
    companion object {
        private val EMERGENCY_PHONE_KEY = stringPreferencesKey("emergency_phone") // 保留兼容性
        private val EMERGENCY_CONTACTS_KEY = stringPreferencesKey("emergency_contacts") // 多个紧急联系人
        private val QUICK_APPS_KEY = stringPreferencesKey("quick_apps")
        private val CUSTOM_APPS_KEY = stringPreferencesKey("custom_apps")
        private val FILE_SHORTCUTS_KEY = stringPreferencesKey("file_shortcuts")
        private val DEFAULT_APP_MAPPINGS_KEY = stringPreferencesKey("default_app_mappings") // MIME类型 -> 应用包名
    }
    
    private val dataStore: DataStore<Preferences> = 
        context?.dataStore ?: throw IllegalStateException("Context is required")
    
    suspend fun getEmergencyPhoneNumber(): String {
        return dataStore.data.first()[EMERGENCY_PHONE_KEY] ?: ""
    }
    
    suspend fun setEmergencyPhoneNumber(phoneNumber: String) {
        dataStore.edit { preferences ->
            preferences[EMERGENCY_PHONE_KEY] = phoneNumber
        }
    }
    
    suspend fun getEmergencyContacts(): List<EmergencyContact> {
        val jsonString = dataStore.data.first()[EMERGENCY_CONTACTS_KEY]
        if (jsonString == null || jsonString.isEmpty()) {
            // 兼容旧版本：如果有单个号码，转换为联系人列表
            val oldPhone = getEmergencyPhoneNumber()
            if (oldPhone.isNotEmpty()) {
                return listOf(EmergencyContact(
                    id = "emergency_1",
                    name = "紧急联系人",
                    phoneNumber = oldPhone
                ))
            }
            return emptyList()
        }
        return try {
            parseEmergencyContactsFromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun setEmergencyContacts(contacts: List<EmergencyContact>) {
        dataStore.edit { preferences ->
            preferences[EMERGENCY_CONTACTS_KEY] = emergencyContactsToJson(contacts)
        }
    }
    
    suspend fun getQuickApps(): List<AppInfo> {
        val jsonString = dataStore.data.first()[QUICK_APPS_KEY] ?: return getDefaultQuickApps()
        return try {
            parseAppsFromJson(jsonString)
        } catch (e: Exception) {
            getDefaultQuickApps()
        }
    }
    
    suspend fun setQuickApps(apps: List<AppInfo>) {
        dataStore.edit { preferences ->
            preferences[QUICK_APPS_KEY] = appsToJson(apps)
        }
    }
    
    suspend fun getCustomApps(): List<AppInfo> {
        val jsonString = dataStore.data.first()[CUSTOM_APPS_KEY] ?: return emptyList()
        return try {
            parseAppsFromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun setCustomApps(apps: List<AppInfo>) {
        dataStore.edit { preferences ->
            preferences[CUSTOM_APPS_KEY] = appsToJson(apps)
        }
    }
    
    suspend fun getFileShortcuts(): List<FileShortcut> {
        val jsonString = dataStore.data.first()[FILE_SHORTCUTS_KEY] ?: return emptyList()
        return try {
            parseFileShortcutsFromJson(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun setFileShortcuts(shortcuts: List<FileShortcut>) {
        dataStore.edit { preferences ->
            preferences[FILE_SHORTCUTS_KEY] = fileShortcutsToJson(shortcuts)
        }
    }
    
    fun getDefaultQuickApps(): List<AppInfo> {
        return listOf(
            AppInfo.WECHAT,
            AppInfo.DOUYIN,
            AppInfo.CAMERA,
            AppInfo.GALLERY,
            AppInfo.WEATHER,
            AppInfo.MESSAGE,
            AppInfo.CONTACTS,
            AppInfo.PHONE,
            AppInfo.CALCULATOR,
            AppInfo.FILE_MANAGER
        )
    }
    
    private fun appsToJson(apps: List<AppInfo>): String {
        val jsonArray = JSONArray()
        apps.forEach { app ->
            val jsonObject = JSONObject().apply {
                put("id", app.id)
                put("name", app.name)
                put("packageName", app.packageName ?: "")
                put("activityName", app.activityName ?: "")
                put("intentAction", app.intentAction ?: "")
                put("isSystemApp", app.isSystemApp)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    
    private fun parseAppsFromJson(jsonString: String): List<AppInfo> {
        val jsonArray = JSONArray(jsonString)
        val apps = mutableListOf<AppInfo>()
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val id = jsonObject.getString("id")
            val name = jsonObject.getString("name")
            val packageName = jsonObject.optString("packageName").takeIf { it.isNotEmpty() }
            val activityName = jsonObject.optString("activityName").takeIf { it.isNotEmpty() }
            val intentAction = jsonObject.optString("intentAction").takeIf { it.isNotEmpty() }
            val isSystemApp = jsonObject.optBoolean("isSystemApp", false)
            
            // 根据ID获取图标
            val icon = when (id) {
                "wechat" -> Icons.Default.Chat
                "douyin" -> Icons.Default.VideoLibrary
                "camera" -> Icons.Default.CameraAlt
                "gallery" -> Icons.Default.PhotoLibrary
                "weather" -> Icons.Default.WbSunny
                else -> Icons.Default.Apps
            }
            
            apps.add(
                AppInfo(
                    id = id,
                    name = name,
                    packageName = packageName,
                    activityName = activityName,
                    intentAction = intentAction,
                    icon = icon,
                    isSystemApp = isSystemApp
                )
            )
        }
        
        return apps
    }
    
    suspend fun getDefaultAppForMimeType(mimeType: String): Pair<String?, String?>? {
        val jsonString = dataStore.data.first()[DEFAULT_APP_MAPPINGS_KEY] ?: return null
        return try {
            val jsonObject = JSONObject(jsonString)
            val appInfo = jsonObject.optJSONObject(mimeType)
            if (appInfo != null) {
                Pair(
                    appInfo.optString("packageName").takeIf { it.isNotEmpty() },
                    appInfo.optString("activityName").takeIf { it.isNotEmpty() }
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun setDefaultAppForMimeType(mimeType: String, packageName: String?, activityName: String?) {
        dataStore.edit { preferences ->
            val currentJson = preferences[DEFAULT_APP_MAPPINGS_KEY] ?: "{}"
            val jsonObject = JSONObject(currentJson)
            if (packageName != null) {
                jsonObject.put(mimeType, JSONObject().apply {
                    put("packageName", packageName)
                    put("activityName", activityName ?: "")
                })
            } else {
                jsonObject.remove(mimeType)
            }
            preferences[DEFAULT_APP_MAPPINGS_KEY] = jsonObject.toString()
        }
    }
    
    private fun fileShortcutsToJson(shortcuts: List<FileShortcut>): String {
        val jsonArray = JSONArray()
        shortcuts.forEach { shortcut ->
            val jsonObject = JSONObject().apply {
                put("id", shortcut.id)
                put("name", shortcut.name)
                put("filePath", shortcut.filePath ?: "")
                put("fileUri", shortcut.fileUri ?: "")
                put("mimeType", shortcut.mimeType)
                put("fileSize", shortcut.fileSize)
                put("thumbnailPath", shortcut.thumbnailPath ?: "")
                put("defaultAppPackage", shortcut.defaultAppPackage ?: "")
                put("defaultAppActivity", shortcut.defaultAppActivity ?: "")
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    
    private fun parseFileShortcutsFromJson(jsonString: String): List<FileShortcut> {
        val jsonArray = JSONArray(jsonString)
        val shortcuts = mutableListOf<FileShortcut>()
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            shortcuts.add(
                FileShortcut(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    filePath = jsonObject.optString("filePath").takeIf { it.isNotEmpty() },
                    fileUri = jsonObject.optString("fileUri").takeIf { it.isNotEmpty() },
                    mimeType = jsonObject.getString("mimeType"),
                    fileSize = jsonObject.optLong("fileSize", 0L),
                    thumbnailPath = jsonObject.optString("thumbnailPath").takeIf { it.isNotEmpty() },
                    defaultAppPackage = jsonObject.optString("defaultAppPackage").takeIf { it.isNotEmpty() },
                    defaultAppActivity = jsonObject.optString("defaultAppActivity").takeIf { it.isNotEmpty() }
                )
            )
        }
        
        return shortcuts
    }
    
    private fun emergencyContactsToJson(contacts: List<EmergencyContact>): String {
        val jsonArray = JSONArray()
        contacts.forEach { contact ->
            val jsonObject = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("phoneNumber", contact.phoneNumber)
                put("contactId", contact.contactId ?: -1)
                put("photoUri", contact.photoUri ?: "")
                put("sortKey", contact.sortKey ?: "")
                put("initialLetter", contact.initialLetter ?: "")
                put("category", contact.category)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    
    private fun parseEmergencyContactsFromJson(jsonString: String): List<EmergencyContact> {
        val jsonArray = JSONArray(jsonString)
        val contacts = mutableListOf<EmergencyContact>()
        
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val contactId = jsonObject.optLong("contactId", -1)
            contacts.add(
                EmergencyContact(
                    id = jsonObject.getString("id"),
                    name = jsonObject.getString("name"),
                    phoneNumber = jsonObject.getString("phoneNumber"),
                    contactId = if (contactId >= 0) contactId else null,
                    photoUri = jsonObject.optString("photoUri").takeIf { it.isNotEmpty() },
                    sortKey = jsonObject.optString("sortKey").takeIf { it.isNotEmpty() },
                    initialLetter = jsonObject.optString("initialLetter").takeIf { it.isNotEmpty() },
                    category = jsonObject.optString("category", "other")
                )
            )
        }
        
        return contacts
    }
}
