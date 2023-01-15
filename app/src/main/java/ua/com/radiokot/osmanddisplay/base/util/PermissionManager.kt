package ua.com.radiokot.osmanddisplay.base.util

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Handles specified Android runtime permission.
 */
@Deprecated("Replace it with an activity result contract")
class PermissionManager(
    private val permissions: Array<String>,
    private val requestCode: Int
) {
    constructor(
        permission: String,
        requestCode: Int,
    ) : this(arrayOf(permission), requestCode)

    private var grantedCallback: (() -> Unit)? = null
    private var deniedCallback: (() -> Unit)? = null

    /**
     * Checks for the permission, requests it if needed
     *
     * @param action action to invoke if permission is granted
     * @param deniedAction action to invoke if permission is denied
     */
    fun check(activity: Activity, action: () -> Unit, deniedAction: () -> Unit) {
        this.grantedCallback = action
        this.deniedCallback = deniedAction
        check(activity)
    }

    /**
     * Checks for the permission, requests it if needed
     *
     * @param action action to invoke if permission is granted
     */
    fun check(activity: Activity, action: () -> Unit) {
        this.grantedCallback = action
        check(activity)
    }

    fun check(activity: Activity) {
        val deniedPermissions = permissions
            .filter {
                ContextCompat.checkSelfPermission(
                    activity,
                    it
                ) == PackageManager.PERMISSION_DENIED
            }

        if (deniedPermissions.isEmpty()) {
            grantedCallback?.invoke()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                deniedPermissions.toTypedArray(),
                requestCode
            )
        }
    }

    /**
     * Checks for the permission, requests it if needed
     *
     * @param action action to invoke if permission is granted
     * @param deniedAction action to invoke if permission is denied
     */
    fun check(fragment: Fragment, action: () -> Unit, deniedAction: () -> Unit) {
        this.grantedCallback = action
        this.deniedCallback = deniedAction
        check(fragment)
    }

    /**
     * Checks for the permission, requests it if needed
     *
     * @param action action to invoke if permission is granted
     */
    fun check(fragment: Fragment, action: () -> Unit) {
        this.grantedCallback = action
        check(fragment)
    }

    private fun check(fragment: Fragment) {
        val deniedPermissions = permissions
            .filter {
                ContextCompat.checkSelfPermission(
                    fragment.requireContext(),
                    it
                ) == PackageManager.PERMISSION_DENIED
            }

        if (deniedPermissions.isEmpty()) {
            grantedCallback?.invoke()
        } else {
            fragment.requestPermissions(
                deniedPermissions.toTypedArray(),
                requestCode
            )
        }
    }

    /**
     * Handles permission grant result,
     * invokes corresponding action passed to the [check] method
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == this.requestCode) {
            if (arePermissionsGranted(grantResults)) {
                grantedCallback?.invoke()
            } else {
                deniedCallback?.invoke()
            }
        }
    }

    private fun arePermissionsGranted(grantResults: IntArray): Boolean {
        return grantResults.isNotEmpty()
                && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }
}