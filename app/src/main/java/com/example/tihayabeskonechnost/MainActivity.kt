package com.example.tihayabeskonechnost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

private val Ink = Color(0xFF171923)
private val Muted = Color(0xFF9298A8)
private val Accent = Color(0xFF8C9EFF)
private val Mint = Color(0xFF9ADBCB)
private val Panel = Color(0xFF222633)
private val Background = Color(0xFF141720)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("quiet_save", MODE_PRIVATE)
        val saved = prefs.getString("state", null)
        val game = GameState.load(saved)
        val now = System.currentTimeMillis()
        if (game.lastSave > 0L && now > game.lastSave) {
            val seconds = ((now - game.lastSave) / 1000.0).coerceAtMost(8 * 60 * 60.0)
            game.energy += seconds * game.productionPerSecond()
        }
        game.lastSave = now
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, secondary = Mint, background = Background,
                    surface = Panel, onSurface = Color(0xFFE8EAF2)
                )
            ) {
                GameScreen(game) {
                    game.lastSave = System.currentTimeMillis()
                    prefs.edit().putString("state", game.save()).apply()
                }
            }
        }
    }
}

data class UpgradeCard(
    val id: String, val name: String, val description: String,
    val multiplier: Double, val level: Int = 1, val rarity: String = "Обычная"
)

data class Skill(
    val id: String, val name: String, val description: String,
    val cost: Double, val bought: Boolean = false
)

private fun cardPool() = listOf(
    UpgradeCard("echo", "Эхо источника", "Увеличивает производство генераторов на 15%.", 0.15, rarity = "Обычная"),
    UpgradeCard("calm", "Спокойный ритм", "Увеличивает силу каждого нажатия на 20%.", 0.20, rarity = "Обычная"),
    UpgradeCard("starlight", "Звёздная пыль", "Усиливает производство генераторов на 35%.", 0.35, rarity = "Редкая"),
    UpgradeCard("resonance", "Резонанс", "Увеличивает общий поток на 55%.", 0.55, rarity = "Эпическая"),
    UpgradeCard("bloom", "Тихое цветение", "Усиливает производство на 80%.", 0.80, rarity = "Легендарная")
)

class GameState {
    var energy by mutableDoubleStateOf(0.0)
    var totalEnergy by mutableDoubleStateOf(0.0)
    var xp by mutableDoubleStateOf(0.0)
    var level by mutableIntStateOf(1)
    var clicks by mutableIntStateOf(0)
    var generatorLevel by mutableIntStateOf(0)
    var clickLevel by mutableIntStateOf(0)
    var cardSlots by mutableIntStateOf(0)
    var lastSave: Long = 0L
    val cards = mutableStateListOf<UpgradeCard>()
    val pendingCardChoices = mutableStateListOf<UpgradeCard>()
    val skills = mutableStateListOf(
        Skill("tap", "Тихий импульс", "Каждое нажатие даёт больше энергии.", 40.0),
        Skill("flow", "Ровный поток", "Увеличивает производство генераторов.", 100.0),
        Skill("wisdom", "Созерцание", "Опыт поступает быстрее.", 180.0),
        Skill("capacity", "Расширение", "Открывает дополнительный слот карточки.", 350.0)
    )

    fun xpNeed() = 35.0 * level.toDouble().pow(1.28)
    fun clickPower() = (1.0 + clickLevel * 0.65) *
        if (skills.first { it.id == "tap" }.bought) 1.8 else 1.0
    fun productionPerSecond(): Double {
        val cardBonus = cards.sumOf { it.multiplier * it.level }
        val skillBonus = if (skills.first { it.id == "flow" }.bought) 1.5 else 1.0
        return generatorLevel * 0.7 * skillBonus * (1.0 + cardBonus)
    }
    fun addEnergy(amount: Double) {
        energy += amount
        totalEnergy += amount
        val xpBoost = if (skills.first { it.id == "wisdom" }.bought) 1.5 else 1.0
        xp += amount * 0.12 * xpBoost
        while (xp >= xpNeed()) {
            xp -= xpNeed()
            level++
            pendingCardChoices.clear()
            pendingCardChoices.addAll(cardPool().shuffled().take(3))
        }
    }
    fun tap() {
        clicks++
        addEnergy(clickPower())
        if (clicks % 10 == 0) addEnergy(clickPower() * 2.0)
    }
    fun buyGenerator(): Boolean {
        val cost = generatorCost()
        if (energy < cost) return false
        energy -= cost
        generatorLevel++
        return true
    }
    fun generatorCost() = 12.0 * 1.48.pow(generatorLevel)
    fun buyClick(): Boolean {
        val cost = clickCost()
        if (energy < cost) return false
        energy -= cost
        clickLevel++
        return true
    }
    fun clickCost() = 20.0 * 1.7.pow(clickLevel)
    fun chooseCard(card: UpgradeCard) {
        if (pendingCardChoices.remove(card)) {
            cards.add(card)
            if (cards.size > 3 + cardSlots) cards.removeAt(0)
        }
    }
    fun buySkill(index: Int): Boolean {
        val s = skills[index]
        if (s.bought || energy < s.cost) return false
        energy -= s.cost
        skills[index] = s.copy(bought = true)
        if (s.id == "capacity") cardSlots++
        return true
    }
    fun save(): String {
        val o = JSONObject()
        o.put("energy", energy); o.put("total", totalEnergy); o.put("xp", xp)
        o.put("level", level); o.put("clicks", clicks); o.put("generator", generatorLevel)
        o.put("clickLevel", clickLevel); o.put("lastSave", lastSave); o.put("cardSlots", cardSlots)
        val a = JSONArray()
        cards.forEach { c ->
            a.put(JSONObject().put("id", c.id).put("name", c.name)
                .put("description", c.description).put("multiplier", c.multiplier)
                .put("level", c.level).put("rarity", c.rarity))
        }
        o.put("cards", a)
        val sk = JSONArray()
        skills.forEach { sk.put(it.bought) }
        o.put("skills", sk)
        return o.toString()
    }

    companion object {
        fun load(raw: String?): GameState {
            val g = GameState()
            if (raw.isNullOrBlank()) return g
            runCatching {
                val o = JSONObject(raw)
                g.energy = o.optDouble("energy", 0.0)
                g.totalEnergy = o.optDouble("total", 0.0)
                g.xp = o.optDouble("xp", 0.0)
                g.level = o.optInt("level", 1)
                g.clicks = o.optInt("clicks", 0)
                g.generatorLevel = o.optInt("generator", 0)
                g.clickLevel = o.optInt("clickLevel", 0)
                g.lastSave = o.optLong("lastSave", 0L)
                g.cardSlots = o.optInt("cardSlots", 0)
                val a = o.optJSONArray("cards") ?: JSONArray()
                for (i in 0 until a.length()) {
                    val c = a.getJSONObject(i)
                    g.cards.add(UpgradeCard(c.getString("id"), c.getString("name"),
                        c.getString("description"), c.getDouble("multiplier"),
                        c.optInt("level", 1), c.optString("rarity", "Обычная")))
                }
                val sk = o.optJSONArray("skills") ?: JSONArray()
                for (i in 0 until minOf(sk.length(), g.skills.size)) {
                    g.skills[i] = g.skills[i].copy(bought = sk.optBoolean(i))
                }
            }
            return g
        }
    }
}

@Composable
fun GameScreen(game: GameState, save: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var pulse by remember { mutableIntStateOf(0) }
    var showLevelUp by remember { mutableStateOf(false) }
    var previousLevel by remember { mutableIntStateOf(game.level) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val before = game.level
            game.addEnergy(game.productionPerSecond())
            if (game.level > before) showLevelUp = true
            save()
        }
    }
    LaunchedEffect(game.level) {
        if (game.level > previousLevel) showLevelUp = true
        previousLevel = game.level
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Color.White) {
                val entries = listOf("Остров", "Карты", "Навыки", "Архив")
                val icons = listOf(Icons.Default.Home, Icons.Default.Style, Icons.Default.AutoAwesome, Icons.Default.Insights)
                entries.forEachIndexed { i, name ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = name) },
                        label = { Text(name, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent, indicatorColor = Color(0xFF343B50),
                            unselectedIconColor = Muted, selectedTextColor = Color.White,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(tab, modifier = Modifier.padding(padding), label = "tab") { page ->
            when (page) {
                0 -> IslandPage(game, onTap = {
                    val old = game.level
                    game.tap()
                    pulse++
                    if (game.level > old) showLevelUp = true
                    save()
                }, onGenerator = { if (game.buyGenerator()) save() },
                    onClickUpgrade = { if (game.buyClick()) save() })
                1 -> CardsPage(game, onChoose = { game.chooseCard(it); save() })
                2 -> SkillsPage(game, onBuy = { if (game.buySkill(it)) save() })
                else -> ArchivePage(game)
            }
        }
    }
    if (showLevelUp) {
        AlertDialog(
            onDismissRequest = { showLevelUp = false },
            title = { Text("Новый уровень ✦") },
            text = { Text("Ты достиг ${game.level} уровня. Выбери новую карточку во вкладке «Карты».") },
            confirmButton = { TextButton(onClick = { showLevelUp = false; tab = 1 }) { Text("К карточкам") } }
        )
    }
}

@Composable
fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F1F7))
        Text(subtitle, fontSize = 13.sp, color = Muted)
    }
}

@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, color = Muted, fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

fun fmt(value: Double): String = when {
    value >= 1e12 -> "%.2fT".format(value / 1e12)
    value >= 1e9 -> "%.2fB".format(value / 1e9)
    value >= 1e6 -> "%.2fM".format(value / 1e6)
    value >= 1000 -> "%.1fK".format(value / 1000)
    else -> "%.1f".format(value)
}

@Composable
fun IslandPage(game: GameState, onTap: () -> Unit, onGenerator: () -> Unit, onClickUpgrade: () -> Unit) {
    val progress = (game.xp / game.xpNeed()).toFloat().coerceIn(0f, 1f)
    val scale by animateFloatAsState(if (game.clicks % 2 == 0) 1f else 0.96f, label = "source")
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ТИХАЯ БЕСКОНЕЧНОСТЬ", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("Твой остров", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Surface(color = Panel, shape = CircleShape) {
                Icon(Icons.Default.Spa, null, tint = Mint, modifier = Modifier.padding(12.dp).size(22.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("ЭНЕРГИЯ", fmt(game.energy), Modifier.weight(1f))
            StatPill("В СЕКУНДУ", fmt(game.productionPerSecond()), Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(230.dp).scale(scale).background(
                Brush.radialGradient(listOf(Color(0xFF566A9E), Color(0xFF303B5A), Background)),
                CircleShape
            ).clickable { onTap() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(158.dp).background(
                Brush.linearGradient(listOf(Color(0xFFC4D7FF), Color(0xFF829DDA), Color(0xFF5369A6))),
                CircleShape
            ), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Bolt, null, tint = Color(0xFF18233C), modifier = Modifier.size(76.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("ИСТОЧНИК", color = Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        Text("Коснись, чтобы собрать энергию", color = Color.White, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Уровень ${game.level}", color = Color.White, fontWeight = FontWeight.Medium)
                Text("${fmt(game.xp)} / ${fmt(game.xpNeed())} опыта", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp),
                color = Accent, trackColor = Panel)
        }
        Spacer(Modifier.height(22.dp))
        UpgradeButton("Генератор энергии", "Уровень ${game.generatorLevel} · +0.7/сек", fmt(game.generatorCost()), game.energy >= game.generatorCost(), onGenerator)
        Spacer(Modifier.height(9.dp))
        UpgradeButton("Сила прикосновения", "Уровень ${game.clickLevel} · усиливает нажатия", fmt(game.clickCost()), game.energy >= game.clickCost(), onClickUpgrade)
        Spacer(Modifier.weight(0.7f))
        Text("Здесь не нужно спешить. Остров подождёт.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun UpgradeButton(title: String, description: String, cost: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = Panel, shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                Text(description, color = Muted, fontSize = 11.sp)
            }
            Text("$cost ✦", color = if (enabled) Mint else Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CardsPage(game: GameState, onChoose: (UpgradeCard) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        PageTitle("Карты", "Выбирай одно усиление при каждом новом уровне")
        Spacer(Modifier.height(18.dp))
        Text("АКТИВНЫЕ КАРТЫ · ${game.cards.size}/${3 + game.cardSlots}", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        if (game.cards.isEmpty()) {
            Text("Пока пусто. Повышай уровень, чтобы получить первую карту.", color = Muted, modifier = Modifier.padding(vertical = 18.dp))
        }
        game.cards.forEach { CardRow(it) }
        Spacer(Modifier.height(22.dp))
        if (game.pendingCardChoices.isNotEmpty()) {
            Text("НОВЫЙ ВЫБОР · ВОЗЬМИ ОДНУ КАРТУ", color = Mint, fontSize = 11.sp, letterSpacing = 1.sp)
            game.pendingCardChoices.toList().forEach { card ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onChoose(card) },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(card.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(card.rarity.uppercase(), color = if (card.rarity == "Легендарная") Color(0xFFF1C78A) else Accent, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(card.description, color = Muted, fontSize = 12.sp)
                        Text("ВЫБРАТЬ  ·  +${(card.multiplier * 100).toInt()}%", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        } else {
            Text("Следующий выбор появится при повышении уровня.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun CardRow(card: UpgradeCard) {
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF2B3345), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = Accent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(card.name, color = Color.White, fontWeight = FontWeight.Medium)
                Text("${card.rarity} · уровень ${card.level}", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SkillsPage(game: GameState, onBuy: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        PageTitle("Древо навыков", "Постоянные открытия за накопленную энергию")
        Spacer(Modifier.height(8.dp))
        Text("Доступно для исследований: ${fmt(game.energy)} ✦", color = Mint, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(game.skills.size) { index ->
                val skill = game.skills[index]
                Surface(color = Panel, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (skill.bought) Icons.Default.AutoAwesome else Icons.Default.Lock, null,
                            tint = if (skill.bought) Mint else Muted, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(skill.description, color = Muted, fontSize = 11.sp)
                            Text(if (skill.bought) "Открыто" else "Стоимость: ${fmt(skill.cost)}", color = Mint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (!skill.bought) {
                            Button(onClick = { onBuy(index) }, enabled = game.energy >= skill.cost,
                                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("Открыть", fontSize = 11.sp)
                            }
                        } else {
                            Icon(Icons.Default.AutoAwesome, null, tint = Mint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivePage(game: GameState) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        PageTitle("Архив", "Твоя история маленьких открытий")
        Spacer(Modifier.height(20.dp))
        StatPill("ТЕКУЩИЙ УРОВЕНЬ", game.level.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StatPill("ВСЕГО ЭНЕРГИИ СОБРАНО", fmt(game.totalEnergy), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StatPill("КАСАНИЙ ИСТОЧНИКА", game.clicks.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        StatPill("ГЕНЕРАТОРЫ", game.generatorLevel.toString(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        Text("«Большие миры начинаются с одного тихого импульса»", color = Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun mutableDoubleStateOf(value: Double) = androidx.compose.runtime.mutableDoubleStateOf(value)
private fun mutableIntStateOf(value: Int) = androidx.compose.runtime.mutableIntStateOf(value)
