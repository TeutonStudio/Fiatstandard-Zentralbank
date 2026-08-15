package de.teutonstudio.zentralbank.netzwerk

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Sehr kleiner HTTP/1.1-Transport für einen Spielhost direkt auf Android. */
class WlanSpielHost(
    dienst: SpielNetzwerkDienst,
    freigegebenesSpielId: Long,
) : AutoCloseable {
    private val router = SpielNetzwerkRouter(
        dienst = dienst,
        freigegebenesSpielId = freigegebenesSpielId,
        spielErstellenErlaubt = false,
    )
    private val aktiv = AtomicBoolean(false)
    private val akzeptor = Executors.newSingleThreadExecutor { aufgabe ->
        Thread(aufgabe, "fiatstandard-wlan-accept").apply { isDaemon = true }
    }
    private val arbeiter = Executors.newCachedThreadPool { aufgabe ->
        Thread(aufgabe, "fiatstandard-wlan-client").apply { isDaemon = true }
    }
    private var serverSocket: ServerSocket? = null

    val port: Int
        get() = serverSocket?.localPort ?: 0

    fun starten(port: Int = 0) {
        check(aktiv.compareAndSet(false, true)) { "WLAN-Host läuft bereits." }
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        serverSocket = socket
        akzeptor.execute {
            while (aktiv.get()) {
                try {
                    val client = socket.accept()
                    arbeiter.execute { client.use(::bearbeite) }
                } catch (_: Exception) {
                    if (aktiv.get()) aktiv.set(false)
                }
            }
        }
    }

    override fun close() {
        aktiv.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        akzeptor.shutdownNow()
        arbeiter.shutdownNow()
    }

    private fun bearbeite(socket: Socket) {
        socket.soTimeout = 8_000
        val eingabe = BufferedInputStream(socket.getInputStream())
        val anfrageZeile = leseZeile(eingabe) ?: return
        val teile = anfrageZeile.split(' ', limit = 3)
        if (teile.size < 2) return
        val kopfzeilen = linkedMapOf<String, String>()
        while (true) {
            val zeile = leseZeile(eingabe) ?: break
            if (zeile.isEmpty()) break
            val trennung = zeile.indexOf(':')
            if (trennung > 0) {
                kopfzeilen[zeile.substring(0, trennung).trim()] =
                    zeile.substring(trennung + 1).trim()
            }
        }
        val laenge = kopfzeilen.entries
            .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value
            ?.toIntOrNull()
            ?: 0
        require(laenge in 0..MAXIMALE_ANFRAGE_BYTES) { "Anfrage ist zu groß." }
        val inhalt = if (laenge == 0) "" else {
            val bytes = ByteArray(laenge)
            var position = 0
            while (position < bytes.size) {
                val gelesen = eingabe.read(bytes, position, bytes.size - position)
                if (gelesen < 0) error("Anfrage wurde vorzeitig beendet.")
                position += gelesen
            }
            bytes.toString(StandardCharsets.UTF_8)
        }
        val antwort = router.bearbeiten(
            NetzwerkAnfrage(
                methode = teile[0],
                pfad = teile[1],
                kopfzeilen = kopfzeilen,
                inhalt = inhalt,
            ),
        )
        val antwortBytes = antwort.inhalt.toByteArray(StandardCharsets.UTF_8)
        val ausgabe = socket.getOutputStream()
        val kopf = buildString {
            append("HTTP/1.1 ${antwort.status} ${statusText(antwort.status)}\r\n")
            append("Content-Type: ${antwort.inhaltstyp}\r\n")
            append("Content-Length: ${antwortBytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }.toByteArray(StandardCharsets.ISO_8859_1)
        ausgabe.write(kopf)
        ausgabe.write(antwortBytes)
        ausgabe.flush()
    }

    private fun leseZeile(eingabe: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val zeichen = eingabe.read()
            if (zeichen < 0) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.ISO_8859_1.name())
            if (zeichen == '\n'.code) break
            if (zeichen != '\r'.code) bytes.write(zeichen)
            require(bytes.size() <= MAXIMALE_KOPFZEILE_BYTES) { "HTTP-Kopfzeile ist zu lang." }
        }
        return bytes.toString(StandardCharsets.ISO_8859_1.name())
    }

    private fun statusText(status: Int): String = when (status) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        409 -> "Conflict"
        422 -> "Unprocessable Content"
        else -> "Internal Server Error"
    }

    companion object {
        fun lokaleIpv4Adresse(): String? = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses).asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

        private const val MAXIMALE_ANFRAGE_BYTES = 1_048_576
        private const val MAXIMALE_KOPFZEILE_BYTES = 16_384
    }
}
