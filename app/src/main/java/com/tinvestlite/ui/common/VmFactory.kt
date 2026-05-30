package com.tinvestlite.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Concise factory for the manual-DI ViewModels used across screens. */
inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM) =
    viewModelFactory { initializer { create() } }
