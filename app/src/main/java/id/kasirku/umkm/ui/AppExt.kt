package id.kasirku.umkm.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import id.kasirku.umkm.KasirKuApp

/** Dipakai di dalam `initializer { }` ViewModel untuk mengambil AppContainer. */
fun CreationExtras.kasirApp(): KasirKuApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KasirKuApp
