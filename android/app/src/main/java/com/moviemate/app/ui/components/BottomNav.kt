package com.moviemate.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moviemate.app.ui.theme.MovieMateTheme
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Space

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Bottom navigation.
 *
 * All three items share ONE vertical structure: icon above label, centred. The
 * active item differs only by `surfaceAccent` behind it and `textAccent` on it —
 * its layout never changes. An early v8 draft laid the active item out
 * horizontally while the others stayed vertical; that was a bug, and this
 * structure is what prevents it coming back.
 */
@Composable
fun MovieMateBottomNav(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MovieMateTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceRaised, RoundedCornerShape(Radius.pill))
            .padding(vertical = Space.inline, horizontal = Space.inline),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavItem(
                item = item,
                selected = item.route == selectedRoute,
                onClick = { onSelect(item.route) },
            )
        }
    }
}

@Composable
private fun NavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MovieMateTheme.colors
    val tint = if (selected) colors.textAccent else colors.textSecondary

    Column(
        modifier = Modifier
            .background(
                color = if (selected) colors.surfaceAccent else Color.Transparent,
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressableCard(onClick = onClick)
            // 48dp minimum touch target (a11y.minTouchTarget).
            .padding(horizontal = Space.stackTight, vertical = Space.stackTight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.inlineTight),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Text(text = item.label, style = MovieMateType.navLabel, color = tint)
    }
}
