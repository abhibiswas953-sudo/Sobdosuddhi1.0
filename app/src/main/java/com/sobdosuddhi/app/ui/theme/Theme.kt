package com.sobdosuddhi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sobdosuddhi.app.model.ThemeMode

// Literary Parchment & Deep Forest Emerald Palette
val ForestEmerald = Color(0xFF1B4332)
val ForestEmeraldContainer = Color(0xFFD8EBE1)
val InkNavy = Color(0xFF1D3557)
val InkNavyContainer = Color(0xFFDCE7F5)
val WarmOchre = Color(0xFF9A581B)
val WarmOchreContainer = Color(0xFFF6E7D6)
val CrimsonInk = Color(0xFFA62626)
val CrimsonContainer = Color(0xFFFADEDE)

val ParchmentBg = Color(0xFFFAF8F3)
val ParchmentSurface = Color(0xFFFFFFFF)
val ParchmentSurfaceVariant = Color(0xFFF1EDE4)
val ParchmentOutline = Color(0xFFDCD5C7)
val CharcoalInk = Color(0xFF1C1B18)
val MutedInk = Color(0xFF5D5A52)

// Dark Literary Obsidian & Sage Emerald Palette
val SageEmerald = Color(0xFF72C899)
val SageEmeraldContainer = Color(0xFF1C3B2B)
val MistNavy = Color(0xFF93BCE0)
val MistNavyContainer = Color(0xFF1E3247)
val SoftOchre = Color(0xFFE5B17C)
val SoftOchreContainer = Color(0xFF3E2B19)
val SoftCrimson = Color(0xFFF28B82)
val SoftCrimsonContainer = Color(0xFF471E1E)

val ObsidianBg = Color(0xFF111412)
val ObsidianSurface = Color(0xFF191E1B)
val ObsidianSurfaceVariant = Color(0xFF232925)
val ObsidianOutline = Color(0xFF333B36)
val IvoryInk = Color(0xFFECE8DF)
val MutedIvory = Color(0xFFA9A49A)

private val SobdosuddhiLightColors = lightColorScheme(
  primary = ForestEmerald,
  onPrimary = Color.White,
  primaryContainer = ForestEmeraldContainer,
  onPrimaryContainer = Color(0xFF0C261B),
  secondary = InkNavy,
  onSecondary = Color.White,
  secondaryContainer = InkNavyContainer,
  onSecondaryContainer = Color(0xFF0E1D33),
  tertiary = WarmOchre,
  onTertiary = Color.White,
  tertiaryContainer = WarmOchreContainer,
  onTertiaryContainer = Color(0xFF3A1E05),
  error = CrimsonInk,
  onError = Color.White,
  errorContainer = CrimsonContainer,
  onErrorContainer = Color(0xFF470C0C),
  background = ParchmentBg,
  onBackground = CharcoalInk,
  surface = ParchmentSurface,
  onSurface = CharcoalInk,
  surfaceVariant = ParchmentSurfaceVariant,
  onSurfaceVariant = MutedInk,
  outline = ParchmentOutline,
  outlineVariant = Color(0xFFE7E1D6)
)

private val SobdosuddhiDarkColors = darkColorScheme(
  primary = SageEmerald,
  onPrimary = Color(0xFF072617),
  primaryContainer = SageEmeraldContainer,
  onPrimaryContainer = Color(0xFFCFF2DD),
  secondary = MistNavy,
  onSecondary = Color(0xFF0B2033),
  secondaryContainer = MistNavyContainer,
  onSecondaryContainer = Color(0xFFD6E8F8),
  tertiary = SoftOchre,
  onTertiary = Color(0xFF2E1803),
  tertiaryContainer = SoftOchreContainer,
  onTertiaryContainer = Color(0xFFF9DEC0),
  error = SoftCrimson,
  onError = Color(0xFF3D0A0A),
  errorContainer = SoftCrimsonContainer,
  onErrorContainer = Color(0xFFFFDAD6),
  background = ObsidianBg,
  onBackground = IvoryInk,
  surface = ObsidianSurface,
  onSurface = IvoryInk,
  surfaceVariant = ObsidianSurfaceVariant,
  onSurfaceVariant = MutedIvory,
  outline = ObsidianOutline,
  outlineVariant = Color(0xFF28302B)
)

val SobdosuddhiTypography = Typography(
  displaySmall = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp
  ),
  headlineMedium = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    lineHeight = 31.sp
  ),
  headlineSmall = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 27.sp
  ),
  titleLarge = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp,
    lineHeight = 25.sp
  ),
  titleMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 22.sp
  ),
  titleSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 19.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 26.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 21.sp
  ),
  bodySmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp
  ),
  labelLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp
  ),
  labelMedium = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp
  ),
  labelSmall = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 15.sp
  )
)

@Composable
fun SobdosuddhiTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit
) {
  val useDark = when (themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }
  val colorScheme = if (useDark) SobdosuddhiDarkColors else SobdosuddhiLightColors

  MaterialTheme(
    colorScheme = colorScheme,
    typography = SobdosuddhiTypography,
    content = content
  )
}
