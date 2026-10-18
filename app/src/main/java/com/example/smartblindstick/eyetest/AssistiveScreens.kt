package com.example.smartblindstick.eyetest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TextReaderScreen(
    onReadRequested: () -> Unit,
    onCloudReadRequested: () -> Unit,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = {
            EyeTestBottomNavBar(
                activeTab = "Text Reader",
                onTabSelected = onTabSelected
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5))
                    .border(2.dp, Color(0xFF10B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                EyeTestIcons.TextReaderIcon(modifier = Modifier.size(44.dp), color = Color(0xFF059669))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Instant Text Reader",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Point camera at any physical document, book, sign, or label. Tap below or say 'Read' to listen aloud.",
                fontSize = 14.sp,
                color = Color(0xFF4B5563),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onReadRequested,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EyeTestIcons.EyeIcon(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Point Camera & Read Aloud", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onCloudReadRequested, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Read with Cloud AI")
            }
        }
    }
}

@Composable
fun HelpScreen(
    onSpeak: (String) -> Unit,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        bottomBar = {
            EyeTestBottomNavBar(
                activeTab = "Help",
                onTabSelected = onTabSelected
            )
        },
        containerColor = Color(0xFFF9FAFB)
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Accessibility & Voice Commands",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF111827)
            )

            Text(
                text = "Vaachak is optimized for partially and completely blind users. You can speak naturally or tap to activate features.",
                fontSize = 13.sp,
                color = Color(0xFF4B5563),
                lineHeight = 18.sp
            )

            Button(onClick = { onTabSelected("API Playground") }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Cloud API Playground")
            }

            val commands = listOf(
                Pair("Eye Test", "Read letters aloud during the test. Say 'Next' to advance, or 'I cannot see' if letters are unreadable."),
                Pair("\"Read\"", "Scans any physical document or text in front of you and reads it aloud."),
                Pair("\"Describe\"", "Uses Vaachak Vision AI to describe your surroundings in full rich detail."),
                Pair("\"Find [item]\"", "Searches for an item (e.g. cup, chair, door, keys) using a 3x3 directional spatial grid."),
                Pair("\"Turn on/off flashlight\"", "Manually or automatically triggers camera torch to illuminate dark rooms."),
                Pair("\"Switch to spatial audio\"", "Enables directional binaural audio beeps to guide movement.")
            )

            commands.forEach { (cmd, desc) ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = cmd, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                        Text(text = desc, fontSize = 13.sp, color = Color(0xFF374151))
                    }
                }
            }

            Button(
                onClick = {
                    onSpeak("Here are your voice commands: Read, Describe surroundings, Find object, Flashlight, and Spatial audio.")
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Listen to Voice Guide", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
