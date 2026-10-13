package io.github.hziqzmin.wildguardai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.hziqzmin.wildguardai.ui.theme.DarkSlateGreen
import io.github.hziqzmin.wildguardai.ui.theme.MontserratFontFamily
import io.github.hziqzmin.wildguardai.ui.theme.OffWhite
import io.github.hziqzmin.wildguardai.ui.theme.WildGuardAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WildGuardAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkSlateGreen
                ) {
                    FigmaLoadingScreen()
                }
            }
        }
    }
}

/**
 * Clean Canvas — Figma Loading Page:
 * Centered white Montserrat Bold "WildGuard AI" on #2D3A3A background.
 */
@Composable
fun FigmaLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSlateGreen),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "WildGuard AI",
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = OffWhite
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FigmaLoadingPreview() {
    WildGuardAITheme {
        FigmaLoadingScreen()
    }
}