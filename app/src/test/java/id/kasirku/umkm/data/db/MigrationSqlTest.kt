package id.kasirku.umkm.data.db

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Memastikan SQL migrasi v1 -> v2 persis sama dengan skema yang dihasilkan Room (app/schemas/.../2.json).
 * Jika berbeda, Room akan menolak membuka database lama di HP pengguna, jadi build harus gagal di sini.
 */
class MigrationSqlTest {

    private val schema: String by lazy {
        val f = File("schemas/id.kasirku.umkm.data.db.AppDatabase/2.json")
        assertTrue("Skema Room v2 tidak ditemukan di ${f.absolutePath}", f.exists())
        f.readText()
    }

    private fun roomSql(sql: String, table: String) = sql.replace("`$table`", "`\${TABLE_NAME}`")

    @Test
    fun productUnitsTableMatchesRoom() {
        val create = MigrationSql.V1_TO_V2[0]
        assertTrue("CREATE TABLE product_units tidak cocok dengan Room", schema.contains(roomSql(create, "product_units")))
    }

    @Test
    fun productUnitsIndicesMatchRoom() {
        for (sql in MigrationSql.V1_TO_V2.subList(1, 3)) {
            val expected = sql.replace("ON `product_units`", "ON `\${TABLE_NAME}`")
            assertTrue("Index tidak cocok dengan Room: $sql", schema.contains(expected))
        }
    }

    @Test
    fun saleItemsUnitFactorMatchesRoom() {
        val compact = schema.replace(Regex("\\s+"), "")
        val field = Regex("\\{[^{}]*\"columnName\":\"unitFactor\"[^{}]*\\}").find(compact)?.value
        assertTrue("Kolom unitFactor tidak ada di skema Room", field != null)
        assertTrue("unitFactor harus REAL: $field", field!!.contains("\"affinity\":\"REAL\""))
        assertTrue("unitFactor harus NOT NULL: $field", field.contains("\"notNull\":true"))
        assertTrue("default unitFactor harus 1: $field", field.contains("\"defaultValue\":\"1\""))
    }
}
