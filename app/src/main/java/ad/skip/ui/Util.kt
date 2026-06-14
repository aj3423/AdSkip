package ad.skip.ui

import ad.skip.util.PackagePresentation
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext


@Composable
fun rememberPackageIconBitmap(packageName: String?): Bitmap {
    val context = LocalContext.current.applicationContext
    return remember(packageName) {
        PackagePresentation.iconBitmap(context, packageName)
    }
}

object Util {
}
