package com.launcher.senior.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.data.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EmergencyCallUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val familyContacts: List<EmergencyContact> = emptyList(),
    val otherContacts: List<EmergencyContact> = emptyList(),
    val callConfirmation: EmergencyContact? = null // 待确认的联系人
)

class EmergencyCallViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EmergencyCallUiState())
    val uiState: StateFlow<EmergencyCallUiState> = _uiState.asStateFlow()
    
    fun loadContacts(context: Context) {
        viewModelScope.launch {
            val prefs = AppPreferences(context)
            var contacts = prefs.getEmergencyContacts()
            
            // 按首字母和排序键排序
            contacts = contacts.sortedWith(compareBy(
                { it.initialLetter ?: "#" },
                { it.sortKey ?: it.name }
            ))
            
            val familyContacts = contacts.filter { it.category == "family" }
            val otherContacts = contacts.filter { it.category != "family" }
            
            _uiState.value = _uiState.value.copy(
                contacts = contacts,
                familyContacts = familyContacts,
                otherContacts = otherContacts
            )
        }
    }
    
    fun initiateCall(contact: EmergencyContact) {
        // 显示确认对话框
        _uiState.value = _uiState.value.copy(callConfirmation = contact)
    }

    fun confirmCall(context: Context, contact: EmergencyContact) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${contact.phoneNumber}")
        }
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            // 权限被拒绝
        }
        cancelCall()
    }
    
    fun cancelCall() {
        _uiState.value = _uiState.value.copy(callConfirmation = null)
    }
}
