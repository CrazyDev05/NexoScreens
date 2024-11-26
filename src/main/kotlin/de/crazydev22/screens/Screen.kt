package de.crazydev22.screens

import com.nexomc.nexo.NexoPlugin
import com.nexomc.nexo.fonts.Glyph
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import java.time.Duration
import java.util.*

data class Screen(
    val id: String,
    val invulnerable: Boolean,
    val movement: Boolean,
    val startCommands: List<String>,
    val endCommands: List<String>
) {
    private var glyph: Glyph? = null

    fun show(players: Set<Player>, color: TextColor, text: Component, time: Long, consumer: (UUID, View) -> Unit) {
        val title = createTitle(color, text, time)
        players.forEach { player ->
            val placeholders = mapOf("name" to player.name, "uuid" to player.uniqueId.toString())
            val endCommands = replace(endCommands, placeholders)
            val startCommands = replace(startCommands, placeholders)
            runCommands(startCommands)

            val oldInvulnerable = player.isInvulnerable
            if (invulnerable) player.isInvulnerable = true
            consumer(player.uniqueId, View(oldInvulnerable, movement, endCommands, time + System.currentTimeMillis()))
            player.showTitle(title)
        }
    }

    private fun createTitle(color: TextColor, text: Component, time: Long): Title {
        glyph = glyph ?: NexoPlugin.instance().fontManager().glyphFromID("nexoscreens_$id")
        if (glyph == null) throw IllegalStateException("Glyph not found: nexoscreens_$id")

        return Title.title(text, Component.text(glyph!!.character()).font(glyph!!.font()).color(color), Title.Times.times(
            Duration.ofMillis(0),
            Duration.ofMillis(time),
            Duration.ofMillis(0)
        ))
    }

    data class View(
        val invulnerable: Boolean,
        val movement: Boolean,
        val endCommands: List<String>,
        val endTime: Long
    ) {

        fun isOver(): Boolean {
            return System.currentTimeMillis() > endTime
        }

        fun executeCommands() {
            runCommands(endCommands)
        }
    }

    companion object {
        fun parse(id: String, section: ConfigurationSection, defaults: ConfigurationSection): Screen {
            return Screen(
                id,
                section.getBoolean("invulnerable", defaults.getBoolean("invulnerable")),
                section.getBoolean("movement", defaults.getBoolean("movement")),
                section.getStringList("commands.start").ifEmpty { defaults.getStringList("commands.start") },
                section.getStringList("commands.end").ifEmpty { defaults.getStringList("commands.start") }
            )
        }

        fun replace(strings: List<String>, placeholders: Map<String, String>): List<String> {
            return strings.stream()
                .map {
                    var res = it
                    placeholders.forEach{ (key, value) -> res = res.replace(key, value) }
                    res
                }
                .toList()
        }

        fun runCommands(commands: List<String>) {
            val sender = Bukkit.getConsoleSender()
            commands.forEach { Bukkit.dispatchCommand(sender, it) }
        }
    }
}
