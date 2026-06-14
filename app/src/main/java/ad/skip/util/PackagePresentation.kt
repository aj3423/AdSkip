package ad.skip.util

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

object PackagePresentation {
    private const val DEFAULT_ICON_SIZE_PX = 24

    fun iconBitmap(
        ctx: Context,
        packageName: String?,
        width: Int = DEFAULT_ICON_SIZE_PX,
        height: Int = DEFAULT_ICON_SIZE_PX
    ): Bitmap {
        val packageManager = ctx.packageManager
        val drawable = packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { findLauncherIcon(ctx, it) ?: findPackageManagerIcon(ctx, it) }
            ?: ctx.applicationInfo.loadIcon(packageManager) // fallback icon

        return drawable.toBitmap(width = width, height = height)
    }

    fun appLabel(ctx: Context, packageName: String): String {
        val packageManager = ctx.packageManager
        val activityInfo = findLauncherActivity(ctx, packageName)
        if (activityInfo != null) {
            return packageManager.getUserBadgedLabel(
                activityInfo.label,
                activityInfo.user
            ).toString()
        }

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun findLauncherIcon(ctx: Context, packageName: String): Drawable? {
        val activityInfo = findLauncherActivity(ctx, packageName) ?: return null
        return activityInfo.getBadgedIcon(0)
    }

    private fun findLauncherActivity(
        context: Context,
        packageName: String
    ): LauncherActivityInfo? {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return null
        val profiles = runCatching { launcherApps.profiles }.getOrNull().orEmpty()
        return profiles.firstNotNullOfOrNull { user ->
            runCatching {
                launcherApps.getActivityList(packageName, user)
                    .firstOrNull()
            }.getOrNull()
        }
    }

    private fun findPackageManagerIcon(context: Context, packageName: String): Drawable? =
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
}
