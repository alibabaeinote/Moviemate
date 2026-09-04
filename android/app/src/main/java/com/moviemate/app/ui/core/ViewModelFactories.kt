package com.moviemate.app.ui.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Builds a [ViewModelProvider.Factory] for a ViewModel with constructor
 * parameters.
 *
 * The stock `viewModel()` can only call a no-arg constructor, which is what
 * pushes people into either a DI framework or into ViewModels that reach for
 * singletons themselves. This keeps the constructor honest:
 *
 * ```
 * val vm: MatchViewModel = viewModel(factory = factoryOf { MatchViewModel(graph.pairRepository) })
 * ```
 */
inline fun <reified VM : ViewModel> factoryOf(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
