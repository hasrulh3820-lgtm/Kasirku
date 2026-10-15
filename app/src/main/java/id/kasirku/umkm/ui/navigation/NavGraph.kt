package id.kasirku.umkm.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.kasirku.umkm.ui.home.HomeScreen
import id.kasirku.umkm.ui.product.ProductDetailScreen
import id.kasirku.umkm.ui.product.ProductFormScreen
import id.kasirku.umkm.ui.product.ProductListScreen
import id.kasirku.umkm.ui.product.StockInScreen

object Routes {
    const val HOME = "home"
    const val PRODUCTS = "products"
    const val PRODUCT_FORM = "product_form?id={id}"
    const val PRODUCT_DETAIL = "product/{id}"
    const val STOCK_IN = "stock_in/{id}"

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
            )
        }
        composable(Routes.PRODUCTS) {
            ProductListScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(Routes.productForm()) },
                onOpen = { nav.navigate(Routes.productDetail(it)) },
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
