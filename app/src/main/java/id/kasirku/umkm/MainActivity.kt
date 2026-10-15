package id.kasirku.umkm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.kasirku.umkm.ui.navigation.KasirKuNavHost
import id.kasirku.umkm.ui.theme.KasirKuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KasirKuTheme {
                KasirKuNavHost()
            }
        }
    }
}
