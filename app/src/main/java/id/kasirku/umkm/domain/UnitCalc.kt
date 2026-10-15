package id.kasirku.umkm.domain

import kotlin.math.floor
import kotlin.math.roundToLong

/** Satu pilihan satuan untuk dijual / dibeli. factor = isi dalam satuan dasar (satuan dasar: factor 1). */
data class UnitOption(
    val name: String,
    val factor: Double,
    val sellPrice: Long,
)

object UnitCalc {

    /** Jumlah dalam satuan dasar. 2 dus × 40 = 80 pcs. */
    fun toBase(qty: Double, factor: Double): Double = Qty.round(qty * factor)

    /** Modal per satuan besar = modal per satuan dasar × isi. */
    fun costPerUnit(baseCost: Long, factor: Double): Long = (baseCost * factor).roundToLong()

    /** Harga beli per dus dikonversi ke modal per pcs. Rp 110.000 / 40 = Rp 2.750. */
    fun baseCostFrom(unitCost: Long, factor: Double): Long =
        if (factor <= 0.0) unitCost else (unitCost / factor).roundToLong()

    /**
     * Rincian stok ke satuan terbesar dulu.
     * 172 pcs dengan Renteng=10, Dus=40 -> "4 dus + 1 renteng + 2 pcs".
     * Stok 0 atau minus ditampilkan apa adanya dalam satuan dasar.
     */
    fun breakdown(baseQty: Double, baseUnit: String, bigUnits: List<UnitOption>): String {
        val total = Qty.round(baseQty)
        if (total <= 0.0 || bigUnits.isEmpty()) return "${Qty.format(total)} $baseUnit"
        var rest = total
        val parts = mutableListOf<String>()
        for (u in bigUnits.filter { it.factor > 1.0 }.sortedByDescending { it.factor }) {
            val n = floor(Qty.round(rest / u.factor) + 1e-9)
            if (n >= 1.0) {
                parts += "${n.toLong()} ${u.name}"
                rest = Qty.round(rest - n * u.factor)
            }
        }
        if (rest > 0.0 || parts.isEmpty()) parts += "${Qty.format(rest)} $baseUnit"
        return parts.joinToString(" + ")
    }

    /** Jumlahkan hasil hitung fisik ke satuan dasar: [(3 dus, 40), (2 renteng, 10), (4 pcs, 1)] -> 144. */
    fun sumParts(parts: List<Pair<Double, Double>>): Double = Qty.round(parts.sumOf { (qty, factor) -> qty * factor })

    /** Pesan kesalahan untuk daftar satuan besar, atau null jika valid. */
    fun validate(baseUnit: String, bigUnits: List<UnitOption>): String? {
        val names = mutableSetOf(baseUnit.trim().lowercase())
        val factors = mutableSetOf<Double>()
        for (u in bigUnits) {
            val n = u.name.trim().lowercase()
            if (n.isEmpty()) return "Nama satuan besar wajib diisi."
            if (!names.add(n)) return "Nama satuan \"${u.name}\" dipakai dua kali."
            if (u.factor <= 1.0) return "Isi satuan \"${u.name}\" harus lebih dari 1 $baseUnit."
            if (!factors.add(Qty.round(u.factor))) return "Dua satuan besar tidak boleh punya isi yang sama."
            if (u.sellPrice <= 0) return "Harga jual per ${u.name} wajib diisi."
        }
        return null
    }
}
