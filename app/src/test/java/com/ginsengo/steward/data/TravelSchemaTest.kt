package com.ginsengo.steward.data

import com.ginsengo.steward.data.db.AppDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Schema 6 adds the travel memory's table and changes nothing that was there (wave M.1). */
class TravelSchemaTest {

    private fun entities(version: Int): Map<String, JsonObject> {
        val json = Json.parseToJsonElement(File("schemas/com.ginsengo.steward.data.db.AppDatabase/$version.json").readText()) as JsonObject
        val list = (json["database"] as JsonObject)["entities"] as JsonArray
        return list.associate { e -> e as JsonObject; (e["tableName"] as JsonPrimitive).content to e }
    }

    @Test
    fun theMigrationsCreateSqlIsRoomsOwn() {
        val room = (entities(6).getValue("visited_cells")["createSql"] as JsonPrimitive).content.replace("\${TABLE_NAME}", "visited_cells")
        assertEquals(room, AppDatabase.CREATE_VISITED_CELLS)
    }

    @Test
    fun everySchema5TableIsUnchanged() {
        val v5 = entities(5)
        val v6 = entities(6)
        assertEquals(v5.keys + "visited_cells", v6.keys)
        for ((table, e) in v5) {
            assertEquals(table, e["createSql"], v6.getValue(table)["createSql"])
            assertEquals(table, e["indices"], v6.getValue(table)["indices"])
        }
    }
}
