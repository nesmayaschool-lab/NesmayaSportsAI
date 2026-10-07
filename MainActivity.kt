package com.nesmaya.sportsai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class Player(
    val number: Int,
    val name: String,
    val team: String,
    val position: String,
    val goals: Int = 0,
    val assists: Int = 0,
    val shots: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NesmayaSportsApp() }
    }
}

@Composable
private fun NesmayaSportsApp() {
    var players by remember { mutableStateOf(listOf<Player>()) }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("نسماية سبورت", style = MaterialTheme.typography.headlineMedium)
            Text("بيانات اللاعبين", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(number, { number = it.filter(Char::isDigit) }, label = { Text("رقم القميص") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("اسم اللاعب") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(team, { team = it }, label = { Text("الفريق") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(position, { position = it }, label = { Text("المركز") }, modifier = Modifier.fillMaxWidth())

            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val n = number.toIntOrNull()
                    when {
                        n == null -> error = "أدخلي رقم القميص"
                        name.isBlank() -> error = "أدخلي اسم اللاعب"
                        players.any { it.number == n && it.team == team } -> error = "رقم القميص مستخدم بالفعل لهذا الفريق"
                        else -> {
                            players = players + Player(n, name.trim(), team.trim(), position.trim())
                            number = ""; name = ""; team = ""; position = ""; error = ""
                        }
                    }
                }
            ) { Text("إضافة اللاعب") }

            HorizontalDivider()

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(players) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("#${p.number}  ${p.name}", style = MaterialTheme.typography.titleMedium)
                            Text("${p.team} • ${p.position}")
                            Text("⚽ أهداف: ${p.goals}   🅰️ تمريرات: ${p.assists}   🎯 تسديدات: ${p.shots}")
                            Text("🏃 المسافة: —   ⚡ السرعة القصوى: —")
                        }
                    }
                }
            }
        }
    }
}
