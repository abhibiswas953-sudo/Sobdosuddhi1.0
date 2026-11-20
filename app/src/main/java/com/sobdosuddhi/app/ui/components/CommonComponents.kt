package com.sobdosuddhi.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sobdosuddhi.app.domain.BengaliUtils
import com.sobdosuddhi.app.model.DiffOp
import com.sobdosuddhi.app.model.DiffSegment
import com.sobdosuddhi.app.model.ProcessingStage
import com.sobdosuddhi.app.model.ProgressState
import kotlinx.coroutines.delay

/**
 * Custom Sobdosuddhi brand emblem: manuscript page & golden fountain pen nib.
 */
@Composable
fun SobdosuddhiLogo(
  modifier: Modifier = Modifier,
  size: Dp = 44.dp
) {
  val bgColor = MaterialTheme.colorScheme.primary
  val paperColor = Color(0xFFFAF8F3)
  val goldColor = Color(0xFFD4A373)

  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawRoundRect(
      color = bgColor,
      cornerRadius = CornerRadius(w * 0.24f, h * 0.24f)
    )
    // Manuscript sheet
    drawRoundRect(
      color = paperColor,
      topLeft = Offset(w * 0.25f, h * 0.18f),
      size = Size(w * 0.50f, h * 0.64f),
      cornerRadius = CornerRadius(w * 0.06f, h * 0.06f)
    )
    // Subtle literary lines on sheet
    val lineColor = Color(0xFFCFC8BA)
    drawLine(lineColor, Offset(w * 0.34f, h * 0.34f), Offset(w * 0.66f, h * 0.34f), strokeWidth = w * 0.032f)
    drawLine(lineColor, Offset(w * 0.34f, h * 0.45f), Offset(w * 0.60f, h * 0.45f), strokeWidth = w * 0.032f)
    drawLine(lineColor, Offset(w * 0.34f, h * 0.56f), Offset(w * 0.54f, h * 0.56f), strokeWidth = w * 0.032f)

    // Fountain pen nib accent
    val nib = Path().apply {
      moveTo(w * 0.72f, h * 0.46f)
      lineTo(w * 0.82f, h * 0.56f)
      lineTo(w * 0.56f, h * 0.80f)
      lineTo(w * 0.44f, h * 0.82f)
      lineTo(w * 0.46f, h * 0.70f)
      close()
    }
    drawPath(nib, color = goldColor)
    drawCircle(color = bgColor, radius = w * 0.032f, center = Offset(w * 0.60f, h * 0.64f))
  }
}

@Composable
fun SobdosuddhiSplashScreen(onFinished: () -> Unit) {
  var started by remember { mutableStateOf(false) }
  val alpha by animateFloatAsState(
    targetValue = if (started) 1f else 0f,
    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
    label = "splashAlpha"
  )
  val scale by animateFloatAsState(
    targetValue = if (started) 1f else 0.92f,
    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    label = "splashScale"
  )

  LaunchedEffect(Unit) {
    started = true
    delay(900L)
    onFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .alpha(alpha)
        .scale(scale)
        .padding(32.dp)
    ) {
      SobdosuddhiLogo(size = 84.dp)
      Spacer(modifier = Modifier.height(20.dp))
      Text(
        text = "শব্দশুদ্ধি ১.০",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "বাংলা পান্ডুলিপি প্রুফরিডিং, ভাষাতাত্ত্বিক বিশ্লেষণ ও সাহিত্য সম্পাদনা",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

/**
 * Clean literary surface container with subtle hairline border (no nested card-in-card clutter).
 */
@Composable
fun LiteraryCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  content: @Composable () -> Unit
) {
  val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  val shape = RoundedCornerShape(16.dp)
  if (onClick != null) {
    Surface(
      onClick = onClick,
      modifier = modifier,
      shape = shape,
      color = containerColor,
      border = border
    ) {
      content()
    }
  } else {
    Surface(
      modifier = modifier,
      shape = shape,
      color = containerColor,
      border = border
    ) {
      content()
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  subtitle: String? = null,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground
    )
    if (!subtitle.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * Honest, measurable processing progress UI — never shows fake percentage timers.
 */
@Composable
fun ProcessingProgressCard(
  progress: ProgressState,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  LiteraryCard(
    modifier = modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.4.dp,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = progress.stage.bengaliLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        TextButton(onClick = onCancel) {
          Icon(Icons.Outlined.Close, contentDescription = "বাতিল", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("বাতিল")
        }
      }

      if (progress.detailText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = progress.detailText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Measurable chunk progress bar ONLY when chunk count is real & > 1; otherwise indeterminate
      if (progress.totalChunks > 1) {
        val ratio = (progress.completedChunks.toFloat() / progress.totalChunks.toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { ratio },
          modifier = Modifier.fillMaxWidth().height(5.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "সম্পন্ন খণ্ড: ${BengaliUtils.toBengaliDigits(progress.completedChunks)} / ${BengaliUtils.toBengaliDigits(progress.totalChunks)}  •  অতিবাহিত সময়: ${BengaliUtils.toBengaliDigits(progress.elapsedSeconds)} সেকেন্ড",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        LinearProgressIndicator(
          modifier = Modifier.fillMaxWidth().height(5.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "ধাপ ${BengaliUtils.toBengaliDigits(progress.stage.stepIndex)} / ৫  •  অতিবাহিত সময়: ${BengaliUtils.toBengaliDigits(progress.elapsedSeconds)} সেকেন্ড",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Spacer(modifier = Modifier.height(10.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProcessingStage.entries.forEach { step ->
          val done = step.stepIndex < progress.stage.stepIndex
          val active = step.stepIndex == progress.stage.stepIndex
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = when {
                done -> MaterialTheme.colorScheme.primary
                active -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
              }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = step.bengaliLabel,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
              color = when {
                done || active -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
              }
            )
          }
        }
      }
    }
  }
}

@Composable
fun ErrorBannerCard(
  message: String,
  onRetry: (() -> Unit)? = null,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  LiteraryCard(
    modifier = modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Icon(
          imageVector = Icons.Outlined.ErrorOutline,
          contentDescription = "ত্রুটি",
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.weight(1f)
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onDismiss) {
          Text("বন্ধ করুন", color = MaterialTheme.colorScheme.onErrorContainer)
        }
        if (onRetry != null) {
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("পুনরায় চেষ্টা করুন")
          }
        }
      }
    }
  }
}

@Composable
fun EmptyStateView(
  icon: ImageVector,
  title: String,
  description: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(48.dp),
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(14.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = description,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (actionLabel != null && onAction != null) {
      Spacer(modifier = Modifier.height(16.dp))
      Button(onClick = onAction) {
        Text(actionLabel)
      }
    }
  }
}

/**
 * Highlights word-level insertions and deletions cleanly for Before/After comparison and Version Control diffs.
 */
@Composable
fun InlineDiffTextView(
  segments: List<DiffSegment>,
  fontSizeSp: Float = 15f,
  modifier: Modifier = Modifier
) {
  val insertBg = MaterialTheme.colorScheme.primaryContainer
  val insertFg = MaterialTheme.colorScheme.onPrimaryContainer
  val deleteBg = MaterialTheme.colorScheme.errorContainer
  val deleteFg = MaterialTheme.colorScheme.onErrorContainer
  val normalFg = MaterialTheme.colorScheme.onSurface

  val annotated = remember(segments, insertBg, deleteBg, normalFg) {
    buildAnnotatedString {
      for (seg in segments) {
        when (seg.op) {
          DiffOp.EQUAL -> {
            withStyle(SpanStyle(color = normalFg)) { append(seg.text) }
          }
          DiffOp.DELETE -> {
            withStyle(
              SpanStyle(
                background = deleteBg,
                color = deleteFg,
                textDecoration = TextDecoration.LineThrough
              )
            ) { append(seg.text) }
          }
          DiffOp.INSERT -> {
            withStyle(
              SpanStyle(
                background = insertBg,
                color = insertFg,
                fontWeight = FontWeight.SemiBold
              )
            ) { append(seg.text) }
          }
        }
      }
    }
  }

  Text(
    text = annotated,
    style = MaterialTheme.typography.bodyLarge.copy(
      fontSize = fontSizeSp.sp,
      lineHeight = (fontSizeSp * 1.6f).sp
    ),
    modifier = modifier
  )
}

@Composable
fun BengaliConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  dismissLabel: String = "বাতিল",
  isDestructive: Boolean = false,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
    text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = if (isDestructive) {
          ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
          ButtonDefaults.buttonColors()
        }
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss) {
        Text(dismissLabel)
      }
    }
  )
}
