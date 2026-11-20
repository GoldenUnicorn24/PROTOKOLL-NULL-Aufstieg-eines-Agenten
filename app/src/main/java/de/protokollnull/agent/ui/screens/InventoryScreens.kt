package de.protokollnull.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.protokollnull.agent.engine.*
import de.protokollnull.agent.model.*
import de.protokollnull.agent.ui.*
import de.protokollnull.agent.ui.theme.*

private val categoryLabels = linkedMapOf(
    "all" to "Alle", "equipped" to "Ausgerüstet", "weapons" to "Bewaffnung", "armor" to "Schutz", "gadgets" to "Geräte",
    "medical" to "Medizin", "documents" to "Dokumente", "tools" to "Werkzeug", "equipment" to "Ausrüstung"
)

@Composable
fun InventoryScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var filter by remember { mutableStateOf("all") }
    val p = s.player
    val weight = Inventory.weight(data, p.inventory)
    val cap = Inventory.capacity(data, s)
    val eq = p.equipment.values.toSet()
    val list = p.inventory.filter { st ->
        val def = data.itemById[st.itemId]
        when (filter) { "all" -> true; "equipped" -> st.uid in eq; else -> def?.category == filter }
    }
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Overline("AUSRÜSTUNG", Pn.Accent)
                Text("INVENTAR", style = MaterialTheme.typography.titleLarge)
                StatBar("Gewicht ${"%.1f".format(weight / 1000.0)} / ${"%.1f".format(cap / 1000.0)} kg", weight, cap, if (weight > cap * 0.9) Pn.Danger else Pn.Accent, showValue = false)
                GlassPanel(padding = 12.dp) {
                    Text("Ausrüstungsslots", style = MaterialTheme.typography.titleSmall)
                    Slots.ids.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { slot ->
                                val uid = p.equipment[slot]
                                val st = p.inventory.firstOrNull { it.uid == uid }
                                val def = st?.let { data.itemById[it.itemId] }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (def != null) ItemIcon(def.icon, 52.dp, tint = Pn.rarity[def.rarity] ?: Pn.Accent, modifier = Modifier.padding(2.dp))
                                    else Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Text("—", color = Pn.Muted) }
                                    Text(Slots.label(slot), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                LoadoutPanel(vm, s)
                CompactChips(categoryLabels.entries.map { it.key to it.value }, filter) { filter = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Spind-Lager (${p.storage.size})", Modifier.weight(1f), secondary = true) { vm.open(Overlay.Storage) }
                    ActionButton("Ausrüstungskammer", Modifier.weight(1f), secondary = true, enabled = s.location == "armory", subtitle = if (s.location == "armory") null else "nur vor Ort") { vm.open(Overlay.Shop) }
                }
            }
        }
        items(list, key = { it.uid }) { st ->
            val def = data.itemById[st.itemId]
            val color = Pn.rarity[def?.rarity ?: "common"] ?: Pn.Accent
            GlassPanel(accent = color, padding = 12.dp, onClick = { vm.open(Overlay.Item(st.uid)) }) {
                ItemIcon(def?.icon ?: "notebook", 96.dp, tint = color, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(def?.name ?: st.itemId, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(Pn.rarityLabel(def?.rarity ?: "common"), color)
                    if (st.quantity > 1) Pill("×${st.quantity}", Pn.Text)
                }
                Text((if (st.uid in eq) "◆ ausgerüstet · " else "") + "Zustand ${st.condition}%", style = MaterialTheme.typography.labelSmall, color = if (st.condition < 30) Pn.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (list.isEmpty()) item(span = { GridItemSpan(2) }) { SmallNote("Keine Gegenstände in dieser Kategorie.") }
    }
}

@Composable
fun ItemDetailScreen(vm: GameViewModel, s: GameState, uid: Long) {
    val data = vm.data
    val st = s.player.inventory.firstOrNull { it.uid == uid }
    if (st == null) { LaunchedEffect(uid) { vm.back() }; return }
    val def = data.itemById[st.itemId] ?: return
    val color = Pn.rarity[def.rarity] ?: Pn.Accent
    val equipped = uid in s.player.equipment.values
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(def.name, Pn.rarityLabel(def.rarity), onBack = { vm.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ItemIcon(def.icon, 180.dp, tint = color, modifier = Modifier.align(Alignment.CenterHorizontally))
            GlassPanel(accent = color) {
                Text(def.description, style = MaterialTheme.typography.bodyMedium)
                KeyValue("Kategorie", categoryLabels[def.category] ?: def.category)
                KeyValue("Slot", if (def.slot == "none") "—" else Slots.label(def.slot))
                KeyValue("Gewicht", "${def.weightGrams} g")
                KeyValue("Menge", "${st.quantity} (max. Stapel ${def.maxStack})")
                KeyValue("Zustand", "${st.condition}%")
                KeyValue("Freigabe", "ab Rang ${def.minRank} (${data.rank(def.minRank).name})")
                if (def.bonuses.isNotEmpty()) KeyValue("Boni", def.bonuses.entries.joinToString { Attr.label(it.key) + " " + Effs.signed(it.value) })
                if (def.armor > 0) KeyValue("Schutz", "+${def.armor}")
                if (def.damage > 0) KeyValue("Wirkung (Taktik)", "+${def.damage}")
                if (def.capacityBonusGrams > 0) KeyValue("Tragkraft", "+${def.capacityBonusGrams / 1000} kg")
                def.effect?.let { e -> KeyValue("Wirkung", listOfNotNull(if (e.health != 0) "Gesundheit ${Effs.signed(e.health)}" else null, if (e.energy != 0) "Energie ${Effs.signed(e.energy)}" else null, if (e.stress != 0) "Stress ${Effs.signed(e.stress)}" else null).joinToString()) }
            }
            val cmp = Inventory.compare(data, s, uid)
            if (cmp.isNotEmpty() && !equipped) {
                GlassPanel(accent = Pn.Violet) {
                    Text("VERGLEICH · ${Slots.label(def.slot).uppercase()}", style = MaterialTheme.typography.labelSmall, color = Pn.Violet)
                    Row { Text("", Modifier.weight(1f)); Text(Inventory.equippedName(data, s, def.slot) ?: "leer", Modifier.width(96.dp), style = MaterialTheme.typography.labelSmall, color = Pn.Muted, maxLines = 1); Text(def.name, Modifier.width(96.dp), style = MaterialTheme.typography.labelSmall, color = Pn.Accent, maxLines = 1) }
                    cmp.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(c.current, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                            Text(c.candidate, Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall, color = when { c.delta > 0 -> Pn.Ok; c.delta < 0 -> Pn.Danger; else -> Pn.Text })
                        }
                    }
                }
            }
            val reason = Inventory.canEquip(data, s, st)
            if (def.slot != "none") {
                if (equipped) ActionButton("Ablegen", Modifier.fillMaxWidth(), secondary = true) { vm.act { d, x -> Inventory.unequip(d, x, def.slot) } }
                else ActionButton(if (reason == null) "Ausrüsten" else reason, Modifier.fillMaxWidth(), enabled = reason == null) { vm.act { d, x -> Inventory.equip(d, x, uid) } }
            }
            if (def.effect != null) ActionButton("Verwenden", Modifier.fillMaxWidth()) { vm.act { d, x -> Inventory.use(d, x, uid) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("In den Spind", Modifier.weight(1f), secondary = true, enabled = !equipped) { vm.act { d, x -> Inventory.toStorage(d, x, uid) } }
                if (s.location == "armory") {
                    ActionButton("Warten", Modifier.weight(1f), secondary = true, enabled = st.condition < 100) { vm.act { d, x -> Inventory.repair(d, x, uid) } }
                    ActionButton("Zurückgeben", Modifier.weight(1f), secondary = true, enabled = def.baseValue > 0 && !equipped) { vm.act { d, x -> Inventory.sell(d, x, uid) } }
                }
            }
        }
    }
}

@Composable
fun ShopScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    var filter by remember { mutableStateOf("all") }
    val items = Inventory.shopItems(data, s).filter { filter == "all" || it.category == filter }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ausrüstungskammer", "Owen · ${s.player.credits} Cr", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { CompactChips(categoryLabels.entries.filter { it.key != "equipped" }.map { it.key to it.value }, filter) { filter = it } }
            item { SmallNote("Anforderung gegen Credits. Freigaben richten sich nach deinem Rang. Wartung und Rückgabe in der Detailansicht eines Gegenstands.") }
            items(items, key = { it.id }) { def ->
                val price = Inventory.price(def, s)
                val locked = s.player.rank < def.minRank
                val ageLocked = def.category == "weapons" && Ages.player(s) < 18 && def.id != "pistol_training"
                val color = Pn.rarity[def.rarity] ?: Pn.Accent
                GlassPanel(accent = color, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemIcon(def.icon, 64.dp, tint = color)
                        Column(Modifier.weight(1f)) {
                            Text(def.name, style = MaterialTheme.typography.titleSmall)
                            Text(def.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Pill(Pn.rarityLabel(def.rarity), color)
                                Pill("${def.weightGrams} g", Pn.Muted)
                                if (Inventory.count(s, def.id) > 0) Pill("besitzt ${Inventory.count(s, def.id)}", Pn.Ok)
                            }
                        }
                    }
                    ActionButton(if (locked) "Ab Rang ${def.minRank}" else if (ageLocked) "Erst ab 18" else "Anfordern · $price Cr", Modifier.fillMaxWidth(), enabled = !locked && !ageLocked && s.player.credits >= price, secondary = true) {
                        vm.act { d, x -> Inventory.buy(d, x, def.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageScreen(vm: GameViewModel, s: GameState) {
    val data = vm.data
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Spind-Lager", "Persönliches Lager", onBack = { vm.back() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SmallNote("Was du nicht trägst, lagert hier. Neue Gegenstände landen automatisch im Spind, wenn deine Tragkraft erreicht ist.") }
            if (s.player.storage.isEmpty()) item { SmallNote("Der Spind ist leer.") }
            items(s.player.storage, key = { it.uid }) { st ->
                val def = data.itemById[st.itemId]
                GlassPanel(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ItemIcon(def?.icon ?: "notebook", 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(def?.name ?: st.itemId, style = MaterialTheme.typography.titleSmall)
                            Text("×${st.quantity} · ${(def?.weightGrams ?: 0) * st.quantity} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionButton("Nehmen", secondary = true) { vm.act { d, x -> Inventory.fromStorage(d, x, st.uid) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadoutPanel(vm: GameViewModel, s: GameState) {
    val data = vm.data
    GlassPanel(padding = 12.dp, accent = Pn.Violet) {
        Overline("AUSRÜSTUNGSPROFILE", Pn.Violet)
        s.loadouts.forEach { lo ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lo.name, style = MaterialTheme.typography.titleSmall)
                    Text(if (lo.items.isEmpty()) "leer – aktuelle Ausrüstung speichern" else lo.items.values.joinToString { data.itemById[it]?.name ?: it },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Spacer(Modifier.width(6.dp))
                ActionButton("Anlegen", enabled = lo.items.isNotEmpty(), secondary = true) { vm.act { d, x -> Inventory.applyLoadout(d, x, lo.id) } }
                Spacer(Modifier.width(6.dp))
                ActionButton("Speichern", secondary = true) { vm.act { d, x -> Inventory.saveLoadout(d, x, lo.id) } }
            }
        }
    }
}
