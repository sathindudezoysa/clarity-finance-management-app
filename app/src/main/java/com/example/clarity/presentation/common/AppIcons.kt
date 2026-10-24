package com.example.clarity.presentation.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val Visibility: ImageVector by lazy {
        ImageVector.Builder(
            name = "Visibility",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 17f)
            curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f)
            close()
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }.build()
    }

    val VisibilityOff: ImageVector by lazy {
        ImageVector.Builder(
            name = "VisibilityOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(12f, 6.5f)
            curveTo(15.79f, 6.5f, 19.17f, 8.63f, 20.82f, 12f)
            curveTo(19.17f, 15.37f, 15.79f, 17.5f, 12f, 17.5f)
            curveTo(8.21f, 17.5f, 4.83f, 15.37f, 3.18f, 12f)
            curveTo(4.83f, 8.63f, 8.21f, 6.5f, 12f, 6.5f)
            close()
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f)
            close()
            moveTo(2.81f, 2.81f)
            lineTo(1.39f, 4.22f)
            lineTo(20.19f, 23.02f)
            lineTo(21.61f, 21.61f)
            lineTo(2.81f, 2.81f)
            close()
        }.build()
    }

    val Wallet: ImageVector by lazy {
        ImageVector.Builder(
            name = "Wallet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(21f, 7.28f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(16.72f)
            curveTo(21.59f, 16.37f, 22f, 15.74f, 22f, 15f)
            verticalLineTo(9f)
            curveTo(22f, 8.26f, 21.59f, 7.63f, 21f, 7.28f)
            close()
            moveTo(20f, 9f)
            verticalLineTo(15f)
            horizontalLineTo(13f)
            verticalLineTo(9f)
            horizontalLineTo(20f)
            close()
            moveTo(5f, 19f)
            verticalLineTo(5f)
            horizontalLineTo(19f)
            verticalLineTo(7f)
            horizontalLineTo(13f)
            curveTo(11.9f, 7f, 11f, 7.9f, 11f, 9f)
            verticalLineTo(15f)
            curveTo(11f, 16.1f, 11.9f, 17f, 13f, 17f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(16f, 10.5f)
            curveTo(15.17f, 10.5f, 14.5f, 11.17f, 14.5f, 12f)
            curveTo(14.5f, 12.83f, 15.17f, 13.5f, 16f, 13.5f)
            curveTo(16.83f, 13.5f, 17.5f, 12.83f, 17.5f, 12f)
            curveTo(17.5f, 11.17f, 16.83f, 10.5f, 16f, 10.5f)
            close()
        }.build()
    }

    val ArrowDownward: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDownward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 12f)
            lineTo(18.59f, 10.59f)
            lineTo(13f, 16.17f)
            lineTo(13f, 4f)
            lineTo(11f, 4f)
            lineTo(11f, 16.17f)
            lineTo(5.42f, 10.58f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            lineTo(20f, 12f)
            close()
        }.build()
    }

    val ArrowUpward: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUpward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 12f)
            lineTo(5.41f, 13.41f)
            lineTo(11f, 7.83f)
            lineTo(11f, 20f)
            lineTo(13f, 20f)
            lineTo(13f, 7.83f)
            lineTo(18.58f, 13.42f)
            lineTo(20f, 12f)
            lineTo(12f, 4f)
            lineTo(4f, 12f)
            close()
        }.build()
    }

    val Transactions: ImageVector by lazy {
        ImageVector.Builder(
            name = "Transactions",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6.99f, 11f)
            lineTo(3f, 15f)
            lineTo(6.99f, 19f)
            lineTo(6.99f, 16f)
            lineTo(14f, 16f)
            lineTo(14f, 14f)
            lineTo(6.99f, 14f)
            lineTo(6.99f, 11f)
            close()
            moveTo(21f, 9f)
            lineTo(17.01f, 5f)
            lineTo(17.01f, 8f)
            lineTo(10f, 8f)
            lineTo(10f, 10f)
            lineTo(17.01f, 10f)
            lineTo(17.01f, 13f)
            lineTo(21f, 9f)
            close()
        }.build()
    }

    val Budgets: ImageVector by lazy {
        ImageVector.Builder(
            name = "Budgets",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 9.2f)
            horizontalLineTo(8f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(10.6f, 5f)
            horizontalLineTo(13.4f)
            verticalLineTo(19f)
            horizontalLineTo(10.6f)
            close()
            moveTo(16.2f, 13f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(16.2f)
            close()
        }.build()
    }

    // Fixed Google brand colors (official 4-color "G" mark).
    val Google: ImageVector by lazy {
        ImageVector.Builder(
            name = "Google",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 48f,
            viewportHeight = 48f
        ).path(fill = SolidColor(Color(0xFF4285F4))) {
            moveTo(45.12f, 24.5f)
            curveToRelative(0f, -1.56f, -0.14f, -3.06f, -0.4f, -4.5f)
            horizontalLineTo(24f)
            verticalLineToRelative(8.51f)
            horizontalLineToRelative(11.84f)
            curveToRelative(-0.51f, 2.75f, -2.06f, 5.08f, -4.39f, 6.64f)
            verticalLineToRelative(5.52f)
            horizontalLineToRelative(7.11f)
            curveToRelative(4.16f, -3.83f, 6.56f, -9.47f, 6.56f, -16.17f)
            close()
        }.path(fill = SolidColor(Color(0xFF34A853))) {
            moveTo(24f, 46f)
            curveToRelative(5.94f, 0f, 10.92f, -1.97f, 14.56f, -5.33f)
            lineToRelative(-7.11f, -5.52f)
            curveToRelative(-1.97f, 1.32f, -4.49f, 2.1f, -7.45f, 2.1f)
            curveToRelative(-5.73f, 0f, -10.58f, -3.87f, -12.31f, -9.07f)
            horizontalLineTo(4.34f)
            verticalLineToRelative(5.7f)
            curveTo(7.96f, 41.07f, 15.4f, 46f, 24f, 46f)
            close()
        }.path(fill = SolidColor(Color(0xFFFBBC05))) {
            moveTo(11.69f, 28.18f)
            curveTo(11.25f, 26.86f, 11f, 25.45f, 11f, 24f)
            reflectiveCurveToRelative(0.25f, -2.86f, 0.69f, -4.18f)
            verticalLineToRelative(-5.7f)
            horizontalLineTo(4.34f)
            curveTo(2.85f, 17.09f, 2f, 20.45f, 2f, 24f)
            curveToRelative(0f, 3.55f, 0.85f, 6.91f, 2.34f, 9.88f)
            lineToRelative(7.35f, -5.7f)
            close()
        }.path(fill = SolidColor(Color(0xFFEA4335))) {
            moveTo(24f, 10.75f)
            curveToRelative(3.23f, 0f, 6.13f, 1.11f, 8.41f, 3.29f)
            lineToRelative(6.31f, -6.31f)
            curveTo(34.91f, 4.18f, 29.93f, 2f, 24f, 2f)
            curveTo(15.4f, 2f, 7.96f, 6.93f, 4.34f, 14.12f)
            lineToRelative(7.35f, 5.7f)
            curveToRelative(1.73f, -5.2f, 6.58f, -9.07f, 12.31f, -9.07f)
            close()
        }.build()
    }
}
