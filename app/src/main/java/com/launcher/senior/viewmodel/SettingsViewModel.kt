package com.launcher.senior.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.senior.data.AppInfo
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.data.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val phoneNumber: String = "", // 保留兼容性
    val emergencyContacts: List<EmergencyContact> = emptyList(), // 多个紧急联系人
    val quickApps: List<AppInfo> = emptyList(),
    val customApps: List<AppInfo> = emptyList(),
    val availableDefaultApps: List<AppInfo> = emptyList(), // 可恢复的预设应用
    val showContactSelector: Boolean = false, // 显示联系人选择器
    val selectedCategory: String? = null // 当前选择的联系人类别
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        loadData(context)
    }
    
    private fun loadData(context: Context) {
        viewModelScope.launch {
            val prefs = AppPreferences(context)
            val phoneNumber = prefs.getEmergencyPhoneNumber()
            val emergencyContacts = prefs.getEmergencyContacts()
            val quickApps = prefs.getQuickApps()
            val customApps = prefs.getCustomApps()
            
            // 计算可恢复的预设应用（默认应用中没有在当前快捷应用中的）
            val defaultApps = prefs.getDefaultQuickApps()
            val currentAppIds = quickApps.map { it.id }.toSet()
            val availableDefaultApps = defaultApps.filter { it.id !in currentAppIds }
            
            _uiState.value = SettingsUiState(
                phoneNumber = phoneNumber,
                emergencyContacts = emergencyContacts,
                quickApps = quickApps,
                customApps = customApps,
                availableDefaultApps = availableDefaultApps
            )
        }
    }
    
    fun updatePhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = phoneNumber)
    }
    
    suspend fun savePhoneNumber(context: Context) {
        val prefs = AppPreferences(context)
        prefs.setEmergencyPhoneNumber(_uiState.value.phoneNumber)
    }
    
    suspend fun selectSystemApp(context: Context, app: AppInfo, packageName: String, appName: String, activityName: String?) {
        val updatedApp = app.copy(
            packageName = packageName,
            activityName = activityName,
            intentAction = null,
            name = appName
        )
        
        val prefs = AppPreferences(context)
        val currentApps = prefs.getQuickApps().toMutableList()
        val index = currentApps.indexOfFirst { it.id == app.id }
        if (index >= 0) {
            currentApps[index] = updatedApp
            prefs.setQuickApps(currentApps)
            loadData(context)
        }
    }
    
    suspend fun removeQuickApp(context: Context, app: AppInfo) {
        val prefs = AppPreferences(context)
        val currentApps = prefs.getQuickApps().toMutableList()
        currentApps.removeAll { it.id == app.id }
        prefs.setQuickApps(currentApps)
        loadData(context)
    }
    
    suspend fun removeCustomApp(context: Context, app: AppInfo) {
        val prefs = AppPreferences(context)
        val currentApps = prefs.getCustomApps().toMutableList()
        currentApps.removeAll { it.id == app.id }
        prefs.setCustomApps(currentApps)
        loadData(context)
    }
    
    suspend fun addCustomApp(context: Context, packageName: String, appName: String, activityName: String? = null) {
        val newApp = AppInfo(
            id = "custom_${System.currentTimeMillis()}",
            name = appName,
            packageName = packageName,
            activityName = activityName,
            icon = Icons.Default.Apps,
            isSystemApp = false
        )
        
        val prefs = AppPreferences(context)
        val currentApps = prefs.getCustomApps().toMutableList()
        currentApps.add(newApp)
        prefs.setCustomApps(currentApps)
        loadData(context)
    }
    
    suspend fun restoreDefaultApp(context: Context, app: AppInfo) {
        val prefs = AppPreferences(context)
        val currentApps = prefs.getQuickApps().toMutableList()
        // 检查是否已存在
        if (currentApps.none { it.id == app.id }) {
            currentApps.add(app)
            prefs.setQuickApps(currentApps)
            loadData(context)
        }
    }
    
    suspend fun addEmergencyContact(context: Context, contact: EmergencyContact) {
        val prefs = AppPreferences(context)
        val currentContacts = prefs.getEmergencyContacts().toMutableList()
        // 检查是否已存在
        if (currentContacts.none { it.id == contact.id || it.phoneNumber == contact.phoneNumber }) {
            currentContacts.add(contact)
            prefs.setEmergencyContacts(currentContacts)
            loadData(context)
        }
    }
    
    suspend fun removeEmergencyContact(context: Context, contact: EmergencyContact) {
        val prefs = AppPreferences(context)
        val currentContacts = prefs.getEmergencyContacts().toMutableList()
        currentContacts.removeAll { it.id == contact.id }
        prefs.setEmergencyContacts(currentContacts)
        loadData(context)
    }

    fun showContactSelector(category: String = "other") {
        _uiState.value = _uiState.value.copy(showContactSelector = true, selectedCategory = category)
    }
    
    fun dismissContactSelector() {
        _uiState.value = _uiState.value.copy(showContactSelector = false, selectedCategory = null)
    }
    
    // ... existing methods ...

    suspend fun addEmergencyContacts(context: Context, contacts: List<EmergencyContact>, category: String = "other") {
        val prefs = AppPreferences(context)
        val currentContacts = prefs.getEmergencyContacts().toMutableList()
        var hasChanges = false
        
        contacts.forEach { contact ->
            if (currentContacts.none { it.id == contact.id || it.phoneNumber == contact.phoneNumber }) {
                currentContacts.add(contact.copy(category = category))
                hasChanges = true
            }
        }
        
        if (hasChanges) {
            prefs.setEmergencyContacts(currentContacts)
            loadData(context)
        }
    }
    
    suspend fun renameApp(context: Context, app: AppInfo, newName: String) {
        val prefs = AppPreferences(context)
        if (app.isSystemApp || app.id.startsWith("custom_")) {
            // 自定义应用或快捷应用
            val currentApps = prefs.getQuickApps().toMutableList()
            val index = currentApps.indexOfFirst { it.id == app.id }
            if (index >= 0) {
                currentApps[index] = app.copy(name = newName)
                prefs.setQuickApps(currentApps)
            } else {
                val customApps = prefs.getCustomApps().toMutableList()
                val customIndex = customApps.indexOfFirst { it.id == app.id }
                if (customIndex >= 0) {
                    customApps[customIndex] = app.copy(name = newName)
                    prefs.setCustomApps(customApps)
                }
            }
        }
        loadData(context)
    }
}
