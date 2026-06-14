package ad.skip.util

import ad.skip.service.MyAccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

typealias Lambda = () -> Unit
typealias Lambda1<A> = (A) -> Unit
typealias Lambda2<A, B> = (A, B) -> Unit
typealias Lambda3<A, B, C> = (A, B, C) -> Unit
typealias Lambda4<A, B, C, D> = (A, B, C, D) -> Unit

fun String.truncate(maxLength: Int) : String {
    return if (this.length <= maxLength)
        this
    else
        this.take(maxLength) + "..."
}

fun Int.hasFlag(flag: Int): Boolean {
    return this and flag != 0
}

fun Bitmap.toLosslessWebpByteArray(): ByteArray? =
    ByteArrayOutputStream().use { stream ->
        if(compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)) {
            stream.toByteArray()
        } else
            null
    }

object Util {
    fun formatTime(timestamp: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, MyAccessibilityService::class.java)
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServiceList = accessibilityManager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()

        if (enabledServiceList.any { serviceInfo ->
                val enabledService = serviceInfo.resolveInfo.serviceInfo
                ComponentName(enabledService.packageName, enabledService.name) == expectedComponent
            }
        ) {
            return true
        }

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedFlatName = expectedComponent.flattenToString()
        val expectedShortName = expectedComponent.flattenToShortString()
        val splitter = TextUtils.SimpleStringSplitter(':').apply {
            setString(enabledServices)
        }
        while (splitter.hasNext()) {
            val enabledServiceName = splitter.next()
            val enabledComponent = ComponentName.unflattenFromString(enabledServiceName)
            if (
                enabledComponent == expectedComponent ||
                enabledServiceName.equals(expectedFlatName, ignoreCase = true) ||
                enabledServiceName.equals(expectedShortName, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    fun getAppName(ctx: Context, packageName: String): String {
        return PackagePresentation.appLabel(ctx, packageName)
    }
}
