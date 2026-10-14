package com.example.ui.overlay

import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * v1.5.1 — a tiny, self-contained lifecycle host for Compose content shown in
 * WindowManager overlay windows (TYPE_APPLICATION_OVERLAY), where there is no
 * Activity / Fragment to provide ViewTree owners.
 *
 * Without this, ComposeView.onAttachedToWindow throws
 * IllegalStateException("ViewTreeLifecycleOwner not found") on the main thread
 * (reported crash on vivo V2403, Android 16, Focivo 1.5.0).
 *
 * NOTE on the ViewTree* bindings: the androidx ViewTree* helper classes
 * (ViewTreeLifecycleOwner, ViewTreeViewModelStoreOwner,
 * ViewTreeSavedStateRegistryOwner) live in the lifecycle-*-android / savedstate
 * artifacts, which are not on this module's compile classpath (AndroidX KMP
 * artifact split in lifecycle 2.8.x). They ARE present at runtime — compose-ui
 * itself needs them, and they ship in the APK — so installOn() binds them via
 * reflection instead of direct references. Every directly-referenced symbol in
 * this file is compile-safe.
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

    /**
     * Sets this host as all three ViewTree owners for [view].
     * Uses reflection for the ViewTree* helpers (see class KDoc); they are
     * guaranteed present at runtime via compose-ui's own dependency chain.
     */
    fun installOn(view: View) {
        setViewTreeOwner(
            "androidx.lifecycle.ViewTreeLifecycleOwner",
            view, this, LifecycleOwner::class.java
        )
        setViewTreeOwner(
            "androidx.lifecycle.ViewTreeViewModelStoreOwner",
            view, this, ViewModelStoreOwner::class.java
        )
        setViewTreeOwner(
            "androidx.savedstate.ViewTreeSavedStateRegistryOwner",
            view, this, SavedStateRegistryOwner::class.java
        )
    }

    private fun setViewTreeOwner(
        helperClassName: String,
        view: View,
        owner: Any,
        ownerInterface: Class<*>
    ) {
        try {
            val helper = Class.forName(helperClassName)
            helper.getMethod("set", View::class.java, ownerInterface)
                .invoke(null, view, owner)
        } catch (t: Throwable) {
            // Should never happen: these classes ship with compose-ui in the APK.
            // If it does, surface it loudly — the alternative is the original
            // "ViewTreeLifecycleOwner not found" crash on addView.
            Log.e(TAG, "Failed to bind $helperClassName", t)
            throw IllegalStateException("OverlayLifecycleHost: cannot bind $helperClassName", t)
        }
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

    companion object {
        private const val TAG = "OverlayLifecycleHost"
    }
}
