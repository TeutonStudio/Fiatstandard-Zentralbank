package de.teutonstudio.zentralbank.simulation

import java.nio.file.Path

fun main(args: Array<String>) {
    val spiele = args.getOrNull(0)?.toIntOrNull() ?: 100
    val seed = args.getOrNull(1)?.toLongOrNull() ?: 42L
    val ausgabe = Path.of(args.getOrNull(2) ?: "build/liga")
    val bericht = AgentenLiga.ausfuehren(spiele, seed)
    AgentenLiga.exportieren(bericht, ausgabe)
    println("Agentenliga: ${bericht.spiele} Spiele, ${bericht.fehler.size} Fehler")
}
