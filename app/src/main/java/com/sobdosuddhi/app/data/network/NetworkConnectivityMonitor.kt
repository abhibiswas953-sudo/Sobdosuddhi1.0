package com.sobdosuddhi.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityProvider {
  val isOnline: StateFlow<Boolean>
  fun refreshNow(): Boolean
}

/**
 * Observes real Android network availability via ConnectivityManager.NetworkCallback.
 * Allows seamless offline startup and automatic switch back to Online Mode when internet returns.
 */
class NetworkConnectivityMonitor(context: Context) : ConnectivityProvider {

  private val appContext = context.applicationContext
  private val connectivityManager: ConnectivityManager? =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  @Volatile
  private var testOverride: Boolean? = null

  private val _isOnline = MutableStateFlow(querySystemConnectivity())
  override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      updateStateFromSystem()
    }

    override fun onLost(network: Network) {
      updateStateFromSystem()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
      updateStateFromSystem()
    }

    override fun onUnavailable() {
      updateStateFromSystem()
    }
  }

  init {
    registerSafely()
  }

  private fun registerSafely() {
    val cm = connectivityManager ?: return
    runCatching {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
      cm.registerNetworkCallback(request, networkCallback)
    }
  }

  private fun updateStateFromSystem() {
    if (testOverride != null) return
    _isOnline.value = querySystemConnectivity()
  }

  private fun querySystemConnectivity(): Boolean {
    testOverride?.let { return it }
    val cm = connectivityManager ?: return false
    return runCatching {
      val active = cm.activeNetwork ?: return@runCatching false
      val caps = cm.getNetworkCapabilities(active) ?: return@runCatching false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
          caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
          caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
          caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }.getOrDefault(false)
  }

  override fun refreshNow(): Boolean {
    val online = querySystemConnectivity()
    _isOnline.value = online
    return online
  }

  /**
   * Used exclusively in automated unit & UI tests to verify Offline -> Online transitions deterministically.
   */
  fun setTestOverride(online: Boolean?) {
    testOverride = online
    _isOnline.value = online ?: querySystemConnectivity()
  }
}
