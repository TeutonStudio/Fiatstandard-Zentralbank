package de.teutonstudio.zentralbank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.teutonstudio.zentralbank.datenbank.GameViewModel
import de.teutonstudio.zentralbank.datenbank.Ausland
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.datenbank.Zahlungsmittel
import de.teutonstudio.zentralbank.daten.zuordnung.zuBauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.ZugPhase
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeSpieler
import de.teutonstudio.zentralbank.schnittstelle.eingabe.ProzugDialog
import de.teutonstudio.zentralbank.schnittstelle.domain.zuProzugAnzeigeZustand
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.AnleiheDialog
import de.teutonstudio.zentralbank.schnittstelle.eingabe.HandelDialog
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AnleihenRegister
import de.teutonstudio.zentralbank.schnittstelle.kategorien.Hauptmenü
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielErstellen
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielLaden
import de.teutonstudio.zentralbank.schnittstelle.kategorien.Spielmenü
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielmenueBereich
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielmenueDialog
import de.teutonstudio.zentralbank.schnittstelle.kategorien.zeigeAussenhandel
import de.teutonstudio.zentralbank.schnittstelle.kategorien.zeigeMarktplatz
import de.teutonstudio.zentralbank.spielbrett.KartenSpielBildschirm
import de.teutonstudio.zentralbank.spielbrett.RundenwechselNacht
import de.teutonstudio.zentralbank.spielbrett.spielzugZeitfenster

private fun Screen.navigiere(navController: NavHostController): () -> Unit = { navController.navigate(route = this.route) }

private data class HandelsVorauswahl(
    val rohstoff: Rohstoffe,
    val gesamtpreis: Zahlungsmittel,
)

@Composable
private fun MitAktuellemSpiel(
    viewModel: GameViewModel,
    navController: NavHostController,
    inhalt: @Composable (Spiel) -> Unit,
) {
    val spiel = viewModel.aktuellesSpielOderNull
    if (spiel == null) {
        LaunchedEffect(navController) {
            navController.navigate(Screen.StartScreen.route) {
                popUpTo(Screen.StartScreen.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    } else {
        inhalt(spiel)
    }
}

sealed class Screen(val route: String) {
    object StartScreen: Screen(route = "main_screen")
    object NewGame: Screen(route = "new_game")
    object LoadGame: Screen(route = "load_game")
    object Game: Screen(route = "game")
    object GameMap: Screen(route = "game_map")
    object PlayerSaldo: Screen(route = "player_saldo")
    object DebtSaldo: Screen(route = "debt_saldo")
    object MarketSaldo: Screen(route = "market_saldo")
    object ForeignSaldo: Screen(route = "foreign_saldo")
    object PriceIndex: Screen(route = "price_index")
    object NewTrade: Screen(route = "new_trade")
    object NewCredit: Screen(route = "new_credit")
    object NewBuild: Screen(route = "new_build")
}

@Composable
fun Navigation(viewModel: GameViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val rundenwechselZustand by viewModel.rundenwechselAnzeige.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.spielFehler.collect { meldung ->
            snackbarHostState.showSnackbar(meldung)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innenabstand ->
            NavHost(
                navController = navController,
                startDestination = Screen.StartScreen.route,
                modifier = Modifier.padding(innenabstand),
            ) {
            composable(route = Screen.StartScreen.route) {
                Hauptmenü(
                    Screen.NewGame.navigiere(navController),
                    Screen.LoadGame.navigiere(navController)
                )
            }

            composable(route = Screen.NewGame.route) {
                SpielErstellen(
                    Screen.StartScreen.navigiere(navController),
                    viewModel::erstelleSpiel,
                    Screen.GameMap.navigiere(navController),
                )
            }

            composable(route = Screen.LoadGame.route) {
                SpielLaden(
                    Screen.StartScreen.navigiere(navController),
                    viewModel.vernichteSpiel,
                    viewModel.ladeSpiel,
                    Screen.Game.navigiere(navController),
                    viewModel.spielstaende.collectAsState().value
                )
            }

        composable(route = Screen.Game.route) {
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                val spielUebersicht = viewModel.spielUebersicht.collectAsState().value
                val spielZustand = viewModel.spielZustand.collectAsState().value
                var geoeffneterBereich by remember(spiel) {
                    mutableStateOf<SpielmenueBereich?>(null)
                }
                var bauauftrag by remember(spiel) { mutableStateOf<BauteilTyp?>(null) }
                var handelDialogOffen by remember(spiel) { mutableStateOf(false) }
                var handelsVorauswahl by remember(spiel) {
                    mutableStateOf<HandelsVorauswahl?>(null)
                }
                var anleiheDialogOffen by remember(spiel) { mutableStateOf(false) }
                val aktiverIndex = spielZustand?.let { zustand ->
                    zustand.spieler.indexOfFirst { it.id == zustand.aktiverSpieler }
                } ?: -1
                val zugZeitText = spielZustand
                    ?.takeIf { aktiverIndex >= 0 && it.spieler.isNotEmpty() }
                    ?.let { zustand ->
                        spielzugZeitfenster(aktiverIndex, zustand.spieler.size).text
                    }
                if (spielZustand == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Der Spielzustand wird geladen.")
                    }
                } else {
                    Spielmenü(
                        zustand = spielZustand,
                        zugText = spielUebersicht?.zug?.text ?: "Kein Zug aktiv",
                        zugZeitText = zugZeitText,
                        beiBereich = { bereich -> geoeffneterBereich = bereich },
                        beiZugBeenden = viewModel::beendeZug,
                        beiSpielBeenden = {
                            viewModel.spielstandBeenden {
                                navController.navigate(Screen.StartScreen.route) {
                                    popUpTo(Screen.StartScreen.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        KartenSpielBildschirm(
                            zustand = spielZustand,
                            beiEreignis = viewModel::ereignisAnwenden,
                            beiRueckgaengig = viewModel::ereignisRueckgaengig,
                            beiWiederholen = viewModel::ereignisWiederholen,
                            modifier = Modifier.fillMaxSize(),
                            kompakteZentrale = true,
                            vorgewaehltesBauteil = bauauftrag,
                            beiBauauftragBeendet = { bauauftrag = null },
                            beiBauAusFinanzmitteln = viewModel::baueMitAuslandseinkauf,
                            beiBauplanAusLager = viewModel::bauplanAnwenden,
                            beiBauplanAusFinanzmitteln = viewModel::baueBauplanMitAuslandseinkauf,
                        )
                    }
                }

                geoeffneterBereich?.let { bereich ->
                    SpielmenueDialog(
                        titel = bereich.beschriftung,
                        beiSchliessen = { geoeffneterBereich = null },
                    ) {
                        when (bereich) {
                            SpielmenueBereich.SALDO -> zeigeSpieler(
                                spiel = spiel,
                                konfliktAktionenAktiv = spielZustand?.zugStatus?.phase == ZugPhase.Epizug,
                                onDeclareWar = { (aggressor, verteidiger) ->
                                    viewModel.kriegErklaeren(aggressor, verteidiger)
                                },
                                onDeclarePeace = { (spielerA, spielerB) ->
                                    viewModel.friedenSchliessen(spielerA, spielerB)
                                },
                            )
                            SpielmenueBereich.SCHULDEN -> AnleihenRegister(
                                spiel = spiel,
                                spielerBauSaldo = emptyMap(),
                                runden = emptyList(),
                                beiEmission = {
                                    geoeffneterBereich = null
                                    anleiheDialogOffen = true
                                },
                            )
                            SpielmenueBereich.MARKTPLATZ -> zeigeMarktplatz(
                                spiel = spiel,
                                onWarenkorbAendern = viewModel::aktualisiereWarenkorb,
                                beiBauteilKlick = { bauteil ->
                                    when (val typ = bauteil.zuBauteilTyp()) {
                                        BauteilTyp.HAUPTBAHNHOF -> viewModel.meldeSpielFehler(
                                            "Der Hauptbahnhof kann nur in Runde 0 platziert werden."
                                        )
                                        else -> {
                                            bauauftrag = typ
                                            geoeffneterBereich = null
                                        }
                                    }
                                },
                            )
                            SpielmenueBereich.AUSSENHANDEL -> zeigeAussenhandel(
                                spiel = spiel,
                                beiRohstoffKlick = { rohstoff, importpreis ->
                                    handelsVorauswahl = HandelsVorauswahl(rohstoff, importpreis)
                                    geoeffneterBereich = null
                                    handelDialogOffen = true
                                },
                            )
                        }
                    }
                }
                val prozugAnzeige = spielZustand
                    ?.takeIf { rundenwechselZustand == null }
                    ?.zuProzugAnzeigeZustand()
                if (prozugAnzeige != null) {
                    ProzugDialog(
                        zustand = prozugAnzeige,
                        onVerarbeiten = viewModel::verarbeitungAusfuehren,
                        onVersorgen = viewModel::verwaltungsstandortVersorgen,
                        onBezahlen = viewModel::verbindlichkeitBegleichen,
                        onHandel = {
                            handelsVorauswahl = null
                            handelDialogOffen = true
                        },
                        onAussenhandel = {
                            handelsVorauswahl = null
                            handelDialogOffen = true
                        },
                        onAnleihe = { anleiheDialogOffen = true },
                        onAbschliessen = viewModel::prozugAbschliessen,
                    )
                }
                if (handelDialogOffen) {
                    val aktiverSpieler = spiel.spielerListe.firstOrNull { spieler ->
                        spieler.name == spielZustand?.aktiverSpieler?.wert
                    }
                    HandelDialog(
                        spiel = spiel,
                        onDismiss = {
                            handelDialogOffen = false
                            handelsVorauswahl = null
                        },
                        onCreateRohstoff = { handel ->
                            if (viewModel.erfasseRohstoffhandel(handel)) {
                                handelDialogOffen = false
                                handelsVorauswahl = null
                            }
                        },
                        onCreateAnleihe = { handel ->
                            if (viewModel.erfasseAnleihenhandel(handel)) {
                                handelDialogOffen = false
                                handelsVorauswahl = null
                            }
                        },
                        initialerRohstoff = handelsVorauswahl?.rohstoff,
                        initialerBesitzer = handelsVorauswahl?.let { Ausland },
                        initialerErwerber = handelsVorauswahl?.let { aktiverSpieler },
                        initialerGesamtpreis = handelsVorauswahl?.gesamtpreis,
                    )
                }
                if (anleiheDialogOffen) {
                    AnleiheDialog(
                        spiel = spiel,
                        aktuellerSpielerName = spielZustand?.zugStatus?.spieler?.wert
                            ?: spiel.spielerStringListe.firstOrNull().orEmpty(),
                        onDismiss = { anleiheDialogOffen = false },
                        onCreate = { handel ->
                            if (viewModel.erfasseAnleihenhandel(handel)) {
                                anleiheDialogOffen = false
                            }
                        },
                    )
                }
            }
        }

        composable(route = Screen.GameMap.route) {
            MitAktuellemSpiel(viewModel, navController) {
                val zustand = viewModel.spielZustand.collectAsState().value
                var rundeNullBegonnen by remember { mutableStateOf(false) }
                LaunchedEffect(zustand?.spielabschnitt) {
                    when (zustand?.spielabschnitt) {
                        Spielabschnitt.RUNDE_NULL -> rundeNullBegonnen = true
                        Spielabschnitt.REGULAER -> if (rundeNullBegonnen) {
                            navController.navigate(Screen.Game.route) {
                                popUpTo(Screen.StartScreen.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        null -> Unit
                    }
                }
                Titel(beiZurück = { navController.popBackStack() }) {
                    if (zustand == null) {
                        Text("Der Spielzustand wird geladen.")
                    } else {
                        KartenSpielBildschirm(
                            zustand = zustand,
                            beiEreignis = viewModel::ereignisAnwenden,
                            beiRueckgaengig = viewModel::ereignisRueckgaengig,
                            beiWiederholen = viewModel::ereignisWiederholen,
                            modifier = Modifier.fillMaxSize(),
                            beiBauplanAusLager = viewModel::bauplanAnwenden,
                            beiBauplanAusFinanzmitteln = viewModel::baueBauplanMitAuslandseinkauf,
                        )
                    }
                }
            }
        }

        composable(route = Screen.PlayerSaldo.route) { // TODO
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                val zugPhase = viewModel.spielZustand.collectAsState().value?.zugStatus?.phase
                Titel(Screen.Game.navigiere(navController)) {
                    zeigeSpieler(
                        spiel = spiel,
                        konfliktAktionenAktiv = zugPhase == ZugPhase.Epizug,
                        onDeclareWar = { (aggressor, verteidiger) ->
                            viewModel.kriegErklaeren(aggressor, verteidiger)
                        },
                        onDeclarePeace = { (spielerA, spielerB) ->
                            viewModel.friedenSchliessen(spielerA, spielerB)
                        },
                    )
                }
            }
        }

        composable(route = Screen.DebtSaldo.route) { // TODO
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                Titel(Screen.Game.navigiere(navController)) {
                    AnleihenRegister(
                        spiel = spiel,
                        spielerBauSaldo = emptyMap(),
                        runden = emptyList(),
                        onDelete = {},
                        onNew = {},
                        beiEmission = { navController.navigate(Screen.NewCredit.route) },
                    )
                }
            }
        }

        composable(route = Screen.MarketSaldo.route) {
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                Titel(Screen.Game.navigiere(navController)) {
                    zeigeMarktplatz(
                        spiel = spiel,
                        onWarenkorbAendern = viewModel::aktualisiereWarenkorb,
                    )
                }
            }
        }

        composable(route = Screen.ForeignSaldo.route) {
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                Titel(Screen.Game.navigiere(navController)) {
                    zeigeAussenhandel(spiel)
                }
            }
        }

        composable(route = Screen.NewTrade.route) {
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                HandelDialog(
                    spiel = spiel,
                    onDismiss = { navController.popBackStack() },
                    onCreateRohstoff = { handel ->
                        if (viewModel.erfasseRohstoffhandel(handel)) {
                            navController.popBackStack()
                        }
                    },
                    onCreateAnleihe = { handel ->
                        if (viewModel.erfasseAnleihenhandel(handel)) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }

        composable(route = Screen.NewCredit.route) {
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                AnleiheDialog(
                    spiel = spiel,
                    aktuellerSpielerName = viewModel.spielZustand.collectAsState().value
                        ?.zugStatus?.spieler?.wert
                        ?: spiel.spielerStringListe.firstOrNull().orEmpty(),
                    onDismiss = { navController.popBackStack() },
                    onCreate = { handel ->
                        if (viewModel.erfasseAnleihenhandel(handel)) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }

        composable(route = Screen.NewBuild.route) {
            Titel(Screen.Game.navigiere(navController)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
            }
        }

        rundenwechselZustand?.let { zustand ->
            RundenwechselNacht(
                zustand = zustand,
                modifier = Modifier.fillMaxSize(),
                beiAbgeschlossen = {
                    viewModel.rundenwechselAngezeigt()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.StartScreen.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
