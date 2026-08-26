package bep.hax.modules;

import bep.hax.Bep;
import java.lang.reflect.Field;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;

public class GhostMode extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> fullFood = this.sgGeneral
        .add(new Builder().name("full-food").description("Sets the food level client-side to max.").defaultValue(true).build());
    private final Setting<Boolean> maintainHealth = this.sgGeneral
        .add(new Builder().name("maintain-health").description("Maintains health at a specific value to prevent issues.").defaultValue(true).build());
    private final Setting<Double> healthValue = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("health-value")
                .description("Health value to maintain while in ghost mode.")
                .defaultValue(20.0)
                .min(1.0)
                .max(20.0)
                .sliderMin(1.0)
                .sliderMax(20.0)
                .visible(this.maintainHealth::get)
                .build()
        );
    private final Setting<Boolean> blockDeathPackets = this.sgGeneral
        .add(new Builder().name("block-death-packets").description("Blocks death-related packets from the server.").defaultValue(false).build());
    private boolean active = false;

    public GhostMode() {
        super(Bep.CATEGORY, "ghost-mode", "Allows you to keep playing after you die. Works on Forge, Fabric and Vanilla servers.");
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        this.active = false;
        this.warning("You are no longer in a ghost mode!");
        if (this.mc.player != null && this.mc.player.connection != null) {
            this.mc.player.respawn();
            this.info("Respawn request has been sent to the server.");
        }
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        this.active = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.active) {
            float targetHealth = this.maintainHealth.get() ? this.healthValue.get().floatValue() : 1.0F;
            if (this.mc.player.getHealth() <= 0.0F || this.maintainHealth.get() && this.mc.player.getHealth() != targetHealth) {
                this.mc.player.setHealth(targetHealth);
            }

            if (this.mc.player.deathTime > 0) {
                this.mc.player.deathTime = 0;
            }

            if (this.fullFood.get() && this.mc.player.getFoodData().getFoodLevel() < 20) {
                this.mc.player.getFoodData().setFoodLevel(20);
            }

            if (this.mc.player.getAbilities().flying && !this.mc.player.getAbilities().mayfly) {
                this.mc.player.getAbilities().flying = false;
            }
        }
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof DeathScreen) {
            event.cancel();
            if (!this.active) {
                this.active = true;
                this.info("You are now in ghost mode. Toggle off to respawn.");
            }
        }
    }

    @EventHandler
    private void onReceivePacket(Receive event) {
        if (this.active) {
            if (this.blockDeathPackets.get() && event.packet instanceof ClientboundSetHealthPacket packet) {
                try {
                    Field healthField = packet.getClass().getDeclaredField("health");
                    healthField.setAccessible(true);
                    float health = healthField.getFloat(packet);
                    if (health <= 0.0F) {
                        event.cancel();
                        if (this.mc.player != null) {
                            this.mc.player.setHealth(this.maintainHealth.get() ? this.healthValue.get().floatValue() : 1.0F);
                        }
                    }
                } catch (Exception var5) {
                }
            }

            if (this.blockDeathPackets.get() && event.packet instanceof ClientboundPlayerCombatKillPacket) {
                event.cancel();
            }
        }
    }
}
