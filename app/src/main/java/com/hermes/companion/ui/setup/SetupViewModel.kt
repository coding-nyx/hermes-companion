package com.hermes.companion.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.companion.data.repo.NodeRepository
import com.hermes.companion.data.repo.SetupRung
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    node: NodeRepository,
) : ViewModel() {
    val rungs: StateFlow<List<SetupRung>> = node.observeSetup()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
