package ru.astrosmap.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.astrosmap.app.data.access.Entitlement
import ru.astrosmap.app.data.api.AstroApi

data class SavedMaterialsAccess(
    val loaded: Boolean = false,
    val pdfAllowed: Boolean = false,
)

@HiltViewModel
class SavedMaterialsViewModel @Inject constructor(
    private val api: AstroApi,
) : ViewModel() {
    private val _access = MutableStateFlow(SavedMaterialsAccess())
    val access: StateFlow<SavedMaterialsAccess> = _access

    init {
        viewModelScope.launch {
            val me = runCatching { api.me() }.getOrNull()
            _access.value = SavedMaterialsAccess(
                loaded = true,
                pdfAllowed = me?.accessState()?.hasEntitlement(Entitlement.PDF_EXPORT) == true ||
                    (me?.reportCredits ?: 0) > 0,
            )
        }
    }
}
