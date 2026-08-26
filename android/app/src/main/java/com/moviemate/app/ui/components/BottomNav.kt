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
import com.moviemate.app.ui.theme.MovieMateColors
import com.moviemate.app.ui.theme.MovieMateType
import com.moviemate.app.ui.theme.Radius
import com.moviemate.app.ui.theme.Spacing

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * Bottom navigation — Design System v8 §5.
 *
 * Every item has the SAME vertical structure (icon above label, centred). The
 * active item differs only by its soft-blue pill background and blue tint — its
 * layout never changes. An earlier draft laid the active item out horizontally
 * while the others stayed vertical; that was a bug and is explicitly fixed.
 */
@Composable
fun MovieMateBottomNav(
    items: List<BottomNavItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MovieMateColors.Paper, RoundedCornerShape(Radius.pill))
            .padding(vertical = Spacing.s3, horizontal = Spacing.s2),
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
    val tint = if (selected) MovieMateColors.Blue else MovieMateColors.InkSecondary

    Column(
        modifier = Modifier
            .background(
                color = if (selected) MovieMateColors.BlueSoft else Color.Transparent,
                shape = RoundedCornerShape(Radius.pill),
            )
            .pressableCard(onClick = onClick)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(text = item.label, style = MovieMateType.navLabel, color = tint)
    }
}
