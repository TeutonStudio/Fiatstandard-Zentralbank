package de.teutonstudio.zentralbank.netzwerk

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.nio.charset.StandardCharsets

/** Android-NSD/mDNS für automatische Spieleerkennung im selben WLAN. */
class WlanSpielEntdeckung(context: Context) : AutoCloseable {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrierung: NsdManager.RegistrationListener? = null
    private var suche: NsdManager.DiscoveryListener? = null

    fun hostVeroeffentlichen(
        spielId: Long,
        port: Int,
        name: String = "Fiatstandard Spiel $spielId",
        beiFehler: (String) -> Unit = {},
    ) {
        hostVerbergen()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = DIENST_TYP
            this.port = port
            setAttribute("spielId", spielId.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                beiFehler("WLAN-Spiel konnte nicht veröffentlicht werden (NSD $errorCode).")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                beiFehler("WLAN-Spiel konnte nicht sauber abgemeldet werden (NSD $errorCode).")
            }
        }
        registrierung = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Suppress("DEPRECATION")
    fun spieleSuchen(
        beiFund: (WlanEndpunkt) -> Unit,
        beiEntfernt: (String) -> Unit = {},
        beiFehler: (String) -> Unit = {},
    ) {
        sucheBeenden()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.startsWith(DIENST_TYP.substringBeforeLast('.'))) return
                nsd.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            beiFehler("WLAN-Spiel ${serviceInfo.serviceName} konnte nicht aufgelöst werden (NSD $errorCode).")
                        }

                        override fun onServiceResolved(aufgeloest: NsdServiceInfo) {
                            val host = aufgeloest.host?.hostAddress ?: return
                            val id = aufgeloest.attributes["spielId"]
                                ?.toString(StandardCharsets.UTF_8)
                                ?.toLongOrNull()
                                ?: return
                            beiFund(
                                WlanEndpunkt(
                                    host = host,
                                    port = aufgeloest.port,
                                    spielId = id,
                                    name = aufgeloest.serviceName,
                                ),
                            )
                        }
                    },
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                beiEntfernt(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                beiFehler("WLAN-Suche konnte nicht gestartet werden (NSD $errorCode).")
                runCatching { nsd.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                beiFehler("WLAN-Suche konnte nicht beendet werden (NSD $errorCode).")
            }
        }
        suche = listener
        nsd.discoverServices(DIENST_TYP, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun sucheBeenden() {
        val listener = suche ?: return
        suche = null
        runCatching { nsd.stopServiceDiscovery(listener) }
    }

    fun hostVerbergen() {
        val listener = registrierung ?: return
        registrierung = null
        runCatching { nsd.unregisterService(listener) }
    }

    override fun close() {
        sucheBeenden()
        hostVerbergen()
    }

    companion object {
        const val DIENST_TYP = "_fiatstandard._tcp."
    }
}
