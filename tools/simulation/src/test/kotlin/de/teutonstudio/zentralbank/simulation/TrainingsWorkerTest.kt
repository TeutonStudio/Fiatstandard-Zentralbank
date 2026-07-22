package de.teutonstudio.zentralbank.simulation

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingsWorkerTest {
    @Test
    fun workerUnterstuetztResetObserveLegalStepBatchUndStrukturierteFehler() {
        val worker = TrainingsWorker()
        val reset = worker.verarbeite(buildJsonObject {
            put("command", "reset")
            put("environmentId", "eins")
            put("seed", 42)
        }).first
        assertTrue(reset.getValue("ok").jsonPrimitive.boolean)
        assertEquals(2, reset.getValue("observationSchemaVersion").jsonPrimitive.int)
        assertEquals(2, reset.getValue("actionSchemaVersion").jsonPrimitive.int)
        assertEquals(2, reset.getValue("episodeSchemaVersion").jsonPrimitive.int)

        val legal = worker.verarbeite(buildJsonObject {
            put("command", "legal_actions")
            put("environmentId", "eins")
        }).first
        val aktionen = legal.getValue("actions").jsonArray
        assertTrue(aktionen.isNotEmpty())
        val kanonisch = aktionen.first().jsonObject.getValue("canonical").jsonPrimitive.content
        val schritt = worker.verarbeite(buildJsonObject {
            put("command", "step")
            put("environmentId", "eins")
            put("actionCanonical", kanonisch)
        }).first
        assertTrue(schritt.getValue("ok").jsonPrimitive.boolean)
        assertEquals(3, schritt.getValue("rewards").jsonObject.size)

        val batch = worker.verarbeite(buildJsonObject {
            put("command", "batch_reset")
            put("environments", buildJsonArray {
                add(buildJsonObject { put("environmentId", "zwei"); put("seed", 1) })
                add(buildJsonObject { put("environmentId", "drei"); put("seed", 2) })
            })
        }).first
        assertEquals(2, batch.getValue("results").jsonArray.size)

        val fehler = worker.verarbeite(buildJsonObject {
            put("command", "observe")
            put("environmentId", "unbekannt")
        }).first
        assertFalse(fehler.getValue("ok").jsonPrimitive.boolean)
        assertTrue(fehler.getValue("error").jsonObject.containsKey("code"))
    }
}
