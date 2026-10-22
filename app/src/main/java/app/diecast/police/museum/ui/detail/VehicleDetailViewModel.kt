package app.diecast.police.museum.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.diecast.police.museum.data.model.Vehicle
import app.diecast.police.museum.data.repository.AuthRepository
import app.diecast.police.museum.data.repository.MuseumRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VehicleDetailViewModel(
    private val repository: MuseumRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        const val STATUS_COLLECTION = "collection"
        const val STATUS_WISHLIST = "wishlist"
    }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val vehicle: Vehicle? = null,
        /** The user's status for this vehicle: "collection" | "wishlist" | null */
        val myStatus: String? = null,
        val collectionCount: Int = 0,
        val wishlistCount: Int = 0,
        val busy: Boolean = false,
        val message: String? = null,
        val needsAuth: Boolean = false
    ) {
        val inCollection: Boolean get() = myStatus == STATUS_COLLECTION
        val inWishlist: Boolean get() = myStatus == STATUS_WISHLIST
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var vehicleId: Int = -1

    fun load(id: Int) {
        if (id == vehicleId && !_state.value.loading) return
        vehicleId = id
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            try {
                val vehicleDeferred = async { repository.fetchVehicle(id) }
                val countsDeferred = async { repository.fetchCounts(listOf(id)) }
                val vehicle = vehicleDeferred.await()
                val counts = countsDeferred.await()[id]

                if (vehicle == null) {
                    _state.update {
                        it.copy(loading = false, error = "Vehicle not found.")
                    }
                    return@launch
                }

                _state.update {
                    it.copy(
                        loading = false,
                        vehicle = vehicle,
                        collectionCount = counts?.collectionCount ?: 0,
                        wishlistCount = counts?.wishlistCount ?: 0
                    )
                }

                loadMyStatus(id)
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Could not load vehicle details.")
                }
            }
        }
    }

    private suspend fun loadMyStatus(id: Int) {
        val token = authRepository.validToken() ?: return
        val userId = authRepository.sessionFlow.firstOrNull()?.userId ?: return
        val status = repository.fetchUserStatus(userId, id, token)
        _state.update { it.copy(myStatus = status) }
    }

    /** The collection / wishlist button. */
    fun toggle(target: String) {
        val current = _state.value
        val vehicle = current.vehicle ?: return
        if (current.busy) return

        viewModelScope.launch {
            val token = authRepository.validToken()
            val session = authRepository.sessionFlow.firstOrNull()
            if (token == null || session == null) {
                _state.update { it.copy(needsAuth = true) }
                return@launch
            }

            _state.update { it.copy(busy = true, message = null) }
            val newStatus = if (current.myStatus == target) null else target
            val ok = repository.setUserStatus(
                userId = session.userId,
                vehicleId = vehicle.id,
                status = newStatus,
                token = token
            )

            if (ok) {
                _state.update {
                    it.copy(
                        busy = false,
                        myStatus = newStatus,
                        message = when (newStatus) {
                            STATUS_COLLECTION -> "Added to collection."
                            STATUS_WISHLIST -> "Added to wishlist."
                            else -> "Removed from lists."
                        }
                    )
                }
                refreshCounts(vehicle.id)
            } else {
                _state.update {
                    it.copy(busy = false, message = "Operation failed. Please try again.")
                }
            }
        }
    }

    private fun refreshCounts(id: Int) {
        viewModelScope.launch {
            val counts = repository.fetchCounts(listOf(id))[id] ?: return@launch
            _state.update {
                it.copy(
                    collectionCount = counts.collectionCount,
                    wishlistCount = counts.wishlistCount
                )
            }
        }
    }

    fun consumeNeedsAuth() {
        _state.update { it.copy(needsAuth = false) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
