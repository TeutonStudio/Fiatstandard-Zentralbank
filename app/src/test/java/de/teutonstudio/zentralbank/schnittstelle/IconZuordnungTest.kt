package de.teutonstudio.zentralbank.schnittstelle

import de.teutonstudio.zentralbank.datenbank.Handelslinie
import de.teutonstudio.zentralbank.datenbank.Rohstoffe
import de.teutonstudio.zentralbank.datenbank.Verwaltungsstandort
import de.teutonstudio.zentralbank.datenbank.Wirtschaftsregionen
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilArt
import de.teutonstudio.zentralbank.fachlogik.modell.BauteilTyp
import de.teutonstudio.zentralbank.fachlogik.modell.Rohstoff
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.bauteilIconPfadOderNull
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.handelslinieIconPfad
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.handelslinieIconPfadOderNull
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.rohstoffIconPfad
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.verwaltungsstandortIconPfadOderNull
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.wirtschaftseinheitIconPfad
import de.teutonstudio.zentralbank.schnittstelle.ausgabe.wirtschaftseinheitIconPfadOderNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconZuordnungTest {
    private val rohstoffZuordnungen = listOf(
        Rohstoffe.NAHRUNG to Rohstoff.NAHRUNG,
        Rohstoffe.LEHM to Rohstoff.LEHM,
        Rohstoffe.ZIEGEL to Rohstoff.ZIEGEL,
        Rohstoffe.HOLZ to Rohstoff.HOLZ,
        Rohstoffe.ROHÖL to Rohstoff.ROHOEL,
        Rohstoffe.SCHWERÖL to Rohstoff.SCHWEROEL,
        Rohstoffe.DIESEL to Rohstoff.DIESEL,
        Rohstoffe.KOHLE to Rohstoff.KOHLE,
        Rohstoffe.STAHL to Rohstoff.STAHL,
        Rohstoffe.EISEN to Rohstoff.EISEN,
    )

    private val wirtschaftseinheitZuordnungen = listOf(
        Wirtschaftsregionen.GESCHÄFTSBANK to BauteilTyp.GESCHAEFTSBANK,
        Wirtschaftsregionen.VIEHHOF to BauteilTyp.VIEHHOF,
        Wirtschaftsregionen.ZIEGELBRENNER to BauteilTyp.ZIEGELBRENNER,
        Wirtschaftsregionen.LEHMINE to BauteilTyp.LEHMINE,
        Wirtschaftsregionen.FÖRSTER to BauteilTyp.FOERSTER,
        Wirtschaftsregionen.BOHRTURM to BauteilTyp.BOHRTURM,
        Wirtschaftsregionen.RAFFINERIE to BauteilTyp.RAFFINERIE,
        Wirtschaftsregionen.SRAFINNERIE to BauteilTyp.SYNTHETIK_RAFFINERIE,
        Wirtschaftsregionen.KOHLEMINE to BauteilTyp.KOHLEMINE,
        Wirtschaftsregionen.STAHLFABRIK to BauteilTyp.STAHLFABRIK,
        Wirtschaftsregionen.EISENMINE to BauteilTyp.EISENMINE,
    )

    private val handelslinienZuordnungen = listOf(
        Handelslinie.LAND to BauteilTyp.EISENBAHNLINIE,
        Handelslinie.SEE to BauteilTyp.FRACHTSCHIFF,
    )

    private val verwaltungsstandortZuordnungen = listOf(
        Verwaltungsstandort.BAHNHOF to BauteilTyp.BAHNHOF,
        Verwaltungsstandort.GROSSBAHNHOF to BauteilTyp.GROSSBAHNHOF,
        Verwaltungsstandort.HAFEN to BauteilTyp.HAFEN,
        Verwaltungsstandort.GROSSHAFEN to BauteilTyp.GROSSHAFEN,
    )

    @Test
    fun alleRohstoffeBesitzenEinEindeutigesIcon() {
        assertEquals(Rohstoffe.entries.size, rohstoffZuordnungen.size)
        assertEquals(Rohstoff.entries.size, rohstoffZuordnungen.size)

        val iconPfade = rohstoffZuordnungen.map { (alt, neu) ->
            alt.rohstoffIconPfad().also { assertEquals(it, neu.rohstoffIconPfad()) }
        }

        assertEquals(rohstoffZuordnungen.size, iconPfade.distinct().size)
    }

    @Test
    fun wirtschaftseinheitIconsSindAusschliesslichWirtschaftsregionenZugeordnet() {
        assertEquals(Wirtschaftsregionen.entries.size, wirtschaftseinheitZuordnungen.size)
        assertEquals(
            BauteilTyp.entries.count { it.art == BauteilArt.WIRTSCHAFTSREGION },
            wirtschaftseinheitZuordnungen.size,
        )

        val iconPfade = wirtschaftseinheitZuordnungen.map { (alt, neu) ->
            alt.wirtschaftseinheitIconPfad().also { iconPfad ->
                assertEquals(iconPfad, neu.wirtschaftseinheitIconPfadOderNull())
                assertEquals(iconPfad, alt.bauteilIconPfadOderNull())
                assertEquals(iconPfad, neu.bauteilIconPfadOderNull())
            }
        }

        assertEquals(wirtschaftseinheitZuordnungen.size, iconPfade.distinct().size)
        assertTrue(
            BauteilTyp.entries
                .filter { it.art != BauteilArt.WIRTSCHAFTSREGION }
                .all { it.wirtschaftseinheitIconPfadOderNull() == null },
        )
    }

    @Test
    fun handelslinienBesitzenJeweilsEinEigenesIcon() {
        assertEquals(Handelslinie.entries.size, handelslinienZuordnungen.size)
        assertEquals(
            BauteilTyp.entries.count { it.art == BauteilArt.HANDELSLINIE },
            handelslinienZuordnungen.size,
        )

        val iconPfade = handelslinienZuordnungen.map { (alt, neu) ->
            alt.handelslinieIconPfad().also { iconPfad ->
                assertEquals(iconPfad, neu.handelslinieIconPfadOderNull())
                assertEquals(iconPfad, alt.bauteilIconPfadOderNull())
                assertEquals(iconPfad, neu.bauteilIconPfadOderNull())
            }
        }

        assertEquals(handelslinienZuordnungen.size, iconPfade.distinct().size)
        assertTrue(
            BauteilTyp.entries
                .filter { it.art != BauteilArt.HANDELSLINIE }
                .all { it.handelslinieIconPfadOderNull() == null },
        )
    }

    @Test
    fun vorhandeneVerwaltungsstandortIconsSindSeparatZugeordnet() {
        val iconPfade = verwaltungsstandortZuordnungen.map { (alt, neu) ->
            requireNotNull(alt.verwaltungsstandortIconPfadOderNull()).also { iconPfad ->
                assertEquals(iconPfad, neu.verwaltungsstandortIconPfadOderNull())
                assertEquals(iconPfad, alt.bauteilIconPfadOderNull())
                assertEquals(iconPfad, neu.bauteilIconPfadOderNull())
            }
        }

        assertEquals(verwaltungsstandortZuordnungen.size, iconPfade.distinct().size)
        assertNull(Verwaltungsstandort.HAUPTBAHNHOF.verwaltungsstandortIconPfadOderNull())
        assertNull(Verwaltungsstandort.HAUPTBAHNHOF.bauteilIconPfadOderNull())
        assertNull(BauteilTyp.HAUPTBAHNHOF.verwaltungsstandortIconPfadOderNull())
        assertNull(BauteilTyp.HAUPTBAHNHOF.bauteilIconPfadOderNull())
    }
}
