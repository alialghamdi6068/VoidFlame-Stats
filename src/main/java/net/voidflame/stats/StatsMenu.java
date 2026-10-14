package net.voidflame.stats;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StatsMenu implements org.bukkit.event.Listener {
    private final VoidFlameStatsPlugin plugin;

    public StatsMenu(VoidFlameStatsPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        StatsService.PlayerStats stats = plugin.stats().getCached(player.getUniqueId());
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text("VOIDFLAME MC  •  PLAYER PROFILE", NamedTextColor.LIGHT_PURPLE), 300),
                DialogBody.item(new ItemStack(Material.PLAYER_HEAD),
                        DialogBody.plainMessage(Component.text(player.getName() + "  •  Practice statistics", NamedTextColor.GRAY), 220),
                        true, true, 48, 48),
                DialogBody.plainMessage(Component.text("WINS   " + stats.wins() + "     LOSSES   " + stats.losses(), NamedTextColor.GREEN), 300),
                DialogBody.plainMessage(Component.text("KILLS   " + stats.kills() + "     DEATHS   " + stats.deaths(), NamedTextColor.GRAY), 300),
                DialogBody.plainMessage(Component.text("STREAK   " + stats.streak() + "     BEST   " + stats.bestStreak(), NamedTextColor.GOLD), 300),
                DialogBody.plainMessage(Component.text("ELO   " + Math.round(stats.elo()), NamedTextColor.AQUA), 300)
        );
        show(player, "VoidFlame • Statistics", body, List.of(
                button("Match History", "View your recent matches.", "/history", NamedTextColor.AQUA),
                button("Tiers / Leaderboards", "Choose the ranking section.", "/vfui leaderboard-choice", NamedTextColor.LIGHT_PURPLE),
                button("Top ELO Players", "View the live ELO ranking.", "/stats top", NamedTextColor.GOLD),
                button("Main Menu", "Return to VoidFlame navigation.", "/menu", NamedTextColor.GRAY)
        ), 2);
    }

    public void openLeaderboard(Player player, List<StatsService.RankedPlayer> players) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("TOP PLAYERS  •  ELO", NamedTextColor.AQUA), 300));
        if (players.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("No ranking data is available yet. Complete matches to populate this board.", NamedTextColor.GRAY), 300));
        } else {
            int rank = 1;
            for (StatsService.RankedPlayer playerRow : players) {
                StatsService.PlayerStats s = playerRow.stats();
                NamedTextColor color = rank == 1 ? NamedTextColor.GOLD : rank <= 3 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.WHITE;
                body.add(DialogBody.plainMessage(Component.text(
                        "#" + rank++ + "   " + playerRow.name() + "   •   ELO " + Math.round(s.elo())
                                + "   •   W " + s.wins() + " / L " + s.losses(), color), 320));
            }
        }
        show(player, "VoidFlame • ELO Leaderboard", body, List.of(
                button("Refresh", "Reload the current ranking.", "/stats top", NamedTextColor.AQUA),
                button("Tiers / Leaderboards", "Return to the ranking choices.", "/vfui leaderboard-choice", NamedTextColor.LIGHT_PURPLE),
                button("My Statistics", "Return to your player profile.", "/stats", NamedTextColor.GRAY)
        ), 1);
    }

    public void openHistory(Player player, List<String> entries) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text("RECENT MATCHES", NamedTextColor.AQUA), 300));
        if (entries.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("No completed matches have been recorded yet.", NamedTextColor.GRAY), 300));
        } else {
            for (String entry : entries) {
                body.add(DialogBody.plainMessage(Component.text(entry, NamedTextColor.WHITE), 320));
            }
        }
        show(player, "VoidFlame • Match History", body, List.of(
                button("Refresh", "Reload recent matches.", "/history", NamedTextColor.AQUA),
                button("My Statistics", "Return to your player profile.", "/stats", NamedTextColor.LIGHT_PURPLE),
                button("Main Menu", "Return to VoidFlame navigation.", "/menu", NamedTextColor.GRAY)
        ), 1);
    }

    private void show(Player player, String title, List<DialogBody> body, List<ActionButton> actions, int columns) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.WHITE))
                        .externalTitle(Component.text("VoidFlame", NamedTextColor.LIGHT_PURPLE))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions).columns(columns).build()));
        player.showDialog(dialog);
    }

    private ActionButton button(String label, String tooltip, String command, NamedTextColor color) {
        return ActionButton.create(
                Component.text(label, color),
                Component.text(tooltip, NamedTextColor.GRAY),
                180,
                DialogAction.commandTemplate(command)
        );
    }
}
