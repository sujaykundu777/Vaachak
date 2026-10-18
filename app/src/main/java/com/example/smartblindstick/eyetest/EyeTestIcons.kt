package com.example.smartblindstick.eyetest

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom vector and canvas-drawn icons precisely styled to match eye test UI.jpeg.
 */
object EyeTestIcons {

    @Composable
    fun EyeIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Outer eye shape path
            val path = Path().apply {
                moveTo(w * 0.1f, h * 0.5f)
                cubicTo(w * 0.3f, h * 0.18f, w * 0.7f, h * 0.18f, w * 0.9f, h * 0.5f)
                cubicTo(w * 0.7f, h * 0.82f, w * 0.3f, h * 0.82f, w * 0.1f, h * 0.5f)
                close()
            }
            drawPath(path = path, color = color, style = Stroke(width = w * 0.1f, cap = StrokeCap.Round))

            // Inner pupil
            drawCircle(
                color = color,
                radius = w * 0.18f,
                center = Offset(w * 0.5f, h * 0.5f),
                style = Fill
            )
        }
    }

    @Composable
    fun EarVoiceIcon(modifier: Modifier = Modifier, color: Color = Color.White) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Ear outline
            val path = Path().apply {
                moveTo(w * 0.35f, h * 0.85f)
                cubicTo(w * 0.15f, h * 0.7f, w * 0.1f, h * 0.35f, w * 0.35f, h * 0.15f)
                cubicTo(w * 0.75f, h * 0.05f, w * 0.9f, h * 0.35f, w * 0.7f, h * 0.6f)
                cubicTo(w * 0.55f, h * 0.75f, w * 0.45f, h * 0.75f, w * 0.45f, h * 0.85f)
            }
            drawPath(path = path, color = color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))

            // Inner fold
            val innerPath = Path().apply {
                moveTo(w * 0.35f, h * 0.35f)
                cubicTo(w * 0.55f, h * 0.3f, w * 0.6f, h * 0.45f, w * 0.5f, h * 0.55f)
            }
            drawPath(path = innerPath, color = color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun MicIcon(modifier: Modifier = Modifier, color: Color = Color(0xFF10B981)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Pill microphone head
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.32f, h * 0.12f),
                size = Size(w * 0.36f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
            )

            // Cradle
            val cradle = Path().apply {
                moveTo(w * 0.22f, h * 0.4f)
                cubicTo(w * 0.22f, h * 0.72f, w * 0.78f, h * 0.72f, w * 0.78f, h * 0.4f)
            }
            drawPath(path = cradle, color = color, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))

            // Stand & Base
            drawLine(
                color = color,
                start = Offset(w * 0.5f, h * 0.72f),
                end = Offset(w * 0.5f, h * 0.88f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.32f, h * 0.88f),
                end = Offset(w * 0.68f, h * 0.88f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun SpeakerIcon(modifier: Modifier = Modifier, color: Color = Color.Black) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            val body = Path().apply {
                moveTo(w * 0.15f, h * 0.38f)
                lineTo(w * 0.32f, h * 0.38f)
                lineTo(w * 0.56f, h * 0.18f)
                lineTo(w * 0.56f, h * 0.82f)
                lineTo(w * 0.32f, h * 0.62f)
                lineTo(w * 0.15f, h * 0.62f)
                close()
            }
            drawPath(path = body, color = color, style = Fill)

            // Sound waves
            val arc1 = Path().apply {
                moveTo(w * 0.68f, h * 0.36f)
                cubicTo(w * 0.76f, h * 0.43f, w * 0.76f, h * 0.57f, w * 0.68f, h * 0.64f)
            }
            drawPath(path = arc1, color = color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))

            val arc2 = Path().apply {
                moveTo(w * 0.78f, h * 0.25f)
                cubicTo(w * 0.92f, h * 0.37f, w * 0.92f, h * 0.63f, w * 0.78f, h * 0.75f)
            }
            drawPath(path = arc2, color = color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun ContrastToggleIcon(modifier: Modifier = Modifier, color: Color = Color.Black) {
        Canvas(modifier = modifier) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)

            // Outer stroke
            drawCircle(
                color = color,
                radius = r * 0.88f,
                center = c,
                style = Stroke(width = r * 0.18f)
            )

            // Filled right half
            val halfPath = Path().apply {
                moveTo(c.x, c.y - r * 0.88f)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        c.x - r * 0.88f,
                        c.y - r * 0.88f,
                        c.x + r * 0.88f,
                        c.y + r * 0.88f
                    ),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                close()
            }
            drawPath(path = halfPath, color = color, style = Fill)
        }
    }

    @Composable
    fun CompassIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val r = size.minDimension * 0.44f
            val center = Offset(w / 2f, h / 2f)

            drawCircle(color = color, radius = r, center = center, style = Stroke(width = w * 0.09f))

            // Compass needle
            val north = Path().apply {
                moveTo(center.x, center.y - r * 0.75f)
                lineTo(center.x + r * 0.32f, center.y + r * 0.15f)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(path = north, color = color, style = Fill)

            val south = Path().apply {
                moveTo(center.x, center.y + r * 0.75f)
                lineTo(center.x - r * 0.32f, center.y - r * 0.15f)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(path = south, color = color, style = Stroke(width = w * 0.07f))
        }
    }

    @Composable
    fun TextReaderIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Document rect
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.2f, h * 0.15f),
                size = Size(w * 0.6f, h * 0.7f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Stroke(width = w * 0.09f)
            )

            // Text lines inside
            drawLine(
                color = color,
                start = Offset(w * 0.32f, h * 0.35f),
                end = Offset(w * 0.68f, h * 0.35f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.32f, h * 0.5f),
                end = Offset(w * 0.68f, h * 0.5f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(w * 0.32f, h * 0.65f),
                end = Offset(w * 0.54f, h * 0.65f),
                strokeWidth = w * 0.08f,
                cap = StrokeCap.Round
            )
        }
    }

    @Composable
    fun HelpIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            // Rounded question box / badge
            drawRoundRect(
                color = color,
                topLeft = Offset(w * 0.18f, h * 0.18f),
                size = Size(w * 0.64f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = Stroke(width = w * 0.09f)
            )

            // Question mark arc
            val qPath = Path().apply {
                moveTo(w * 0.38f, h * 0.38f)
                cubicTo(w * 0.38f, h * 0.3f, w * 0.62f, h * 0.3f, w * 0.62f, h * 0.42f)
                cubicTo(w * 0.62f, h * 0.52f, w * 0.5f, h * 0.52f, w * 0.5f, h * 0.6f)
            }
            drawPath(path = qPath, color = color, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))

            // Dot
            drawCircle(
                color = color,
                radius = w * 0.05f,
                center = Offset(w * 0.5f, h * 0.72f),
                style = Fill
            )
        }
    }

    @Composable
    fun SparkleIcon(modifier: Modifier = Modifier, color: Color = Color(0xFF059669)) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 0.5f, h * 0.35f, w * 0.65f, h * 0.5f, w, h * 0.5f)
                cubicTo(w * 0.65f, h * 0.5f, w * 0.5f, h * 0.65f, w * 0.5f, h)
                cubicTo(w * 0.5f, h * 0.65f, w * 0.35f, h * 0.5f, 0f, h * 0.5f)
                cubicTo(w * 0.35f, h * 0.5f, w * 0.5f, h * 0.35f, w * 0.5f, 0f)
                close()
            }
            drawPath(path = path, color = color, style = Fill)
        }
    }


    /**
     * Animated Audio Visualizer matching the green vertical bars in Live Voice Transcription card.
     */
    @Composable
    fun AudioWaveformVisualizer(
        modifier: Modifier = Modifier,
        isListening: Boolean = true,
        barColor: Color = Color(0xFF059669)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveform")

        // Heights for 9 bars
        val baseHeights = listOf(0.35f, 0.65f, 0.95f, 0.5f, 0.85f, 0.4f, 0.75f, 0.55f, 0.3f)
        val animProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "waveAnim"
        )

        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val barCount = baseHeights.size
            val barWidth = (w / (barCount * 2.2f))
            val spacing = barWidth * 1.2f
            val startX = (w - (barCount * barWidth + (barCount - 1) * spacing)) / 2f

            for (i in 0 until barCount) {
                val scale = if (isListening) {
                    val phase = ((i.toFloat() / barCount) + animProgress) % 1f
                    (0.3f + 0.7f * kotlin.math.sin(phase * Math.PI.toFloat()).coerceIn(0f, 1f))
                } else {
                    0.25f
                }

                val currentBarHeight = (h * baseHeights[i] * scale).coerceAtLeast(4.dp.toPx())
                val x = startX + i * (barWidth + spacing)
                val y = (h - currentBarHeight) / 2f

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, currentBarHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }
    }
}
