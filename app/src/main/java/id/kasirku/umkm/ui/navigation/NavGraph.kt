package id.kasirku.umkm.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.kasirku.umkm.ui.cashier.CashierScreen
import id.kasirku.umkm.ui.cashier.ReceiptScreen
import id.kasirku.umkm.ui.cashier.SalesHistoryScreen
import id.kasirku.umkm.ui.debt.CustomerDebtScreen
import id.kasirku.umkm.ui.debt.DebtListScreen
import id.kasirku.umkm.ui.expense.ExpenseScreen
import id.kasirku.umkm.ui.home.HomeScreen
import id.kasirku.umkm.ui.report.ReportScreen
import id.kasirku.umkm.ui.settings.BackupScreen
import id.kasirku.umkm.ui.settings.StoreProfileScreen
import id.kasirku.umkm.ui.product.ProductDetailScreen
import id.kasirku.umkm.ui.product.ProductFormScreen
import id.kasirku.umkm.ui.product.ProductListScreen
import id.kasirku.umkm.ui.product.StockCountScreen
import id.kasirku.umkm.ui.product.StockInScreen

object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val PRODUCT_FORM = "product_form?id={id}"
    const val PRODUCT_DETAIL = "product/{id}"
    const val STOCK_IN = "stock_in/{id}"
    const val STOCK_COUNT = "stock_count"
    const val CASHIER = "cashier"
    const val RECEIPT = "receipt/{id}"
    const val SALES = "sales"
    const val STORE = "store"
    const val DEBTS = "debts"
    const val DEBT_CUSTOMER = "debt/{id}"
    const val EXPENSES = "expenses"
    const val REPORT = "report"
    const val BACKUP = "backup"

    fun debtCustomer(id: Long) = "debt/$id"

    fun receipt(id: Long) = "receipt/$id"

    fun productForm(id: Long? = null) = if (id == null) "product_form" else "product_form?id=$id"
    fun productDetail(id: Long) = "product/$id"
    fun stockIn(id: Long) = "stock_in/$id"
}

@Composable
fun KasirKuNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onProducts = { nav.navigate(Routes.PRODUCTS) },
                onAddProduct = { nav.navigate(Routes.productForm()) },
                onProductClick = { nav.navigate(Routes.productDetail(it)) },
                onCashier = { nav.navigate(Routes.CASHIER) },
                onSales = { nav.navigate(Routes.SALES) },
                onStore = { nav.navigate(Routes.STORE) },
                onDebts = { nav.navigate(Routes.DEBTS) },
                onExpenses = { nav.navigate(Routes.EXPENSES) },
                onReport = { nav.navigate(Routes.REPORT) },
                onBackup = { nav.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.REPORT) {
            ReportScreen(
                onBack = { nav.popBackStack() },
                onDebts = { nav.navigate(Routes.DEBTS) },
                onProducts = { nav.navigate(Routes.PRODUCTS) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.DEBTS) {
            DebtListScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.debtCustomer(it)) })
        }
        composable(Routes.DEBT_CUSTOMER, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            CustomerDebtScreen(onBack = { nav.popBackStack() }, onOpenSale = { nav.navigate(Routes.receipt(it)) })
        }
        composable(Routes.EXPENSES) {
            ExpenseScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CASHIER) {
            CashierScreen(
                onBack = { nav.popBackStack() },
                onDone = { nav.navigate(Routes.receipt(it)) },
                onHistory = { nav.navigate(Routes.SALES) },
            )
        }
        composable(Routes.RECEIPT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            ReceiptScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SALES) {
            SalesHistoryScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.receipt(it)) })
        }
        composable(Routes.STORE) {
            StoreProfileScreen(onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(); toast("Profil toko disimpan.") })
        }
        composable(Routes.PRODUCTS) {
            ProductListScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(Routes.productForm()) },
                onOpen = { nav.navigate(Routes.productDetail(it)) },
                onCount = { nav.navigate(Routes.STOCK_COUNT) },
            )
        }
        composable(Routes.STOCK_COUNT) {
            StockCountScreen(
                onBack = { nav.popBackStack() },
                onSaved = { msg -> nav.popBackStack(); toast(msg) },
            )
        }
        composable(
            Routes.PRODUCT_FORM,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            ProductFormScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(); toast("Barang disimpan.") },
            )
        }
        composable(
            Routes.PRODUCT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            ProductDetailScreen(
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.productForm(it)) },
                onStockIn = { nav.navigate(Routes.stockIn(it)) },
                onRemoved = { msg -> nav.popBackStack(); toast(msg) },
            )
        }
        composable(
            Routes.STOCK_IN,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            StockInScreen(
                onBack = { nav.popBackStack() },
                onDone = { msg -> nav.popBackStack(); toast(msg) },
            )
        }
    }
}
