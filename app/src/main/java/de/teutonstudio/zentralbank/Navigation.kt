package de.teutonstudio.zentralbank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.czboracle.ui.composables.AnleihenRegister
import de.teutonstudio.zentralbank.datenbank.Bauteil
import de.teutonstudio.zentralbank.datenbank.GameViewModel
import de.teutonstudio.zentralbank.ui.Titel
import de.teutonstudio.zentralbank.ui.ausgabe.zeigeSpieler
import de.teutonstudio.zentralbank.ui.eingabe.SteuerContainer
import de.teutonstudio.zentralbank.ui.eingabe.bearbeiteRunde
import de.teutonstudio.zentralbank.ui.kategorien.SpielLaden
import de.teutonstudio.zentralbank.ui.kategorien.Hauptmenü
import de.teutonstudio.zentralbank.ui.kategorien.SpielErstellen
import de.teutonstudio.zentralbank.ui.kategorien.Spielmenü
import de.teutonstudio.zentralbank.ui.kategorien.zeigeAussenhandel
import de.teutonstudio.zentralbank.ui.kategorien.zeigeMarktplatz


sealed class Screen(val route: String) {
    object StartScreen: Screen(route = "main_screen")
    object NewGame: Screen(route = "new_game")
    object LoadGame: Screen(route = "load_game")
    object Game: Screen(route = "game")
    object EditRound: Screen(route = "edit_round")
    object PlayerSaldo: Screen(route = "player_saldo")
    object DebtSaldo: Screen(route = "debt_saldo")
    object MarketSaldo: Screen(route = "market_saldo")
    object ForeignSaldo: Screen(route = "foreign_saldo")
    object PriceIndex: Screen(route = "price_index")
    object NewTrade: Screen(route = "new_trade")
    object NewCredit: Screen(route = "new_credit")
    object NewBuild: Screen(route = "new_build")
//    object TradeRegister : Screen(route = "trade_register")
//    object CreditRegister : Screen(route = "credit_register")
}

@Composable
fun Navigation(viewModel: GameViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val hauptMenü = { navController.navigate(route = Screen.StartScreen.route) }
    val spielMenü = { navController.navigate(route = Screen.Game.route) }

    NavHost(
        navController = navController,
        startDestination = Screen.StartScreen.route
    ) {
        composable(route = Screen.StartScreen.route) {
            Titel(hauptMenü) { Hauptmenü(
                beiNeu = { navController.navigate(route = Screen.NewGame.route) },
                beiLade = { navController.navigate(route = Screen.LoadGame.route) }
            ) }
        }

        composable(route = Screen.NewGame.route) {
            Titel(hauptMenü) {
                SpielErstellen({ navController.navigate(route = Screen.StartScreen.route) }) {
                    viewModel.erstelleSpiel(it)
                    spielMenü()
                }
            }
        }

        composable(route = Screen.LoadGame.route) {
            Titel(hauptMenü) {
                SpielLaden(hauptMenü, viewModel.spielSpeicher.collectAsState().value,{ viewModel.vernichteSpiel(it) }) {
                    viewModel.ladeSpiel(it)
                    spielMenü()
                }
            }
        }

        composable(route = Screen.Game.route) {
            Titel(hauptMenü) {
                SteuerContainer(
                    hatZurückWeiter = false,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Spielmenü(
                            beiVermogenSaldo = { navController.navigate(route = Screen.PlayerSaldo.route) },
                            beiSchuldenSaldo = { navController.navigate(route = Screen.DebtSaldo.route) },
                            beiMarktSaldo = { navController.navigate(route = Screen.MarketSaldo.route) },
                            beiAuslandSaldo = { navController.navigate(route = Screen.ForeignSaldo.route) },
                            beiHandel = { navController.navigate(route = Screen.NewTrade.route) },
                            beiAnleihe = { navController.navigate(route = Screen.NewCredit.route) },
                        ) { navController.navigate(route = Screen.EditRound.route) }
                    }
                }
            }
        }

        composable(route = Screen.EditRound.route) {
            Titel(spielMenü) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bearbeiteRunde(
                    viewModel.aktuelleDaten.first.spielID,
                    viewModel.aktuellesSpiel,
                ) { navController.navigate(route = Screen.Game.route) }
            } }
        }

        var ausgewähltesBauwerk: Bauteil
        composable(route = Screen.PlayerSaldo.route) {
            Titel(spielMenü) {
                zeigeSpieler(viewModel.aktuellesSpiel, {
                    navController.navigate(route = "new_build")
                    ausgewähltesBauwerk = it
                }, { spieler, bauteil, wahr ->
/*                    viewModel.neuesBauwerk(Bauwerk(
                        viewModel.aktuellesSpielDaten.SpielID,
                        viewModel.aktuelleRunde,
                        siedler,
                        bauwerke,
                    ))*/
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

        composable(route = Screen.DebtSaldo.route) {
            Titel(spielMenü) {
                AnleihenRegister(viewModel.aktuellesSpiel, emptyMap(),emptyList(),  {},{})
            }
        }

        composable(route = Screen.MarketSaldo.route) {
            Titel(spielMenü) {
                zeigeMarktplatz(viewModel.aktuellesSpiel) { /*inputTrade: HandelsDaten ->
                    viewModel.neuerHandel(inputTrade.copy(runde = viewModel.aktuelleRunde))
                    println(inputTrade)
                    navController.navigate(route = Screen.Game.route)*/
                }
            }
        }

        composable(route = Screen.ForeignSaldo.route) {
            Titel(spielMenü) {
                zeigeAussenhandel(viewModel.aktuellesSpiel)
            }
        }

        composable(route = Screen.NewTrade.route) {
            Titel(spielMenü) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
/*                CreateTrade(viewModel.playerList.collectAsState().value.map { it.playerName }, viewModel.currentRound, viewModel.marketPrices) { trade ->
                    viewModel.newTrade(trade)
                    navController.navigate(route = Screen.Game.route)
                }*/
//                EditTrade(
//                    trade = Trade(-1,viewModel.currentGame.GameID,viewModel.currentRound,"","",0,"",0f),
//                    viewModel.currentRound, viewModel.marketPrices[viewModel.currentRound-1], viewModel.playerList.collectAsState().value
//                ) { inputTrade: Trade ->
//                    viewModel.newTrade(inputTrade)
//                    println(inputTrade)
//                    navController.navigate(route = Screen.Game.route)
//                }
            } }
        }

//        composable(route = Screen.PriceIndex.route) {
//            CZBScrenn(spielMenü) {
//                ShowPreisindex(viewModel.marketPrices)
//            }
//        }

        composable(route = Screen.NewCredit.route) {
            Titel(spielMenü) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
/*                CreateAnleihe(viewModel.playerList.collectAsState().value.map { it.playerName }, viewModel.currentRound) { credit ->
                    viewModel.newCredit(credit)
                    navController.navigate(route = Screen.Game.route)
                }*/
//                EditCredit(
//                    CreditIndex = -1, viewModel.currentRound,
//                    playerNetworthList = viewModel.playerNetworth.last()
//                ) { inputCredit: Credit ->
//                    viewModel.newCredit(inputCredit)
//                    navController.navigate(route = Screen.Game.route)
//                    // TODO
//                }
            } }
        }

        composable(route = Screen.NewBuild.route) {
            Titel(spielMenü) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
/*                EditBuild(viewModel.currentRound, viewModel.playerList.collectAsState().value, ausgewähltesBauwerk) { build ->
                    viewModel.newBuild(build)
                    navController.navigate(route = "player_saldo")
                }*/
            } }
        }
    }
}