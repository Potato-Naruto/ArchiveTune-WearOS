/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 * Portions © vossgraves — github.com/vossgraves
 */

package moe.rukamori.archivetune.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(
        val release: WearRelease,
    ) : UpdateState

    data class Downloading(
        val percent: Int,
    ) : UpdateState

    data object Failed : UpdateState
}

class UpdateViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installing: StateFlow<WearUpdater.InstallState> = WearUpdater.installing

    val version: String = WearUpdater.installedVersion(app)

    fun canInstall(): Boolean = WearUpdater.canInstall(app)

    fun check() {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            _state.value =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val release = WearUpdater.fetchLatest()
                        if (release != null && WearUpdater.isNewer(app, release)) {
                            UpdateState.Available(release)
                        } else {
                            UpdateState.UpToDate
                        }
                    }
                }.getOrDefault(UpdateState.Failed)
        }
    }

    fun install(release: WearRelease) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            _state.value =
                runCatching {
                    withContext(Dispatchers.IO) {
                        val apk =
                            WearUpdater.download(app, release) { percent ->
                                _state.value = UpdateState.Downloading(percent)
                            }
                        WearUpdater.install(app, apk)
                    }
                    UpdateState.Available(release)
                }.getOrDefault(UpdateState.Failed)
        }
    }
}
