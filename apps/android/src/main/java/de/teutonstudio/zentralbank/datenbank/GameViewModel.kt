package de.teutonstudio.zentralbank.datenbank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.teutonstudio.zentralbank.anwendung.SpielstandUebersicht
import de.teutonstudio.zentralbank.fachlogik.ereignis.SpielEreignis
import de.teutonstudio.zentralbank.fachlogik.modell.KartenEcke
import de.teutonstudio.zentralbank.fachlogik.modell.KartenFeld
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.fachlogik.modell.SpielZustand
import de.teutonstudio.zentralbank.fachlogik.modell.VerbindlichkeitId
import de.teutonstudio.zentralbank.schnittstelle.domain.SpielUebersichtZustand
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-Lifecycle-Fassade. Fachaktionen und Sitzungszustand liegen im Application-Layer;
 * verbleibende Tabellen-/Legacy-Synchronisierung ist in [LegacySpielKoordinator] isoliert.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val koordinator = LegacySpielKoordinator(application, viewModelScope)

    val spielstaende: StateFlow<List<SpielstandUebersicht>> = koordinator.spielstaende
    val spielZustand: StateFlow<SpielZustand?> = koordinator.spielZustand
    val spielUebersicht: StateFlow<SpielUebersichtZustand?> = koordinator.spielUebersicht
    val rundenwechselAnzeige: StateFlow<SpielZustand?> = koordinator.rundenwechselAnzeige
    val spielFehler: SharedFlow<String> = koordinator.spielFehler

    val aktuellesSpielOderNull: Spiel?
        get() = koordinator.aktuellesSpielOderNull

    fun ereignisAnwenden(ereignis: SpielEreignis) = koordinator.ereignisAnwenden(ereignis)
    fun meldeSpielFehler(meldung: String) = koordinator.meldeSpielFehler(meldung)
    fun passwortGeschuetzteSpieler(namen: Collection<String>): List<String> =
        koordinator.passwortGeschuetzteSpieler(namen)
    fun pruefeSpielerPasswoerter(passwoerter: Map<String, String>): Boolean =
        koordinator.pruefeSpielerPasswoerter(passwoerter)
    fun spielstandBeenden(nachBeenden: () -> Unit) = koordinator.spielstandBeenden(nachBeenden)

    fun baueMitAuslandseinkauf(
        bauEreignis: SpielEreignis,
        fehlendeRohstoffe: Map<Rohstoff, Int>,
    ): Boolean = koordinator.baueMitAuslandseinkauf(bauEreignis, fehlendeRohstoffe)

    fun baueBauplanMitAuslandseinkauf(
        bauEreignisse: List<SpielEreignis>,
        fehlendeRohstoffe: Map<Rohstoff, Int>,
    ): Boolean = koordinator.baueBauplanMitAuslandseinkauf(bauEreignisse, fehlendeRohstoffe)

    fun bauplanAnwenden(ereignisse: List<SpielEreignis>): Boolean =
        koordinator.bauplanAnwenden(ereignisse)

    fun ereignisRueckgaengig() = koordinator.ereignisRueckgaengig()
    fun ereignisWiederholen() = koordinator.ereignisWiederholen()
    fun prozugAbschliessen() = koordinator.prozugAbschliessen()
    fun verarbeitungAusfuehren(feld: KartenFeld, laeufe: Int) =
        koordinator.verarbeitungAusfuehren(feld, laeufe)
    fun verwaltungsstandortVersorgen(ecke: KartenEcke) =
        koordinator.verwaltungsstandortVersorgen(ecke)
    fun verbindlichkeitBegleichen(verbindlichkeit: VerbindlichkeitId) =
        koordinator.verbindlichkeitBegleichen(verbindlichkeit)
    fun beendeZug() = koordinator.beendeZug()
    fun rundenwechselAngezeigt() = koordinator.rundenwechselAngezeigt()
    fun aktualisiereWarenkorb(warenkorb: Map<Rohstoffe, Int>) =
        koordinator.aktualisiereWarenkorb(warenkorb)
    fun erfasseRohstoffhandel(handel: RohstoffHandel): Boolean =
        koordinator.erfasseRohstoffhandel(handel)
    fun emittiereAnleihe(handel: Anleihenhandel): Boolean = koordinator.emittiereAnleihe(handel)
    fun erfasseAnleihenhandel(handel: Anleihenhandel): Boolean =
        koordinator.erfasseAnleihenhandel(handel)
    fun erstelleSpiel(spiel: Spiel, nachErstellen: () -> Unit) =
        koordinator.erstelleSpiel(spiel, nachErstellen)
    fun kriegErklaeren(aggressor: String, verteidiger: String) =
        koordinator.kriegErklaeren(aggressor, verteidiger)
    fun friedenSchliessen(spielerA: String, spielerB: String) =
        koordinator.friedenSchliessen(spielerA, spielerB)

    val vernichteSpiel: (Long) -> Unit = koordinator.vernichteSpiel
    val ladeSpiel: (Long, () -> Unit) -> Unit = koordinator.ladeSpiel

    class GameViewModelFactory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                return GameViewModel(application) as T
            }
            throw IllegalArgumentException("Unbekannte ViewModel-Klasse: ${modelClass.name}")
        }
    }
}
