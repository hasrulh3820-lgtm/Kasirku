package id.kasirku.umkm.data.db

/** SQL migrasi dipisah ke sini agar bisa diuji tanpa perangkat Android. */
object MigrationSql {
    val V1_TO_V2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `product_units` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `factor` REAL NOT NULL, " +
            "`sellPrice` INTEGER NOT NULL, `barcode` TEXT, `sortOrder` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_product_units_productId` ON `product_units` (`productId`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_units_barcode` ON `product_units` (`barcode`)",
        "ALTER TABLE `sale_items` ADD COLUMN `unitFactor` REAL NOT NULL DEFAULT 1",
    )
}
