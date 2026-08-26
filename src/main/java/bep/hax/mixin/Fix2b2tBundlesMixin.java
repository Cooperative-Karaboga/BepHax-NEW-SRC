package bep.hax.mixin;

import bep.hax.modules.InvFix2b2t;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 900)
public abstract class Fix2b2tBundlesMixin extends ClientCommonPacketListenerImpl {
    protected Fix2b2tBundlesMixin(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    @Unique
    private void bephax$fixBundle(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            InvFix2b2t module = Modules.get().get(InvFix2b2t.class);
            if (module != null && module.isActive() && module.fixBundles.get()) {
                if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
                    stack.get(DataComponents.BUNDLE_CONTENTS).items().forEach(this::bephax$fixBundle);
                } else if (stack.has(DataComponents.CONTAINER)) {
                    stack.get(DataComponents.CONTAINER).stream().forEach(this::bephax$fixBundle);
                }

                if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
                    BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
                    stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(contents.itemCopyStream().toList().reversed()));
                }
            }
        }
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    public void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo info) {
        if (this.minecraft.isSameThread()) {
            packet.items().forEach(this::bephax$fixBundle);
            this.bephax$fixBundle(packet.carriedItem());
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    public void onScreenHandlerSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo info) {
        if (this.minecraft.isSameThread()) {
            this.bephax$fixBundle(packet.getItem());
        }
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("HEAD"))
    public void onSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo info) {
        if (this.minecraft.isSameThread()) {
            this.bephax$fixBundle(packet.contents());
        }
    }

    @Inject(method = "handleSetCursorItem", at = @At("HEAD"))
    public void onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo info) {
        if (this.minecraft.isSameThread()) {
            this.bephax$fixBundle(packet.contents());
        }
    }
}
