package de.teutonstudio.zentralbank.schnittstelle.domain

import de.teutonstudio.zentralbank.domain.GameState

data class GameUiState(
    val zug: ZugAnzeige,
    val spieler: List<SpielerAnzeige>,
)

fun GameState.zuGameUiState(): GameUiState {
    return GameUiState(
        zug = zuZugAnzeige(),
        spieler = zuSpielerAnzeigen(),
    )
}
