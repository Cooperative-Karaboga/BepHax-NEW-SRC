package bep.hax.mixin.meteor;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem.Mode;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutoTotem.class, remap = false)
public abstract class AutoTotemMixin extends Module {
    @Shadow
    @Final
    private Setting<Mode> mode;
    @Shadow
    @Final
    private Setting<Integer> delay;
    @Shadow
    @Final
    private Setting<Integer> health;
    @Shadow
    @Final
    private Setting<Boolean> elytra;
    @Shadow
    @Final
    private Setting<Boolean> fall;
    @Shadow
    @Final
    private Setting<Boolean> explosion;
    @Shadow
    public boolean locked;
    @Shadow
    private int totems;
    @Shadow
    private int ticks;
    @Unique
    private Setting<Boolean> bephax$atomicSwap;
    @Unique
    private Setting<Boolean> bephax$popRefill;

    public AutoTotemMixin(Category category, String name, String description) {
        super(category, name, description);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void bephax$init(CallbackInfo ci) {
        SettingGroup sg = this.settings.createGroup("Grim Swap");
        this.bephax$atomicSwap = sg.add(
            new Builder()
                .name("atomic-swap")
                .description("Equips the totem with a single offhand SWAP click instead of the two-click cursor dance. Cursor- and container-screen-safe.")
                .defaultValue(true)
                .build()
        );
        this.bephax$popRefill = sg.add(
            new Builder()
                .name("instant-pop-refill")
                .description("Refills the offhand the moment the pop packet arrives instead of waiting for the next tick cycle.")
                .defaultValue(true)
                .build()
        );
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void bephax$onTick(Pre event, CallbackInfo ci) {
        if (this.bephax$atomicSwap.get()) {
            if (this.mc.player != null && this.mc.level != null) {
                ci.cancel();
                FindItemResult result = InvUtils.find(Items.TOTEM_OF_UNDYING);
                this.totems = result.count();
                if (this.totems <= 0) {
                    this.locked = false;
                } else if (this.ticks >= this.delay.get()) {
                    boolean low = this.mc.player.getHealth()
                            + this.mc.player.getAbsorptionAmount()
                            - PlayerUtils.possibleHealthReductions(this.explosion.get(), this.fall.get())
                        <= this.health.get().intValue();
                    boolean ely = this.elytra.get()
                        && this.mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                        && this.mc.player.isFallFlying();
                    this.locked = this.mode.get() == Mode.Strict || this.mode.get() == Mode.Smart && (low || ely);
                    if (this.locked && this.mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                        this.bephax$offhandSwap(result.slot());
                    }

                    this.ticks = 0;
                    return;
                }

                this.ticks++;
            }
        }
    }

    @Unique
    @EventHandler(priority = 200)
    private void bephax$onPop(Receive event) {
        if (this.isActive() && this.bephax$popRefill.get()) {
            if (event.packet instanceof ClientboundEntityEventPacket p) {
                if (p.getEventId() == 35) {
                    if (this.mc.player != null && this.mc.level != null) {
                        Entity entity = p.getEntity(this.mc.level);
                        if (entity != null && entity.equals(this.mc.player)) {
                            if (this.mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                                this.mc.execute(() -> {
                                    if (this.mc.player != null) {
                                        for (int i = 0; i < 36; i++) {
                                            if (this.mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
                                                this.bephax$offhandSwap(i);
                                                return;
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void bephax$offhandSwap(int invSlot) {
        if (invSlot >= 0 && invSlot <= 35) {
            if (!this.mc.player.isUsingItem() || this.mc.player.getUsedItemHand() != InteractionHand.OFF_HAND) {
                AbstractContainerMenu menu = this.mc.player.containerMenu;
                if (menu.getCarried().isEmpty()) {
                    int slotId = this.bephax$containerSlotId(menu, invSlot);
                    if (slotId >= 0) {
                        this.mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 40, ClickType.SWAP, this.mc.player);
                    }
                }
            }
        }
    }

    @Unique
    private int bephax$containerSlotId(AbstractContainerMenu menu, int invSlot) {
        if (menu instanceof InventoryMenu) {
            return SlotUtils.indexToId(invSlot);
        }

        for (Slot slot : menu.slots) {
            if (slot.container == this.mc.player.getInventory() && slot.getContainerSlot() == invSlot) {
                return slot.index;
            }
        }

        return -1;
    }
}
