package ad.skip.ui.widgets

import ad.skip.util.Lambda
import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp

@SuppressLint("ComposableNaming") @Composable
fun Str(strId: Int): String {
    return stringResource(id = strId)
}

@Composable
fun ResIcon(
    iconId: Int,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    color: Color = LocalContentColor.current,
    onClick: Lambda? = null
) {
    var m = modifier

    if (size != null) { m = m.size(size) }
    if (onClick != null) { m = m.clickable(onClick = onClick) }

    Icon(
        modifier = m,
        tint = color,
        painter = painterResource(id = iconId),
        contentDescription = ""
    )
}

@Composable
fun ResImage(
    resId: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Image(
        modifier = modifier,
        colorFilter = ColorFilter.tint(color),
        imageVector = ImageVector.vectorResource(id = resId),
        contentDescription = ""
    )
}
