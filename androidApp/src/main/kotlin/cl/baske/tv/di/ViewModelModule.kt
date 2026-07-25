package cl.baske.tv.di

import cl.baske.tv.ui.connect.ConnectViewModel
import cl.baske.tv.ui.detail.DetailViewModel
import cl.baske.tv.ui.home.HomeViewModel
import cl.baske.tv.ui.library.LibraryViewModel
import cl.baske.tv.ui.player.PlayerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** ViewModels compartidos, registrados con el DSL multiplataforma de Koin. */
val viewModelModule = module {
    viewModelOf(::ConnectViewModel)
    viewModelOf(::HomeViewModel)
    // El id llega como parámetro (parametersOf) en tiempo de navegación.
    viewModel { PlayerViewModel(itemId = get(), api = get(), sessionStore = get()) }
    viewModel { DetailViewModel(itemId = get(), api = get(), sessionStore = get()) }
    viewModel { LibraryViewModel(libraryId = get(), api = get(), sessionStore = get()) }
}
