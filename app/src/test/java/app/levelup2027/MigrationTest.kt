package app.levelup2027

import app.levelup2027.data.BackupManager
import app.levelup2027.data.Cat
import app.levelup2027.data.Campaign
import app.levelup2027.data.Migration1to2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Builds a real SQLite database from the exported v1 schema, fills it like a v1 user's phone,
 * runs the v1 → v2 migration, and checks the result matches the exported v2 schema column by
 * column (type, NOT NULL, default, primary key) and index by index, as Room will on the device.
 */
class MigrationTest {
    private val schemaDir = File("schemas/app.levelup2027.data.AppDatabase").let { if (it.exists()) it else File("app/schemas/app.levelup2027.data.AppDatabase") }
    private fun schema(v: Int) = Json.parseToJsonElement(File(schemaDir, "$v.json").readText()).jsonObject.getValue("database").jsonObject

    private fun Connection.run(sql: String) = createStatement().use { it.execute(sql) }
    private fun Connection.rows(sql: String): List<Map<String, Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val md = rs.metaData
            buildList { while (rs.next()) add((1..md.columnCount).associate { md.getColumnName(it) to rs.getObject(it) }) }
        }
    }

    private fun v1Database(): Connection {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        for (e in schema(1).getValue("entities").jsonArray.map { it.jsonObject }) {
            val table = e.getValue("tableName").jsonPrimitive.content
            c.run(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { c.run(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        val day = Campaign.START.toEpochDay()
        c.run("INSERT INTO tasks VALUES ('mon-gym','Gym','', 'FITNESS','EVENING','• lift','• walk',60,20,NULL,'WEEKLY:MON',NULL,NULL,NULL,'fitness','HIGH',30,1,1,'','',1,0,0)")
        c.run("INSERT INTO tasks VALUES ('user-1','My task','', 'TECHNICAL','MORNING','• a','• b',30,10,${day + 3},NULL,NULL,NULL,NULL,NULL,'LOW',0,1,0,'note','',0,0,0)")
        c.run("INSERT INTO completions (taskId, taskTitle, scheduledDate, completionType, completedAt, skipReason, notes) VALUES ('mon-gym','Gym',$day,'MINIMUM',123,NULL,'tired')")
        c.run("INSERT INTO goals VALUES ('fitness','Fitness','d','t',${Campaign.END.toEpochDay()},'~63 kg','kg','active',1)")
        c.run("INSERT INTO progress_entries (goalId, date, metric, value, notes, isEstimate, createdAt) VALUES ('fitness',$day,'weight_kg',63.0,'',1,0)")
        c.run("INSERT INTO weekly_reviews (weekStartDate, wins, blockers, adjustments, nextPriority, notes, createdAt, updatedAt) VALUES ($day,'w','b','a','p','n',0,0)")
        return c
    }

    @Test fun `v1 to v2 produces exactly the v2 schema`() {
        val c = v1Database()
        Migration1to2.statements().forEach { c.run(it) }
        val expected = schema(2).getValue("entities").jsonArray.map { it.jsonObject }
        val tables = c.rows("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").map { it["name"] as String }.toSet()
        assertEquals(expected.map { it.getValue("tableName").jsonPrimitive.content }.toSet(), tables)

        for (e in expected) {
            val table = e.getValue("tableName").jsonPrimitive.content
            val actual = c.rows("PRAGMA table_info(`$table`)").associateBy { it["name"] as String }
            val fields = e.getValue("fields").jsonArray.map { it.jsonObject }
            assertEquals("$table columns", fields.map { it.getValue("columnName").jsonPrimitive.content }.toSet(), actual.keys)
            val pk = e.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray.map { it.jsonPrimitive.content }
            for (f in fields) {
                val name = f.getValue("columnName").jsonPrimitive.content
                val col = actual.getValue(name)
                assertEquals("$table.$name type", f.getValue("affinity").jsonPrimitive.content, col["type"])
                val notNull = f["notNull"]?.jsonPrimitive?.booleanOrNull ?: false
                // SQLite reports NOT NULL as 0 for INTEGER PRIMARY KEY columns declared inline; Room compares the same way.
                if (name !in pk || pk.size > 1) assertEquals("$table.$name notNull", if (notNull) 1 else 0, (col["notnull"] as Number).toInt())
                assertEquals("$table.$name default", f["defaultValue"]?.jsonPrimitive?.content, col["dflt_value"])
                assertEquals("$table.$name pk", name in pk, (col["pk"] as Number).toInt() > 0)
            }
            val expectedIdx = e["indices"]?.jsonArray?.map { it.jsonObject.getValue("name").jsonPrimitive.content }?.toSet().orEmpty()
            val actualIdx = c.rows("PRAGMA index_list(`$table`)").map { it["name"] as String }.filter { it.startsWith("index_") }.toSet()
            assertEquals("$table indices", expectedIdx, actualIdx)
        }
    }

    @Test fun `v1 data survives and is upgraded`() {
        val c = v1Database()
        Migration1to2.statements().forEach { c.run(it) }
        val tasks = c.rows("SELECT * FROM tasks").associateBy { it["id"] }
        assertEquals(Cat.FITNESS, tasks.getValue("mon-gym")["category"])
        assertEquals(Cat.LEARNING, tasks.getValue("user-1")["category"])
        assertEquals("SEED", tasks.getValue("mon-gym")["source"])
        assertEquals("MANUAL", tasks.getValue("user-1")["source"])
        assertEquals(Campaign.START.toEpochDay(), (tasks.getValue("mon-gym")["activeFrom"] as Number).toLong())
        assertEquals(Campaign.END.toEpochDay(), (tasks.getValue("mon-gym")["activeUntil"] as Number).toLong())
        assertEquals(null, tasks.getValue("user-1")["activeUntil"])
        assertEquals("note", tasks.getValue("user-1")["notes"])
        assertEquals(2, (tasks.getValue("user-1")["priority"] as Number).toInt())

        val comp = c.rows("SELECT * FROM completions").single()
        assertEquals("MINIMUM", comp["completionType"])
        assertEquals("tired", comp["notes"])
        val goal = c.rows("SELECT * FROM goals").single()
        assertEquals("Fitness", goal["shortName"])
        assertEquals(Cat.FITNESS, goal["categoryId"])
        assertEquals(1, c.rows("SELECT * FROM progress_entries").size)
        assertEquals(1, c.rows("SELECT * FROM weekly_reviews").size)
        assertEquals(Cat.DEFAULTS.size, c.rows("SELECT * FROM categories").size)
    }

    @Test fun `a v1 backup file restores with categories mapped`() {
        val v1 = """{"format":"levelup2027-backup","version":1,"exportedAt":1,
            "tasks":[{"id":"t","title":"T","description":"","category":"RECOVERY","timeSlot":"EVENING","idealDescription":"i","minimumDescription":"m",
              "estimatedIdealMinutes":10,"estimatedMinimumMinutes":2,"recurrenceRule":"DAILY","energyLevel":"LOW"}],
            "completions":[],"goals":[],"milestones":[],"progress":[],"reviews":[],"settings":{"seedVersion":1}}"""
        val f = BackupManager.parse(v1)
        assertEquals(Cat.HEALTH, f.tasks.single().category)
        assertTrue(f.categories.isEmpty())
        assertEquals(2, f.tasks.single().priority) // new fields get defaults
    }
}
