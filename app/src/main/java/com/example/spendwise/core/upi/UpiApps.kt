package com.example.spendwise.core.upi

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** NPCI's dedicated UPI launch action. */
const val ACTION_UPI_PAY = "android.intent.action.UPI_PAY"

/** An installed, UPI-capable app: what to call it and how to launch it. */
data class UpiApp(
    val packageName: String,
    val label: String,
    /** The intent shape this app registered for the `upi://` scheme. */
    val action: String,
)

/**
 * UPI-capable apps installed on the device — the "Default UPI app" picker in
 * Settings and the scanner's direct-launch path both resolve through this.
 * NPCI defines two launch shapes — the dedicated UPI_PAY action and VIEW +
 * upi:// — and current GPay/PhonePe/Paytm builds commonly register only ONE
 * of them, so both must be probed and merged (querying just UPI_PAY finds
 * nothing on many devices).
 */
fun listUpiApps(packageManager: PackageManager): List<UpiApp> {
    val byPackage = LinkedHashMap<String, UpiApp>()
    for (action in listOf(ACTION_UPI_PAY, Intent.ACTION_VIEW)) {
        val probe = Intent(action).setData(Uri.parse("upi://pay"))
        for (info in packageManager.queryIntentActivities(probe, 0)) {
            byPackage.getOrPut(info.activityInfo.packageName) {
                UpiApp(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    action = action,
                )
            }
        }
    }
    return byPackage.values.sortedBy { it.label.lowercase() }
}