package com.bohannjein.zack.utils

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainExecutor = context.mainExecutor
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onDeviceFound: (String, String) -> Unit) {
        stopDiscovery()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    resolveModern(serviceInfo, onDeviceFound)
                } else {
                    resolveLegacy(serviceInfo, onDeviceFound)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { stopDiscovery() }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) { stopDiscovery() }
        }

        nsdManager.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveModern(serviceInfo: NsdServiceInfo, onDeviceFound: (String, String) -> Unit) {
        nsdManager.registerServiceInfoCallback(
            serviceInfo,
            mainExecutor,
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

                override fun onServiceUpdated(si: NsdServiceInfo) {
                    val host = si.hostAddresses.firstOrNull()
                    if (host != null) onDeviceFound(si.serviceName, host.hostAddress ?: "")
                }

                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(serviceInfo: NsdServiceInfo, onDeviceFound: (String, String) -> Unit) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(si: NsdServiceInfo) {
                val host = si.host
                if (host != null) onDeviceFound(si.serviceName, host.hostAddress ?: "")
            }
        })
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) { }
        }
        discoveryListener = null
    }
}
