package bep.hax.mixin.meteor;

import bep.hax.config.BepConfig;
import bep.hax.mixin.accessor.DisconnectS2CPacketAccessor;
import bep.hax.util.LogUtil;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoLog;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoLog.class, remap = false)
public abstract class AutoLogMixin extends Module {
    @Shadow
    @Final
    private SettingGroup sgGeneral;
    @Shadow
    @Final
    private Setting<Boolean> toggleOff;
    @Shadow
    @Final
    private Setting<Boolean> smartToggle;
    @Unique
    private boolean didLog = false;
    @Unique
    private long requestedDcAt = 0L;
    @Unique
    @Nullable
    private Object bephaxDcNetHandler = null;
    @Unique
    @Nullable
    private Component disconnectReason = null;
    @Unique
    @Nullable
    private Setting<Boolean> forceKick = null;
    @Unique
    @Nullable
    private SettingGroup bephaxGroup = null;
    @Unique
    @Nullable
    private Setting<Boolean> logOnY = null;
    @Unique
    @Nullable
    private Setting<Double> yLevel = null;
    @Unique
    @Nullable
    private Setting<Boolean> logArmor = null;
    @Unique
    @Nullable
    private Setting<Boolean> ignoreElytra = null;
    @Unique
    @Nullable
    private Setting<Double> armorPercent = null;
    @Unique
    @Nullable
    private Setting<Boolean> logElytraCount = null;
    @Unique
    @Nullable
    private Setting<Integer> elytraDurabilityPercent = null;
    @Unique
    @Nullable
    private Setting<Integer> minElytras = null;
    @Unique
    @Nullable
    private Setting<Boolean> logPortal = null;
    @Unique
    @Nullable
    private Setting<Integer> portalTicks = null;
    @Unique
    @Nullable
    private Setting<Boolean> logPosition = null;
    @Unique
    @Nullable
    private Setting<BlockPos> position = null;
    @Unique
    @Nullable
    private Setting<Double> distance = null;
    @Unique
    @Nullable
    private Setting<Boolean> serverNotResponding = null;
    @Unique
    @Nullable
    private Setting<Double> serverNotRespondingSecs = null;
    @Unique
    @Nullable
    private Setting<Boolean> reconnectAfterNotResponding = null;
    @Unique
    @Nullable
    private Setting<Double> secondsToReconnect = null;
    @Unique
    private int currPortalTicks = 0;
    @Unique
    private static final int BEPHAX_ARM_TICKS = 30;
    @Unique
    private int bephaxArmTicks = 0;
    @Unique
    private boolean bephaxHealthSynced = false;
    @Unique
    @Nullable
    private Object bephaxLastPlayer = null;
    @Unique
    private double oldDelay;
    @Unique
    private boolean autoReconnectEnabled;
    @Unique
    private boolean waitingForReconnection = false;

    public AutoLogMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Override
    public void onActivate() {
        super.onActivate();
        this.currPortalTicks = 0;
        this.bephaxArmTicks = 30;
        this.bephaxHealthSynced = false;
        this.bephaxLastPlayer = this.mc.player;
        this.didLog = false;
        this.disconnectReason = null;
        this.requestedDcAt = 0L;
        this.bephaxDcNetHandler = null;
        if (this.waitingForReconnection) {
            this.waitingForReconnection = false;
            AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
            if (autoReconnect != null) {
                Setting<Double> delay = (Setting<Double>)autoReconnect.settings.get("delay");
                if (delay != null) {
                    delay.set(this.oldDelay);
                }

                if (!this.autoReconnectEnabled && autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (this.toggleOff.get() || this.smartToggle.get() && this.didLog) {
            MeteorClient.EVENT_BUS.subscribe(this);
        }
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            target = "Lmeteordevelopment/meteorclient/systems/modules/combat/AutoLog;entities:Lmeteordevelopment/meteorclient/settings/Setting;"
        )
    )
    private void addIllegalDisconnectSetting(CallbackInfo ci) {
        this.forceKick = this.sgGeneral
            .add(
                new Builder()
                    .name("illegal-disconnect")
                    .description("Send an illegal chat message to force the server to kick you (useful for 2b2t).")
                    .defaultValue(true)
                    .build()
            );
        this.bephaxGroup = this.settings.createGroup("BepHax Extended");
        this.logOnY = this.bephaxGroup
            .add(new Builder().name("log-on-y").description("Logs out if you are below a certain Y level.").defaultValue(false).build());
        this.yLevel = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("y-level")
                    .description("Auto log out if below this Y level.")
                    .defaultValue(256.0)
                    .min(-128.0)
                    .sliderRange(-128.0, 320.0)
                    .visible(this.logOnY::get)
                    .build()
            );
        this.logArmor = this.bephaxGroup
            .add(new Builder().name("log-armor").description("Logs out if you have low armor durability.").defaultValue(false).build());
        this.ignoreElytra = this.bephaxGroup
            .add(
                new Builder()
                    .name("ignore-elytra")
                    .description("Ignores the elytra when checking for armor.")
                    .defaultValue(false)
                    .visible(this.logArmor::get)
                    .build()
            );
        this.armorPercent = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("armor-percent")
                    .description("Auto log out if armor durability is below this percent.")
                    .defaultValue(5.0)
                    .min(0.0)
                    .sliderRange(0.0, 100.0)
                    .visible(this.logArmor::get)
                    .build()
            );
        this.logElytraCount = this.bephaxGroup
            .add(
                new Builder()
                    .name("log-elytra-count")
                    .description(
                        "Logs out if you have fewer than the minimum number of valid elytras in your inventory (uses the same durability check as AutoRegear)."
                    )
                    .defaultValue(false)
                    .build()
            );
        this.elytraDurabilityPercent = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("elytra-durability-%")
                    .description("Durability percentage threshold for an elytra to be considered valid.")
                    .defaultValue(30)
                    .min(1)
                    .max(99)
                    .sliderRange(1, 99)
                    .visible(this.logElytraCount::get)
                    .build()
            );
        this.minElytras = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("min-elytras")
                    .description("Auto log out if the number of valid elytras in your inventory drops below this amount.")
                    .defaultValue(2)
                    .min(1)
                    .max(20)
                    .sliderRange(1, 10)
                    .visible(this.logElytraCount::get)
                    .build()
            );
        this.logPortal = this.bephaxGroup
            .add(new Builder().name("log-on-portal").description("Logs out if you are in a portal for too long.").defaultValue(false).build());
        this.portalTicks = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                    .name("portal-ticks")
                    .description("The amount of ticks in a portal before you log out (80 ticks to go through a portal).")
                    .defaultValue(30)
                    .min(1)
                    .sliderMax(70)
                    .visible(this.logPortal::get)
                    .build()
            );
        this.logPosition = this.bephaxGroup
            .add(
                new Builder()
                    .name("log-position")
                    .description("Logs out if you are within x blocks of this position. Y position is not included.")
                    .defaultValue(false)
                    .build()
            );
        this.position = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.BlockPosSetting.Builder()
                    .name("position")
                    .description("The position to log out at. Y position is ignored.")
                    .defaultValue(new BlockPos(0, 0, 0))
                    .visible(this.logPosition::get)
                    .build()
            );
        this.distance = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("distance")
                    .description("The distance from the position to log out at.")
                    .defaultValue(100.0)
                    .sliderRange(0.0, 1000.0)
                    .visible(this.logPosition::get)
                    .build()
            );
        this.serverNotResponding = this.bephaxGroup
            .add(new Builder().name("server-not-responding").description("Logs out if the server is not responding.").defaultValue(false).build());
        this.serverNotRespondingSecs = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("not-responding-seconds")
                    .description("The amount of seconds the server is not responding before you log out.")
                    .defaultValue(10.0)
                    .min(1.0)
                    .sliderMax(60.0)
                    .visible(this.serverNotResponding::get)
                    .build()
            );
        this.reconnectAfterNotResponding = this.bephaxGroup
            .add(
                new Builder()
                    .name("reconnect-after-not-responding")
                    .description("Reconnects after the server is not responding.")
                    .defaultValue(false)
                    .visible(this.serverNotResponding::get)
                    .build()
            );
        this.secondsToReconnect = this.bephaxGroup
            .add(
                new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                    .name("reconnect-delay")
                    .description("The amount of seconds to wait before reconnecting (Will temporarily overwrite Meteor's AutoReconnect).")
                    .defaultValue(60.0)
                    .min(10.0)
                    .sliderMax(300.0)
                    .visible(() -> this.reconnectAfterNotResponding.get() && this.serverNotResponding.get())
                    .build()
            );
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void mixinOnTick(CallbackInfo ci) {
        if (!Utils.canUpdate() || !this.isActive()) {
            ci.cancel();
        }

        if (this.mc.player != this.bephaxLastPlayer) {
            this.bephaxLastPlayer = this.mc.player;
            this.bephaxArmTicks = 30;
            this.bephaxHealthSynced = false;
            this.didLog = false;
            this.disconnectReason = null;
            this.requestedDcAt = 0L;
            this.bephaxDcNetHandler = null;
        }

        if (this.didLog && System.currentTimeMillis() - this.requestedDcAt >= 50L) {
            if (this.mc.getConnection() != null && this.mc.getConnection() == this.bephaxDcNetHandler) {
                LogUtil.warn(
                    "Detected illegal disconnect failure, falling back on regular disconnect (try adjusting your illegal disconnect method config setting)."
                );
                this.mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(this.disconnectReason));
            }

            this.disconnectReason = null;
            this.didLog = false;
            this.requestedDcAt = 0L;
            this.bephaxDcNetHandler = null;
        }

        if (this.mc.player != null && !this.bephaxHealthSynced && this.bephaxArmTicks > 0) {
            this.bephaxArmTicks--;
            ci.cancel();
        }
    }

    @EventHandler
    private void onTickBephaxExtended(Post event) {
        if (this.mc.player != null && !this.mc.player.getAbilities().mayfly) {
            if (this.bephaxHealthSynced || this.bephaxArmTicks <= 0) {
                if (this.serverNotResponding != null
                    && this.serverNotResponding.get()
                    && !this.waitingForReconnection
                    && TickRate.INSTANCE.getTimeSinceLastTick() > this.serverNotRespondingSecs.get()) {
                    if (this.reconnectAfterNotResponding != null && this.reconnectAfterNotResponding.get()) {
                        AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                        if (autoReconnect != null) {
                            this.autoReconnectEnabled = autoReconnect.isActive();
                            Setting<Double> delay = (Setting<Double>)autoReconnect.settings.get("delay");
                            if (delay != null) {
                                this.oldDelay = delay.get();
                                delay.set(this.secondsToReconnect.get());
                            }

                            if (!this.autoReconnectEnabled) {
                                autoReconnect.toggle();
                            }

                            this.waitingForReconnection = true;
                        }
                    }

                    this.bephaxDisconnect(
                        "Server was not responding for " + this.serverNotRespondingSecs.get() + " seconds.",
                        this.reconnectAfterNotResponding == null || !this.reconnectAfterNotResponding.get()
                    );
                } else {
                    if (this.logPortal != null && this.logPortal.get() && this.mc.player.portalProcess != null) {
                        if (this.mc.player.portalProcess.isInsidePortalThisTick()) {
                            this.currPortalTicks++;
                            if (this.portalTicks != null && this.currPortalTicks > this.portalTicks.get()) {
                                this.bephaxDisconnect("Player was in a portal for " + this.currPortalTicks + " ticks.", true);
                                return;
                            }
                        } else {
                            this.currPortalTicks = 0;
                        }
                    }

                    if (this.logOnY != null && this.logOnY.get() && this.yLevel != null && this.mc.player.getY() < this.yLevel.get()) {
                        this.bephaxDisconnect(
                            "Player was at Y=" + this.mc.player.getY() + " which is below your limit of Y=" + this.yLevel.get(), true
                        );
                    } else {
                        if (this.logArmor != null && this.logArmor.get() && this.armorPercent != null) {
                            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                                ItemStack armorPiece = this.mc.player.getItemBySlot(slot);
                                if ((this.ignoreElytra == null || !this.ignoreElytra.get() || armorPiece.getItem() != Items.ELYTRA)
                                    && armorPiece.isDamageableItem()) {
                                    int max = armorPiece.getMaxDamage();
                                    int current = armorPiece.getDamageValue();
                                    double percentUndamaged = 100.0 - (double)current / max * 100.0;
                                    if (percentUndamaged < this.armorPercent.get()) {
                                        this.bephaxDisconnect("You had low armor", true);
                                        return;
                                    }
                                }
                            }
                        }

                        if (this.logElytraCount != null && this.logElytraCount.get() && this.minElytras != null) {
                            int validElytras = this.bephaxCountValidElytras();
                            if (validElytras < this.minElytras.get()) {
                                this.bephaxDisconnect(
                                    "You had only " + validElytras + " valid elytra(s), below your minimum of " + this.minElytras.get() + ".", true
                                );
                                return;
                            }
                        }

                        if (this.logPosition != null && this.logPosition.get() && this.position != null && this.distance != null) {
                            Vec3 playerPos = new Vec3(this.mc.player.getX(), 0.0, this.mc.player.getZ());
                            Vec3 targetPos = new Vec3(this.position.get().getX(), 0.0, this.position.get().getZ());
                            double distanceToTarget = playerPos.distanceTo(targetPos);
                            if (distanceToTarget < this.distance.get()) {
                                this.bephaxDisconnect("Player was within " + distanceToTarget + " blocks of the target position.", true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    private int bephaxCountValidElytras() {
        if (this.mc.player != null && this.elytraDurabilityPercent != null) {
            int count = 0;

            for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
                ItemStack stack = this.mc.player.getInventory().getItem(i);
                if (stack.getItem() == Items.ELYTRA) {
                    int maxDurability = stack.getMaxDamage();
                    int currentDurability = maxDurability - stack.getDamageValue();
                    double percent = (double)currentDurability / maxDurability * 100.0;
                    if (percent >= this.elytraDurabilityPercent.get().intValue()) {
                        count++;
                    }
                }
            }

            return count;
        } else {
            return 0;
        }
    }

    @Unique
    private void bephaxDisconnect(String reason, boolean turnOffReconnect) {
        if (this.mc.player != null) {
            if (turnOffReconnect) {
                AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                if (autoReconnect != null && autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }
            }

            if (this.forceKick != null && this.forceKick.get()) {
                this.didLog = true;
                this.requestedDcAt = System.currentTimeMillis();
                this.bephaxDcNetHandler = this.mc.getConnection();
                this.disconnectReason = Component.literal("§8[§a§oAutoLog§8] §f" + reason);
                bep.hax.util.Utils.illegalDisconnect(true, BepConfig.illegalDisconnectMethodSetting.get());
                if (this.mc.getConnection() != null) {
                    this.mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(this.disconnectReason));
                }
            } else {
                this.mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal("[AutoLog] " + reason)));
            }

            if (this.toggleOff.get() && this.isActive()) {
                this.toggle();
            }
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true, remap = true)
    private void maybeIllegalDisconnect(Component reason, CallbackInfo ci) {
        if (this.forceKick != null && this.forceKick.get()) {
            ci.cancel();
            this.didLog = true;
            this.requestedDcAt = System.currentTimeMillis();
            this.bephaxDcNetHandler = this.mc.getConnection();
            this.disconnectReason = Component.literal("§8[§a§oAutoLog§8] §f" + reason.getString());
            bep.hax.util.Utils.illegalDisconnect(true, BepConfig.illegalDisconnectMethodSetting.get());
            if (this.mc.getConnection() != null) {
                this.mc.getConnection().handleDisconnect(new ClientboundDisconnectPacket(this.disconnectReason));
            }
        }
    }

    @Unique
    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundSetHealthPacket) {
            this.bephaxHealthSynced = true;
        }

        if (this.disconnectReason != null && event.packet instanceof ClientboundDisconnectPacket packet) {
            if (this.didLog) {
                ((DisconnectS2CPacketAccessor)(Object)packet).setReason(this.disconnectReason);
                if (!this.isActive()) {
                    MeteorClient.EVENT_BUS.unsubscribe(this);
                }

                this.disconnectReason = null;
                this.didLog = false;
                this.bephaxDcNetHandler = null;
            }
        }
    }
}
