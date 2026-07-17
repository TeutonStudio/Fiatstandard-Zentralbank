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
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.GameViewModel
import de.teutonstudio.zentralbank.datenbank.Spiel
import de.teutonstudio.zentralbank.fachlogik.modell.Phase
import de.teutonstudio.zentralbank.fachlogik.modell.Spielabschnitt
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.zeigeSpieler
import de.teutonstudio.zentralbank.schnittstelle.eingabe.AusgabenDialog
import de.teutonstudio.zentralbank.schnittstelle.eingabe.Titel
import de.teutonstudio.zentralbank.schnittstelle.eingabe.AnleiheDialog
import de.teutonstudio.zentralbank.schnittstelle.eingabe.HandelDialog
import de.teutonstudio.zentralbank.schnittstelle.kategorien.AnleihenRegister
import de.teutonstudio.zentralbank.schnittstelle.kategorien.Hauptmenü
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielErstellen
import de.teutonstudio.zentralbank.schnittstelle.kategorien.SpielLaden
import de.teutonstudio.zentralbank.schnittstelle.kategorien.Spielmenü
import de.teutonstudio.zentralbank.schnittstelle.kategorien.zeigeAussenhandel
import de.teutonstudio.zentralbank.schnittstelle.kategorien.zeigeMarktplatz
import de.teutonstudio.zentralbank.spielbrett.KartenSpielBildschirm

private fun Screen.navigiere(navController: NavHostController): () -> Unit = { navController.navigate(route = this.route) }

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

    LaunchedEffect(viewModel) {
        viewModel.spielFehler.collect { meldung ->
            snackbarHostState.showSnackbar(meldung)
        }
    }

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
                Spielmenü(
                    { navController.navigate(route = Screen.PlayerSaldo.route) },
                    { navController.navigate(route = Screen.DebtSaldo.route) },
                    { navController.navigate(route = Screen.MarketSaldo.route) },
                    { navController.navigate(route = Screen.ForeignSaldo.route) },
                    { navController.navigate(route = Screen.NewTrade.route) },
                    { navController.navigate(route = Screen.NewCredit.route) },
                    { navController.navigate(route = Screen.GameMap.route) },
                    viewModel::naechsterZugabschnitt,
                    spiel = spiel,
                    aktiverSpielerName = spielZustand?.zugStatus?.spieler?.wert,
                    zugText = spielUebersicht?.zug?.text ?: "Kein Zug aktiv",
                )
                val zugStatus = spielZustand?.zugStatus
                if (zugStatus?.phase == Phase.Ausgaben) {
                    AusgabenDialog(
                        plan = spiel.erhalteAusgabenplan(
                            spielerName = zugStatus.spieler.wert,
                            runde = spielZustand.rundenzähler,
                        ),
                        onClose = viewModel::naechsterZugabschnitt,
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
                        )
                    }
                }
            }
        }

        var ausgewähltesBauwerk: Bauteil
        composable(route = Screen.PlayerSaldo.route) { // TODO
            MitAktuellemSpiel(viewModel, navController) { spiel ->
                Titel(Screen.Game.navigiere(navController)) {
                    zeigeSpieler(spiel, {
                        navController.navigate(route = "new_build")
                        ausgewähltesBauwerk = it
                    }, { spieler, bauteil, wahr ->
                    }, { (aggressor,verteidiger) ->
                        viewModel.kriegErklaeren(aggressor,verteidiger)
                    }, { (aggressor,verteidiger) ->
                        if (aggressor.second >= verteidiger.second) { // Sieg
                            viewModel.militaerergebnisErfassen(aggressor.first,aggressor.second)
                        } else { // Niederlage
                            viewModel.militaerergebnisErfassen(verteidiger.first,verteidiger.second)
                        }
                    }, { (aggressor,verteidiger) ->
                        viewModel.friedenSchliessen(aggressor,verteidiger)
                    } )
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
}
