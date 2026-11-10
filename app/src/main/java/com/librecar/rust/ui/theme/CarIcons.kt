package com.librecar.rust.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CarIcons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private val stroke = SolidColor(Color.White)

    val Navigation: ImageVector by lazy {
        icon("Navigation") {
            path(fill = stroke) {
                moveTo(12f, 2f); lineTo(4.5f, 20.29f); lineTo(5.21f, 21f); lineTo(12f, 18f)
                lineTo(18.79f, 21f); lineTo(19.5f, 20.29f); close()
            }
        }
    }

    val Music: ImageVector by lazy {
        icon("Music") {
            path(fill = stroke) {
                moveTo(12f, 3f); verticalLineTo(13.55f)
                curveTo(11.41f, 13.21f, 10.73f, 13f, 10f, 13f)
                curveTo(7.79f, 13f, 6f, 14.79f, 6f, 17f)
                reflectiveCurveTo(7.79f, 21f, 10f, 21f)
                reflectiveCurveTo(14f, 19.21f, 14f, 17f)
                verticalLineTo(7f); horizontalLineTo(18f); verticalLineTo(3f); close()
            }
        }
    }

    val Play: ImageVector by lazy {
        icon("Play") {
            path(fill = stroke) { moveTo(8f, 5f); verticalLineTo(19f); lineTo(19f, 12f); close() }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            path(fill = stroke) {
                moveTo(6f, 19f); horizontalLineTo(10f); verticalLineTo(5f); horizontalLineTo(6f); close()
                moveTo(14f, 5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(5f); close()
            }
        }
    }

    val SkipNext: ImageVector by lazy {
        icon("SkipNext") {
            path(fill = stroke) {
                moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
                moveTo(16f, 6f); verticalLineTo(18f); horizontalLineTo(18f); verticalLineTo(6f); close()
            }
        }
    }

    val SkipPrev: ImageVector by lazy {
        icon("SkipPrev") {
            path(fill = stroke) {
                moveTo(6f, 6f); horizontalLineTo(8f); verticalLineTo(18f); horizontalLineTo(6f); close()
                moveTo(9.5f, 12f); lineTo(18f, 18f); verticalLineTo(6f); close()
            }
        }
    }

    val YouTube: ImageVector by lazy {
        icon("YouTube") {
            path(fill = stroke) {
                moveTo(21.5f, 7.2f)
                curveTo(21.3f, 6.3f, 20.6f, 5.6f, 19.7f, 5.4f)
                curveTo(18f, 5f, 12f, 5f, 12f, 5f)
                reflectiveCurveTo(6f, 5f, 4.3f, 5.4f)
                curveTo(3.4f, 5.6f, 2.7f, 6.3f, 2.5f, 7.2f)
                curveTo(2f, 9f, 2f, 12f, 2f, 12f)
                reflectiveCurveTo(2f, 15f, 2.5f, 16.8f)
                curveTo(2.7f, 17.7f, 3.4f, 18.4f, 4.3f, 18.6f)
                curveTo(6f, 19f, 12f, 19f, 12f, 19f)
                reflectiveCurveTo(18f, 19f, 19.7f, 18.6f)
                curveTo(20.6f, 18.4f, 21.3f, 17.7f, 21.5f, 16.8f)
                curveTo(22f, 15f, 22f, 12f, 22f, 12f)
                reflectiveCurveTo(22f, 9f, 21.5f, 7.2f); close()
                moveTo(10f, 15f); verticalLineTo(9f); lineTo(15.5f, 12f); close()
            }
        }
    }

    val GamePad: ImageVector by lazy {
        icon("GamePad") {
            path(fill = stroke) {
                moveTo(21f, 6f); horizontalLineTo(3f)
                curveTo(1.9f, 6f, 1f, 6.9f, 1f, 8f); verticalLineTo(16f)
                curveTo(1f, 17.1f, 1.9f, 18f, 3f, 18f); horizontalLineTo(21f)
                curveTo(22.1f, 18f, 23f, 17.1f, 23f, 16f); verticalLineTo(8f)
                curveTo(23f, 6.9f, 22.1f, 6f, 21f, 6f); close()
                moveTo(11f, 13f); horizontalLineTo(8f); verticalLineTo(16f); horizontalLineTo(6f)
                verticalLineTo(13f); horizontalLineTo(3f); verticalLineTo(11f); horizontalLineTo(6f)
                verticalLineTo(8f); horizontalLineTo(8f); verticalLineTo(11f); horizontalLineTo(11f); close()
                moveTo(15.5f, 15f); curveTo(14.67f, 15f, 14f, 14.33f, 14f, 13.5f)
                reflectiveCurveTo(14.67f, 12f, 15.5f, 12f); reflectiveCurveTo(17f, 12.67f, 17f, 13.5f)
                reflectiveCurveTo(16.33f, 15f, 15.5f, 15f); close()
                moveTo(19.5f, 12f); curveTo(18.67f, 12f, 18f, 11.33f, 18f, 10.5f)
                reflectiveCurveTo(18.67f, 9f, 19.5f, 9f); reflectiveCurveTo(21f, 9.67f, 21f, 10.5f)
                reflectiveCurveTo(20.33f, 12f, 19.5f, 12f); close()
            }
        }
    }

    val Mic: ImageVector by lazy {
        icon("Mic") {
            path(fill = stroke) {
                moveTo(12f, 14f); curveTo(13.66f, 14f, 15f, 12.66f, 15f, 11f)
                verticalLineTo(5f); curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f); verticalLineTo(11f)
                curveTo(9f, 12.66f, 10.34f, 14f, 12f, 14f); close()
                moveTo(17.3f, 11f); curveTo(17.3f, 14f, 14.76f, 16.1f, 12f, 16.1f)
                reflectiveCurveTo(6.7f, 14f, 6.7f, 11f); horizontalLineTo(5f)
                curveTo(5f, 14.41f, 7.72f, 17.23f, 11f, 17.72f); verticalLineTo(21f)
                horizontalLineTo(13f); verticalLineTo(17.72f)
                curveTo(16.28f, 17.24f, 19f, 14.42f, 19f, 11f); close()
            }
        }
    }

    val LayoutGrid: ImageVector by lazy {
        icon("LayoutGrid") {
            path(fill = stroke) {
                moveTo(3f, 3f); horizontalLineTo(11f); verticalLineTo(11f); horizontalLineTo(3f); close()
                moveTo(13f, 3f); horizontalLineTo(21f); verticalLineTo(11f); horizontalLineTo(13f); close()
                moveTo(3f, 13f); horizontalLineTo(11f); verticalLineTo(21f); horizontalLineTo(3f); close()
                moveTo(13f, 13f); horizontalLineTo(21f); verticalLineTo(21f); horizontalLineTo(13f); close()
            }
        }
    }

    val EcoLeaf: ImageVector by lazy {
        icon("EcoLeaf") {
            path(fill = stroke) {
                moveTo(17f, 8f); curveTo(8f, 10f, 5.9f, 16.17f, 3.82f, 21.34f)
                lineTo(5.71f, 22f); lineTo(6.66f, 19.7f)
                curveTo(7.14f, 19.87f, 7.64f, 20f, 8f, 20f)
                curveTo(19f, 20f, 22f, 3f, 22f, 3f)
                curveTo(21f, 5f, 14f, 5.25f, 9f, 6.25f)
                curveTo(4f, 7.25f, 2f, 11.5f, 2f, 13.5f)
                curveTo(2f, 15.5f, 3.75f, 17.25f, 3.75f, 17.25f)
                curveTo(7f, 8f, 17f, 8f, 17f, 8f); close()
            }
        }
    }

    val FuelPump: ImageVector by lazy {
        icon("FuelPump") {
            path(fill = stroke) {
                moveTo(19.77f, 7.23f); lineTo(19.78f, 7.22f); lineTo(16.06f, 3.5f)
                lineTo(15f, 4.56f); lineTo(17.11f, 6.67f)
                curveTo(16.17f, 7.03f, 15.5f, 7.93f, 15.5f, 9f)
                curveTo(15.5f, 10.38f, 16.62f, 11.5f, 18f, 11.5f)
                curveTo(18.36f, 11.5f, 18.69f, 11.42f, 19f, 11.29f)
                verticalLineTo(18.5f); curveTo(19f, 19.05f, 18.55f, 19.5f, 18f, 19.5f)
                reflectiveCurveTo(17f, 19.05f, 17f, 18.5f); verticalLineTo(14f)
                curveTo(17f, 12.9f, 16.1f, 12f, 15f, 12f); horizontalLineTo(14f); verticalLineTo(5f)
                curveTo(14f, 3.9f, 13.1f, 3f, 12f, 3f); horizontalLineTo(6f)
                curveTo(4.9f, 3f, 4f, 3.9f, 4f, 5f); verticalLineTo(21f); horizontalLineTo(14f)
                verticalLineTo(13.5f); horizontalLineTo(15.5f); verticalLineTo(18.5f)
                curveTo(15.5f, 19.88f, 16.62f, 21f, 18f, 21f)
                reflectiveCurveTo(20.5f, 19.88f, 20.5f, 18.5f); verticalLineTo(9f)
                curveTo(20.5f, 8.31f, 20.22f, 7.68f, 19.77f, 7.23f); close()
                moveTo(12f, 10f); horizontalLineTo(6f); verticalLineTo(5f); horizontalLineTo(12f); close()
            }
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            path(fill = stroke) {
                moveTo(19f, 4f); horizontalLineTo(18f); verticalLineTo(2f); horizontalLineTo(16f)
                verticalLineTo(4f); horizontalLineTo(8f); verticalLineTo(2f); horizontalLineTo(6f)
                verticalLineTo(4f); horizontalLineTo(5f); curveTo(3.89f, 4f, 3f, 4.9f, 3f, 6f)
                verticalLineTo(20f); curveTo(3f, 21.1f, 3.89f, 22f, 5f, 22f); horizontalLineTo(19f)
                curveTo(20.1f, 22f, 21f, 21.1f, 21f, 20f); verticalLineTo(6f)
                curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f); close()
                moveTo(19f, 20f); horizontalLineTo(5f); verticalLineTo(9f); horizontalLineTo(19f); close()
            }
        }
    }

    val WeatherSun: ImageVector by lazy {
        icon("WeatherSun") {
            path(fill = stroke) {
                moveTo(12f, 7f); curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
                reflectiveCurveTo(9.24f, 17f, 12f, 17f); reflectiveCurveTo(17f, 14.76f, 17f, 12f)
                reflectiveCurveTo(14.76f, 7f, 12f, 7f); close()
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 2f); verticalLineTo(4f)
                moveTo(12f, 20f); verticalLineTo(22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); horizontalLineTo(4f)
                moveTo(20f, 12f); horizontalLineTo(22f)
                moveTo(4.93f, 19.07f); lineTo(6.34f, 17.66f)
                moveTo(17.66f, 6.34f); lineTo(19.07f, 4.93f)
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            path(fill = stroke) {
                moveTo(15.5f, 14f); horizontalLineTo(14.71f); lineTo(14.43f, 13.73f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f); reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f); curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
                lineTo(14f, 14.71f); verticalLineTo(15.5f); lineTo(19f, 20.49f); lineTo(20.49f, 19f); close()
                moveTo(9.5f, 14f); curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f); reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f); close()
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            path(fill = stroke) {
                moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f)
                lineTo(6.41f, 5f); lineTo(5f, 6.41f); lineTo(10.59f, 12f)
                lineTo(5f, 17.59f); lineTo(6.41f, 19f); lineTo(12f, 13.41f)
                lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f); close()
            }
        }
    }

    val SettingsGear: ImageVector by lazy {
        icon("SettingsGear") {
            path(fill = stroke) {
                moveTo(19.14f, 12.94f)
                curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
                reflectiveCurveTo(19.18f, 11.36f, 19.13f, 11.06f)
                lineTo(21.16f, 9.48f)
                curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
                lineTo(19.36f, 5.55f)
                curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
                lineTo(16.38f, 6.29f)
                curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
                lineTo(14.4f, 2.81f)
                curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
                horizontalLineTo(10.08f)
                curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
                lineTo(9.25f, 5.35f)
                curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                lineTo(5.24f, 5.33f)
                curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
                lineTo(2.74f, 8.87f)
                curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                lineTo(4.89f, 11.06f)
                curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
                reflectiveCurveTo(4.82f, 12.64f, 4.87f, 12.94f)
                lineTo(2.84f, 14.52f)
                curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
                lineTo(4.64f, 18.45f)
                curveTo(4.76f, 18.67f, 5.01f, 18.74f, 5.23f, 18.67f)
                lineTo(7.62f, 17.71f)
                curveTo(8.12f, 18.09f, 8.65f, 18.41f, 9.24f, 18.65f)
                lineTo(9.6f, 21.19f)
                curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
                horizontalLineTo(13.92f)
                curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
                lineTo(14.75f, 18.65f)
                curveTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
                lineTo(18.76f, 18.67f)
                curveTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
                lineTo(21.27f, 15.13f)
                curveTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
                close()
                moveTo(12f, 15.6f)
                curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
                reflectiveCurveTo(10.02f, 8.4f, 12f, 8.4f)
                reflectiveCurveTo(15.6f, 10.02f, 15.6f, 12f)
                reflectiveCurveTo(13.98f, 15.6f, 12f, 15.6f); close()
            }
        }
    }

    val Radio: ImageVector by lazy {
        icon("Radio") {
            path(fill = stroke) {
                moveTo(3.24f, 6.15f); lineTo(15.88f, 1.3f); lineTo(16.6f, 3.16f); lineTo(7.45f, 6.67f)
                horizontalLineTo(20f); curveTo(21.1f, 6.67f, 22f, 7.57f, 22f, 8.67f); verticalLineTo(20f)
                curveTo(22f, 21.1f, 21.1f, 22f, 20f, 22f); horizontalLineTo(4f)
                curveTo(2.9f, 22f, 2f, 21.1f, 2f, 20f); verticalLineTo(8.67f)
                curveTo(2f, 7.57f, 2.52f, 6.57f, 3.24f, 6.15f); close()
                moveTo(7f, 17f); curveTo(5.34f, 17f, 4f, 15.66f, 4f, 14f)
                reflectiveCurveTo(5.34f, 11f, 7f, 11f); reflectiveCurveTo(10f, 12.34f, 10f, 14f)
                reflectiveCurveTo(8.66f, 17f, 7f, 17f); close()
                moveTo(20f, 13f); horizontalLineTo(12f); verticalLineTo(11f); horizontalLineTo(20f); close()
            }
        }
    }

    val DashCube: ImageVector by lazy {
        icon("DashCube") {
            path(fill = stroke) {
                moveTo(4f, 4f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); close()
                moveTo(7f, 8f); verticalLineTo(11f); horizontalLineTo(10f); verticalLineTo(8f); close()
                moveTo(14f, 8f); verticalLineTo(11f); horizontalLineTo(17f); verticalLineTo(8f); close()
                moveTo(7f, 14f); verticalLineTo(16.5f); horizontalLineTo(17f); verticalLineTo(14f); close()
            }
        }
    }
}
