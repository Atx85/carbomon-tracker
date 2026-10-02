package com.example.carbomon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val CATALOG_SERVICE_TYPE = "_carbomon._tcp."

data class CatalogServer(val name: String, val address: String, val serverId: String)

// Discovery supplies an address, not authentication. The key is still entered by the user.
internal fun discoveredCatalogServer(
    name: String, host: InetAddress?, port: Int, attributes: Map<String, String>, expectedServerId: String
): CatalogServer? {
    val id = attributes["id"] ?: return null
    if (attributes["version"] != "1" || !id.matches(Regex("[a-zA-Z0-9_-]{1,200}"))) return null
    if (expectedServerId.isNotEmpty() && id != expectedServerId) return null
    if (host !is Inet4Address || host.isLoopbackAddress || !(host.isSiteLocalAddress || host.isLinkLocalAddress)) return null
    if (port !in 1..65535) return null
    return CatalogServer(name.take(100), "http://${host.hostAddress}:$port", id)
}

/** One bounded scan, with serial resolution for Android versions that only allow one at a time. */
@Suppress("DEPRECATION")
internal suspend fun discoverCatalogServers(context: Context, expectedServerId: String): List<CatalogServer> =
    withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val manager = context.getSystemService(NsdManager::class.java)
            val handler = Handler(Looper.getMainLooper())
            val found = linkedMapOf<String, CatalogServer>()
            val visible = mutableSetOf<String>()
            val pending = ArrayDeque<NsdServiceInfo>()
            var resolver: NsdManager.ResolveListener? = null
            var closed = false
            var registered = false
            var stopRetried = false
            lateinit var listener: NsdManager.DiscoveryListener
            lateinit var timeout: Runnable

            fun cleanup() {
                if (closed) return
                closed = true
                handler.removeCallbacks(timeout)
                pending.clear()
                if (registered) runCatching { manager.stopServiceDiscovery(listener) }
                if (Build.VERSION.SDK_INT >= 34) resolver?.let { runCatching { manager.stopServiceResolution(it) } }
                resolver = null
            }

            fun finish(error: Exception? = null) {
                if (closed) return
                cleanup()
                if (continuation.isActive) {
                    if (error == null) continuation.resume(found.values.distinctBy { it.address }.sortedBy { it.name })
                    else continuation.resumeWithException(error)
                }
            }

            fun resolveNext() {
                if (closed || !continuation.isActive || resolver != null || pending.isEmpty()) return
                val service = pending.removeFirst()
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                        handler.post {
                            if (!closed) { resolver = null; resolveNext() }
                        }
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        handler.post {
                            if (!closed) {
                                if (info.serviceName in visible) {
                                    val attributes = info.attributes.mapValues { it.value?.toString(Charsets.UTF_8).orEmpty() }
                                    discoveredCatalogServer(info.serviceName, info.host, info.port, attributes, expectedServerId)
                                        ?.let { found[info.serviceName] = it }
                                }
                                resolver = null
                                resolveNext()
                            }
                        }
                    }
                }
                resolver = resolveListener
                try { manager.resolveService(service, resolveListener) }
                catch (error: Exception) { resolver = null; finish(error) }
            }

            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    handler.post { if (closed) runCatching { manager.stopServiceDiscovery(this) } }
                }
                override fun onDiscoveryStopped(type: String) { handler.post { finish() } }
                override fun onStartDiscoveryFailed(type: String, code: Int) {
                    handler.post { finish(IllegalStateException("Discovery failed ($code)")) }
                }
                override fun onStopDiscoveryFailed(type: String, code: Int) {
                    handler.post {
                        if (!stopRetried) {
                            stopRetried = true
                            runCatching { manager.stopServiceDiscovery(this) }
                        }
                    }
                }
                override fun onServiceFound(info: NsdServiceInfo) {
                    handler.post {
                        if (!closed && info.serviceType.trimEnd('.') == CATALOG_SERVICE_TYPE.trimEnd('.') &&
                            visible.size < 30 && visible.add(info.serviceName)) {
                            pending.addLast(info)
                            resolveNext()
                        }
                    }
                }
                override fun onServiceLost(info: NsdServiceInfo) {
                    handler.post {
                        if (!closed) {
                            visible.remove(info.serviceName)
                            found.remove(info.serviceName)
                            pending.removeAll { it.serviceName == info.serviceName }
                        }
                    }
                }
            }
            timeout = Runnable { finish() }
            continuation.invokeOnCancellation { handler.post { cleanup() } }
            try {
                registered = true
                manager.discoverServices(CATALOG_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                handler.postDelayed(timeout, 10_000)
            } catch (error: Exception) { finish(error) }
        }
    }
