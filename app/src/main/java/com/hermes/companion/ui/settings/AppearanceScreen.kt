package com.hermes.companion.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermes.companion.settings.ThemeMode
import com.hermes.companion.settings.ThemePrefs
import com.hermes.companion.ui.components.HermesCard
import com.hermes.companion.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val prefs: ThemePrefs) : ViewModel() {
    val mode: StateFlow<ThemeMode> = prefs.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
    val dynamic: StateFlow<Boolean> = prefs.dynamic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setMode(m: ThemeMode) = viewModelScope.launch { prefs.setMode(m) }.let {}
    fun setDynamic(on: Boolean) = viewModelScope.launch { prefs.setDynamic(on) }.let {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val dynamic by vm.dynamic.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { vm.setMode(m) }, label = { Text(m.name) })
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                HermesCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Material You", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Use the system dynamic colour palette instead of the Hermes brand colours.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamic, onCheckedChange = { vm.setDynamic(it) })
                    }
                }
            }
            Text(
                "Dark-first is the Hermes look; Light and System are available. Changes apply instantly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
