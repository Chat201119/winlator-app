package com.winlator.butcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Steps of the questions shown after pressing START SNUFF FILM. 0 = not showing. */
const val FILM_ASK_COMPANY = 1
const val FILM_REPLY = 2
const val FILM_ASK_AGE = 3
const val FILM_UNDERAGE = 4

/** Full-screen dark question screen. It blocks touches on the menu underneath. */
@Composable
fun FilmOverlay(
    step: Int,
    reply: String,
    onAlone: () -> Unit,
    onWithOthers: () -> Unit,
    onOver16: () -> Unit,
    onUnder16: () -> Unit,
    onCancel: () -> Unit
) {
    if (step == 0) return
    BackHandler(enabled = step == FILM_ASK_COMPANY || step == FILM_ASK_AGE, onBack = onCancel)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE8000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (step) {
                FILM_ASK_COMPANY -> {
                    FilmText("You Watch Alone or With Other People?")
                    VhsButton("Yes i'm watch alone", onClick = onAlone)
                    VhsButton("Yes I'm Watch with Other people", onClick = onWithOthers)
                }
                FILM_REPLY -> FilmText(reply)
                FILM_ASK_AGE -> {
                    FilmText("Are you over 16+ ?")
                    VhsButton("Yes i am", onClick = onOver16)
                    VhsButton("No i'm not", onClick = onUnder16)
                }
                FILM_UNDERAGE -> FilmText("YOU NEED TO BE OVER 16+ KIDS!", Color(0xFFFF6B6B))
            }
        }
    }
}

@Composable
private fun FilmText(text: String, color: Color = Color.White) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = color
    )
}
