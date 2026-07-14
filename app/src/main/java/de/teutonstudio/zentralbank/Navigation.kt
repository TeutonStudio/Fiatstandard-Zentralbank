package de.teutonstudio.zentralbank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.GameViewModel
import de.teutonstudio.zentralbank.domain.zug.Phase
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

private fun Screen.navigiere(navController: NavHostController): () -> Unit = { navController.navigate(route = this.route) }

sealed class Screen(val route: String) {
    object StartScreen: Screen(route = "main_screen")
    object NewGame: Screen(route = "new_game")
    object LoadGame: Screen(route = "load_game")
    object Game: Screen(route = "game")
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
        viewModel.domainFehler.collect { meldung ->
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
                    viewModel.erstelleSpiel,
                    Screen.Game.navigiere(navController)
                )
            }

            composable(route = Screen.LoadGame.route) {
                SpielLaden(
                    Screen.StartScreen.navigiere(navController),
                    viewModel.vernichteSpiel,
                    viewModel.ladeSpiel,
                    Screen.Game.navigiere(navController),
                    viewModel.spielSpeicher.collectAsState().value
                )
            }

        composable(route = Screen.Game.route) {
            val domainUiState = viewModel.domainUiState.collectAsState().value
            val domainState = viewModel.domainState.collectAsState().value
            Spielmenü(
                { navController.navigate(route = Screen.PlayerSaldo.route) },
                { navController.navigate(route = Screen.DebtSaldo.route) },
                { navController.navigate(route = Screen.MarketSaldo.route) },
                { navController.navigate(route = Screen.ForeignSaldo.route) },
                { navController.navigate(route = Screen.NewTrade.route) },
                { navController.navigate(route = Screen.NewCredit.route) },
                viewModel::naechsterZugabschnitt,
                zugText = domainUiState?.zug?.text ?: "Kein Zug aktiv",
            )
            val zugStatus = domainState?.zugStatus
            if (zugStatus?.phase == Phase.Ausgaben) {
                AusgabenDialog(
                    plan = viewModel.aktuellesSpiel.erhalteAusgabenplan(
                        spielerName = zugStatus.spieler.wert,
                        runde = domainState.rundenzähler,
                    ),
                    onClose = viewModel::naechsterZugabschnitt,
                )
            }
        }

        var ausgewähltesBauwerk: Bauteil
        composable(route = Screen.PlayerSaldo.route) { // TODO
            Titel(Screen.Game.navigiere(navController)) {
                zeigeSpieler(viewModel.aktuellesSpiel, {
                    navController.navigate(route = "new_build")
                    ausgewähltesBauwerk = it
                }, { spieler, bauteil, wahr ->
                }, { (aggressor,verteidiger) ->
                    viewModel.declareWar(aggressor,verteidiger)
                }, { (aggressor,verteidiger) ->
                    if (aggressor.second >= verteidiger.second) { // Sieg
                        viewModel.declareMilitary(aggressor.first,aggressor.second)
                    } else { // Niederlage
                        viewModel.declareMilitary(verteidiger.first,verteidiger.second)
                    }
                }, { (aggressor,verteidiger) ->
                    viewModel.declarePeace(aggressor,verteidiger)
                } )
            }
        }

        composable(route = Screen.DebtSaldo.route) { // TODO
            val domainState = viewModel.domainState.collectAsState().value
            Titel(Screen.Game.navigiere(navController)) {
                AnleihenRegister(
                    spiel = viewModel.aktuellesSpiel,
                    spielerBauSaldo = emptyMap(),
                    runden = emptyList(),
                    onDelete = {},
                    onNew = {},
                    aktiverSpielerName = domainState?.zugStatus?.spieler?.wert,
                )
            }
        }

        composable(route = Screen.MarketSaldo.route) {
            Titel(Screen.Game.navigiere(navController)) {
                zeigeMarktplatz(
                    spiel = viewModel.aktuellesSpiel,
                    onWarenkorbAendern = viewModel::aktualisiereWarenkorb,
                )
            }
        }

        composable(route = Screen.ForeignSaldo.route) {
            Titel(Screen.Game.navigiere(navController)) {
                zeigeAussenhandel(viewModel.aktuellesSpiel)
            }
        }

        composable(route = Screen.NewTrade.route) {
            HandelDialog(
                spiel = viewModel.aktuellesSpiel,
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

        composable(route = Screen.NewCredit.route) {
            AnleiheDialog(
                spiel = viewModel.aktuellesSpiel,
                aktuellerSpielerName = viewModel.domainState.collectAsState().value
                    ?.zugStatus?.spieler?.wert
                    ?: viewModel.aktuellesSpiel.spielerStringListe.firstOrNull().orEmpty(),
                onDismiss = { navController.popBackStack() },
                onCreate = { handel ->
                    if (viewModel.erfasseAnleihenhandel(handel)) {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(route = Screen.NewBuild.route) {
            Titel(Screen.Game.navigiere(navController)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
            }
        }
    }
}
}
