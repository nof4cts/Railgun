package com.ironlung.client.screen;

import com.ironlung.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The typed report you are handed afterwards. */
public class SummaryScreen extends Screen {
    private final Packets.MissionEnd e;
    private int ticks;

    public SummaryScreen(Packets.MissionEnd e) {
        super(Component.translatable("screen.ironlung.summary"));
        this.e = e;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 60, height / 2 + 92, 120, 20).build());
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, width, height, 0xF0050303);
        int w = 310, h = 190;
        int x = (width - w) / 2, y = (height - h) / 2 - 12;
        g.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xFF5E5444);
        g.fill(x, y, x + w, y + h, 0xFF8E8068);
        int ink = 0xFF2A1E18, red = 0xFF7A1410;
        String outcome = switch (e.outcome) {
            case Packets.MissionEnd.RECOVERED -> "VESSEL RECOVERED";
            case Packets.MissionEnd.ABORTED -> "DIVE ABORTED";
            default -> "CONTACT LOST";
        };
        String sub = switch (e.outcome) {
            case Packets.MissionEnd.RECOVERED -> "Survey accepted. Further dives are scheduled.";
            case Packets.MissionEnd.ABORTED -> "Survey incomplete. The pilot has been recalled.";
            default -> "Hull failure at depth. No recovery is planned.";
        };
        int line = 0;
        String[] lines = {
                String.format(Locale.ROOT, "DURATION            %02d:%02d", e.ticks / 1200, (e.ticks / 20) % 60),
                String.format(Locale.ROOT, "SITES LOGGED        %d / %d", e.objectivesDone, e.objectivesTotal),
                String.format(Locale.ROOT, "EXPOSURES           %d", e.photos),
                String.format(Locale.ROOT, "UNCATALOGUED        %d", e.anomalies),
                String.format(Locale.ROOT, "HULL INTEGRITY      %d%%", Math.round(e.hull * 100)),
                String.format(Locale.ROOT, "COLLISIONS          %d", e.collisions),
                String.format(Locale.ROOT, "POWER FAILURES      %d", e.powerFailures),
                String.format(Locale.ROOT, "UNEXPLAINED         %d", e.sightings),
        };
        g.drawString(font, "DIVE REPORT", x + 12, y + 10, ink, false);
        g.drawString(font, outcome, x + w - 12 - font.width(outcome), y + 10, red, false);
        g.fill(x + 12, y + 22, x + w - 12, y + 23, 0x80301A10);
        int shown = Math.min(lines.length, ticks / 6);
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines[i], x + 16, y + 32 + i * 13, ink, false);
            line++;
        }
        if (ticks > lines.length * 6 + 10) {
            g.drawString(font, sub, x + (w - font.width(sub)) / 2, y + h - 22, red, false);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
