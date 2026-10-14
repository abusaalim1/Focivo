package com.example.ui.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.ViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 * v1.5.1 — a tiny, self-contained lifecycle host for Compose content shown in
 * WindowManager overlay windows (TYPE_APPLICATION_OVERLAY), where there is no
 * Activity / Fragment to provide ViewTree owners.
 *
 * Without this, ComposeView.onAttachedToWindow throws
 * IllegalStateException("ViewTreeLifecycleOwner not found") on the main thread
 * (reported crash on vivo V2403, Android 16, Focivo 1.5.0).
 *
 * Usage:
 *   val host = OverlayLifecycleHost()
 *   val view = ComposeView(context).apply {
 *       host.installOn(this)      // set the three ViewTree owners
 *       setContent { ... }        // compose content
 *   }
 *   host.show()                   // CREATED -> STARTED -> RESUMED
 *   windowManager.addView(view, params)
 *   ...
 *   host.dismiss()                // -> DESTROYED + ViewModelStore cleared
 *   windowManager.removeView(view)
 */
class OverlayLifecycleHost :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun getViewModelStore(): ViewModelStore = store

    override fun getSavedStateRegistry(): SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    /** Sets this host as all three ViewTree owners for [view]. */
    fun installOn(view: View) {
        ViewTreeLifecycleOwner.set(view, this)
        ViewTreeSavedStateRegistryOwner.set(view, this)
        ViewTreeViewModelStoreOwner.set(view, this)
    }

    /** Drives the lifecycle to RESUMED. Call BEFORE WindowManager.addView. */
    fun show() {
        savedStateRegistryController.performAttach()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** Drives the lifecycle to DESTROYED and clears the ViewModelStore. Call around removeView. */
    fun dismiss() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
