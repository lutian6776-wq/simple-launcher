package com.launcher.senior.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.data.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OneClickCallUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val familyContacts: List<EmergencyContact> = emptyList(),
    val otherContacts: List<EmergencyContact> = emptyList(),
    val callConfirmation: EmergencyContact? = null, // 待确认的联系人
    val showContactSelector: Boolean = false, // 显示联系人选择器
    val selectedCategory: String? = null // 当前选择的联系人类别
)

class OneClickCallViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OneClickCallUiState())
    val uiState: StateFlow<OneClickCallUiState> = _uiState.asStateFlow()

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

    fun showContactSelector(category: String = "other") {
        _uiState.value = _uiState.value.copy(
            showContactSelector = true,
            selectedCategory = category
        )
    }

    fun dismissContactSelector() {
        _uiState.value = _uiState.value.copy(
            showContactSelector = false,
            selectedCategory = null
        )
    }

    suspend fun addEmergencyContacts(
        context: Context,
        contacts: List<EmergencyContact>,
        category: String = "other"
    ) {
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
            loadContacts(context)
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
