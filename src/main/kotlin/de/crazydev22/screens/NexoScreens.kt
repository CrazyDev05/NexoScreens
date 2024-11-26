package de.crazydev22.screens

import com.nexomc.nexo.commands.ReloadCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.Files
import java.util.*

class NexoScreens : JavaPlugin(), Listener {
    private var activeScreens: MutableMap<UUID, Screen.View> = mutableMapOf()
    private var screens: MutableMap<String, Screen> = mutableMapOf()

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        server.scheduler.runTaskTimer(this, this::tickScreens, 0, 1)
        val command = PluginCommand::class.java.getDeclaredConstructor(String::class.java, Plugin::class.java)
        command.isAccessible = true

        server.commandMap.register("nexoscreens", command.newInstance("nexoscreens", this))

        reload()
    }

    override fun onDisable() {
        clear()
    }

    fun show(players: Set<Player>, screen: Screen, color: TextColor, text: Component, time: Long) {
        if (!Bukkit.isPrimaryThread()) {
            server.scheduler.runTask(this) { _ -> show(players, screen, color, text, time) }
            return
        }

        val show = mutableSetOf(*players.toTypedArray())
        show.removeIf { activeScreens.containsKey(it.uniqueId) }
        screen.show(show, color, text, time) { uuid, view -> activeScreens[uuid] = view }
    }

    private fun reload() {
        if (!Bukkit.isPrimaryThread()) {
            server.scheduler.runTask(this) { _ -> reload() }
            return
        }

        clear()
        saveDefaultConfig()
        reloadConfig()
        saveResources()

        val defaults = config.getConfigurationSection("defaults") ?: return
        val screens = config.getConfigurationSection("screens") ?: return

        screens.getKeys(false).forEach { key ->
            val section = screens.getConfigurationSection(key) ?: return@forEach
            this.screens[key] = Screen.parse(key, section, defaults)
        }
    }

    private fun saveResources() {
        val dir = File(server.pluginsFolder, "Nexo")
        val glyphFile = File(dir, "glyphs/nexoscreens.yml")
        val packFile = File(dir, "pack/external_packs/nexoscreens.zip")

        var changed = false
        if (!glyphFile.exists()) {
            glyphFile.parentFile.mkdirs()
            Files.copy(getResource("default/nexoscreens.yml") ?: return, glyphFile.toPath())
            changed = true
        }

        if (!packFile.exists()) {
            packFile.parentFile.mkdirs()
            Files.copy(getResource("default/assets.zip") ?: return, packFile.toPath())
            changed = true
        }

        if (!changed || server.pluginManager.getPlugin("Nexo")?.isEnabled != true) return
        ReloadCommand.reloadAll(server.consoleSender)
    }

    private fun clear() {
        activeScreens.forEach { (uuid, view) ->
            val player = server.getPlayer(uuid) ?: return@forEach
            player.clearTitle()
            view.executeCommands()
            player.isInvulnerable = view.invulnerable
        }
        activeScreens.clear()
        screens.clear()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        val view = activeScreens.remove(player.uniqueId) ?: return
        view.executeCommands()
        player.isInvulnerable = view.invulnerable
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    private fun onMove(event: PlayerMoveEvent) {
        val uuid = event.player.uniqueId
        val view = activeScreens[uuid] ?: return
        if (view.isOver() || view.movement) return
        event.isCancelled = true
    }

    private fun tickScreens() {
        activeScreens.keys.removeIf{ server.getPlayer(it) == null }
        activeScreens.filterValues(Screen.View::isOver)
            .forEach { (uuid, view) ->
                val player = server.getPlayer(uuid) ?: return@forEach
                view.executeCommands()
                player.isInvulnerable = view.invulnerable
                activeScreens.remove(uuid)
            }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) return false
        when (args[0]) {
            "reload" -> {
                reload()
                return true
            }
            "show" -> {
                if (args.size < 5) {
                    return sender.usage()
                }

                val players = mutableSetOf<Player>()
                val rawPlayer = args[1]
                if (rawPlayer == "*") {
                    players.addAll(server.onlinePlayers)
                } else {
                    players.add(server.getPlayer(rawPlayer) ?: return sender.usage())
                }
                val screen = screens[args[2]] ?: return sender.usage()
                val time = args[3].toLongOrNull() ?: return sender.usage()
                val color = TextColor.fromHexString(args[4]) ?: return sender.usage()
                val text = if (args.size < 6) Component.empty() else MiniMessage.miniMessage().deserialize(args.copyOfRange(5, args.size).joinToString(" "))

                show(players, screen, color, text, time)
                sender.sendMessage("Showing screen!")
            }
        }
        return true
    }

    private fun CommandSender.usage(): Boolean {
        sendMessage("Usage: /nexoscreens show <player> <screen> <time> <color> <text>")
        return false
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val list = mutableListOf<String>()
        when (args.size) {
            1 -> list.addAll(listOf("show", "reload"))
            2 -> if (args[0] == "show") list.run {
                addAll(server.onlinePlayers.map { it.name })
                add("*")
            }
            3 -> if (args[0] == "show") list.addAll(screens.keys)
            4 -> if (args[0] == "show") list.add("10000")
            5 -> if (args[0] == "show") list.add("#0000ff")
            6 -> if (args[0] == "show") list.add("<green>Test")
        }
        list.removeIf{ !it.startsWith(args.last()) }
        return list
    }
}
