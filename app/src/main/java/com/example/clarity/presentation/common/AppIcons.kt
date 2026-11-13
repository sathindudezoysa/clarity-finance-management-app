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

    val DonutLarge: ImageVector by lazy {
        ImageVector.Builder(
            name = "DonutLarge",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 5.08f)
            verticalLineTo(2f)
            curveTo(6f, 2.5f, 2f, 6.81f, 2f, 12f)
            reflectiveCurveToRelative(4f, 9.5f, 9f, 10f)
            verticalLineToRelative(-3.08f)
            curveToRelative(-3f, -0.48f, -6f, -3.4f, -6f, -6.92f)
            reflectiveCurveToRelative(3f, -6.44f, 6f, -6.92f)
            close()
            moveTo(18.97f, 11f)
            horizontalLineTo(22f)
            curveToRelative(-0.47f, -5f, -4f, -8.53f, -9f, -9f)
            verticalLineToRelative(3.08f)
            curveTo(16f, 5.51f, 18.54f, 8f, 18.97f, 11f)
            close()
            moveTo(13f, 18.92f)
            verticalLineTo(22f)
            curveToRelative(5f, -0.47f, 8.53f, -4f, 9f, -9f)
            horizontalLineToRelative(-3.03f)
            curveToRelative(-0.43f, 3f, -2.97f, 5.49f, -5.97f, 5.92f)
            close()
        }.build()
    }

    val Flame: ImageVector by lazy {
        ImageVector.Builder(
            name = "Flame",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(13.5f, 0.67f)
            reflectiveCurveToRelative(0.74f, 2.65f, 0.74f, 4.8f)
            curveToRelative(0f, 2.06f, -1.35f, 3.73f, -3.41f, 3.73f)
            curveToRelative(-2.07f, 0f, -3.63f, -1.67f, -3.63f, -3.73f)
            lineToRelative(0.03f, -0.36f)
            curveTo(5.21f, 7.51f, 4f, 10.62f, 4f, 14f)
            curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            reflectiveCurveToRelative(8f, -3.58f, 8f, -8f)
            curveTo(20f, 8.61f, 17.41f, 3.8f, 13.5f, 0.67f)
            close()
            moveTo(11.71f, 19f)
            curveToRelative(-1.78f, 0f, -3.22f, -1.4f, -3.22f, -3.14f)
            curveToRelative(0f, -1.62f, 1.05f, -2.76f, 2.81f, -3.12f)
            curveToRelative(1.77f, -0.36f, 3.6f, -1.21f, 4.62f, -2.58f)
            curveToRelative(0.39f, 1.29f, 0.59f, 2.65f, 0.59f, 4.04f)
            curveToRelative(0f, 2.65f, -2.15f, 4.8f, -4.8f, 4.8f)
            close()
        }.build()
    }

    val Trophy: ImageVector by lazy {
        ImageVector.Builder(
            name = "Trophy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 5f)
            horizontalLineToRelative(-2f)
            verticalLineTo(3f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(1f)
            curveToRelative(0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
            curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
            verticalLineTo(19f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-3.1f)
            curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
            curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(5f, 8f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3.82f)
            curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
            close()
            moveTo(19f, 8f)
            curveToRelative(0f, 1.3f, -0.84f, 2.4f, -2f, 2.82f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            close()
        }.build()
    }

    val ShieldPlus: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldPlus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(4f, 5f)
            verticalLineToRelative(6.09f)
            curveToRelative(0f, 5.05f, 3.41f, 9.76f, 8f, 10.91f)
            curveToRelative(4.59f, -1.15f, 8f, -5.86f, 8f, -10.91f)
            verticalLineTo(5f)
            lineToRelative(-8f, -3f)
            close()
            moveTo(18f, 11.09f)
            curveToRelative(0f, 4f, -2.55f, 7.7f, -6f, 8.83f)
            curveToRelative(-3.45f, -1.13f, -6f, -4.82f, -6f, -8.83f)
            verticalLineTo(6.31f)
            lineToRelative(6f, -2.12f)
            lineToRelative(6f, 2.12f)
            verticalLineToRelative(4.78f)
            close()
            moveTo(13f, 7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(3f)
            horizontalLineTo(8f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-3f)
            verticalLineTo(7f)
            close()
        }.build()
    }

    val Target: ImageVector by lazy {
        ImageVector.Builder(
            name = "Target",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
            close()
            moveTo(12f, 6f)
            curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
            reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
            reflectiveCurveToRelative(6f, -2.69f, 6f, -6f)
            reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
            close()
            moveTo(12f, 16f)
            curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
            reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
            reflectiveCurveToRelative(4f, 1.79f, 4f, 4f)
            reflectiveCurveToRelative(-1.79f, 4f, -4f, 4f)
            close()
            moveTo(12f, 10f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
            reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
            close()
        }.build()
    }

    val Savings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Savings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19.83f, 7.5f)
            lineToRelative(-2.27f, -2.27f)
            curveToRelative(0.07f, -0.42f, 0.18f, -0.81f, 0.32f, -1.15f)
            curveToRelative(0.23f, -0.56f, 0.56f, -1.06f, 0.97f, -1.5f)
            curveToRelative(-1.42f, -0.2f, -2.81f, 0.22f, -3.85f, 1.15f)
            curveTo(13.85f, 3.26f, 12.46f, 3f, 11f, 3f)
            curveTo(6.58f, 3f, 3f, 6.58f, 3f, 11f)
            curveToRelative(0f, 2.29f, 0.97f, 4.35f, 2.5f, 5.81f)
            verticalLineTo(20f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(3f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(3f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-2.78f)
            curveToRelative(0.85f, -0.78f, 1.5f, -1.77f, 1.85f, -2.89f)
            lineTo(21f, 14f)
            verticalLineTo(9f)
            horizontalLineToRelative(-1.17f)
            verticalLineTo(7.5f)
            close()
            moveTo(14f, 9f)
            horizontalLineTo(8f)
            verticalLineTo(7f)
            horizontalLineToRelative(6f)
            verticalLineTo(9f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
            reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
            reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
            reflectiveCurveTo(17.05f, 12f, 16.5f, 12f)
            close()
        }.build()
    }

    val Schedule: ImageVector by lazy {
        ImageVector.Builder(
            name = "Schedule",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(11.99f, 2f)
            curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
            reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
            reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
            reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
            close()
            moveTo(12.5f, 7f)
            horizontalLineTo(11f)
            verticalLineToRelative(6f)
            lineToRelative(5.25f, 3.15f)
            lineToRelative(0.75f, -1.23f)
            lineToRelative(-4.5f, -2.67f)
            verticalLineTo(7f)
            close()
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "Calendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 4f)
            horizontalLineToRelative(-1f)
            verticalLineTo(2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            verticalLineTo(2f)
            horizontalLineTo(6f)
            verticalLineToRelative(2f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
            lineTo(3f, 20f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(19f, 20f)
            horizontalLineTo(5f)
            verticalLineTo(10f)
            horizontalLineToRelative(14f)
            verticalLineTo(20f)
            close()
            moveTo(19f, 8f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineToRelative(14f)
            verticalLineTo(8f)
            close()
            moveTo(12f, 13f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(-5f)
            verticalLineTo(13f)
            close()
        }.build()
    }

    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "TrendingUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 6f)
            lineToRelative(2.29f, 2.29f)
            lineToRelative(-4.88f, 4.88f)
            lineToRelative(-4f, -4f)
            lineTo(2f, 16.59f)
            lineTo(3.41f, 18f)
            lineToRelative(6f, -6f)
            lineToRelative(4f, 4f)
            lineToRelative(6.3f, -6.29f)
            lineTo(22f, 12f)
            verticalLineTo(6f)
            horizontalLineTo(16f)
            close()
        }.build()
    }

    val Psychology: ImageVector by lazy {
        ImageVector.Builder(
            name = "Psychology",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 8.57f)
            curveToRelative(-0.79f, 0f, -1.43f, 0.64f, -1.43f, 1.43f)
            reflectiveCurveToRelative(0.64f, 1.43f, 1.43f, 1.43f)
            reflectiveCurveToRelative(1.43f, -0.64f, 1.43f, -1.43f)
            reflectiveCurveTo(13.79f, 8.57f, 13f, 8.57f)
            close()
            moveTo(13f, 3f)
            curveTo(9.25f, 3f, 6.2f, 5.94f, 6.02f, 9.64f)
            lineTo(4.1f, 12.2f)
            curveTo(3.85f, 12.53f, 4.09f, 13f, 4.5f, 13f)
            horizontalLineTo(6f)
            verticalLineToRelative(3f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(1f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(-4.68f)
            curveToRelative(2.36f, -1.12f, 4f, -3.53f, 4f, -6.32f)
            curveTo(20f, 6.13f, 16.87f, 3f, 13f, 3f)
            close()
            moveTo(16f, 10f)
            curveToRelative(0f, 1.66f, -1.34f, 3f, -3f, 3f)
            reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
            reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveTo(16f, 8.34f, 16f, 10f)
            close()
        }.build()
    }

    val Lightbulb: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lightbulb",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 21f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(4f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-1f)
            horizontalLineTo(9f)
            verticalLineTo(21f)
            close()
            moveTo(12f, 2f)
            curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
            curveToRelative(0f, 2.38f, 1.19f, 4.47f, 3f, 5.74f)
            verticalLineTo(17f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(6f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-2.26f)
            curveToRelative(1.81f, -1.27f, 3f, -3.36f, 3f, -5.74f)
            curveTo(19f, 5.14f, 15.86f, 2f, 12f, 2f)
            close()
        }.build()
    }

    val Flag: ImageVector by lazy {
        ImageVector.Builder(
            name = "Flag",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(14.4f, 6f)
            lineTo(14f, 4f)
            horizontalLineTo(5f)
            verticalLineToRelative(17f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(5.6f)
            lineToRelative(0.4f, 2f)
            horizontalLineToRelative(7f)
            verticalLineTo(6f)
            horizontalLineTo(14.4f)
            close()
        }.build()
    }

    val AccountBalance: ImageVector by lazy {
        ImageVector.Builder(
            name = "AccountBalance",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 10f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-7f)
            horizontalLineTo(4f)
            close()
            moveTo(10f, 10f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(2f, 22f)
            horizontalLineToRelative(19f)
            verticalLineToRelative(-3f)
            horizontalLineTo(2f)
            verticalLineToRelative(3f)
            close()
            moveTo(16f, 10f)
            verticalLineToRelative(7f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-3f)
            close()
            moveTo(11.5f, 1f)
            lineTo(2f, 6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(19f)
            verticalLineTo(6f)
            lineTo(11.5f, 1f)
            close()
        }.build()
    }

    val SwapHoriz: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwapHoriz",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(6.99f, 11f)
            lineTo(3f, 15f)
            lineToRelative(3.99f, 4f)
            verticalLineToRelative(-3f)
            horizontalLineTo(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(6.99f)
            verticalLineToRelative(-3f)
            close()
            moveTo(21f, 9f)
            lineToRelative(-3.99f, -4f)
            verticalLineToRelative(3f)
            horizontalLineTo(10f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(7.01f)
            verticalLineToRelative(3f)
            lineTo(21f, 9f)
            close()
        }.build()
    }

    val Sync: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sync",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            verticalLineTo(1f)
            lineTo(8f, 5f)
            lineToRelative(4f, 4f)
            verticalLineTo(6f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
            curveToRelative(0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
            lineToRelative(1.46f, 1.46f)
            curveTo(19.54f, 15.03f, 20f, 13.57f, 20f, 12f)
            curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f)
            close()
            moveTo(12f, 18f)
            curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
            curveToRelative(0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
            lineTo(5.24f, 7.74f)
            curveTo(4.46f, 8.97f, 4f, 10.43f, 4f, 12f)
            curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            close()
        }.build()
    }

    val Notes: ImageVector by lazy {
        ImageVector.Builder(
            name = "Notes",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 18f)
            horizontalLineToRelative(12f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            close()
            moveTo(3f, 6f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(18f)
            verticalLineTo(6f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 13f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
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
