package bep.hax.util.printer;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AirPlaceExecutor {
    private AirPlaceExecutor() {
    }

    public static void place(BlockHitResult hit, BlockPos placePos, InteractionHand hand, BlockState predicted, AirPlaceExecutor.Method method) {
        if (hit.isInside()) {
            airPlace(hit, predicted, method);
        } else {
            silentPlace(hit, placePos, predicted, hand, true);
        }
    }

    public static void airPlace(BlockHitResult hit, BlockState predicted, AirPlaceExecutor.Method method) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null && MeteorClient.mc.getConnection() != null) {
            BlockHitResult wireHit = new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), false);
            if (method == AirPlaceExecutor.Method.Grim) {
                grimAirPlace(wireHit, predicted);
            } else {
                silentPlace(wireHit, hit.getBlockPos(), predicted, InteractionHand.MAIN_HAND, true);
            }
        }
    }

    private static void grimAirPlace(BlockHitResult wireHit, BlockState predicted) {
        ClientPacketListener connection = MeteorClient.mc.getConnection();

        try (BlockStatePredictionHandler prediction = MeteorClient.mc.level.getBlockStatePredictionHandler().startPredicting()) {
            connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            connection.send(new ServerboundUseItemOnPacket(InteractionHand.OFF_HAND, wireHit, prediction.currentSequence()));
            connection.send(new ServerboundSwingPacket(InteractionHand.OFF_HAND));
            connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
            if (predicted != null) {
                MeteorClient.mc.level.setBlock(wireHit.getBlockPos(), predicted, 11);
            }
        }
    }

    public static void silentPlace(BlockHitResult hit, BlockPos placePos, BlockState predicted, InteractionHand hand, boolean visualSwing) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null && MeteorClient.mc.getConnection() != null) {
            ClientPacketListener connection = MeteorClient.mc.getConnection();

            try (BlockStatePredictionHandler prediction = MeteorClient.mc.level.getBlockStatePredictionHandler().startPredicting()) {
                connection.send(new ServerboundUseItemOnPacket(hand, hit, prediction.currentSequence()));
                if (predicted != null) {
                    MeteorClient.mc.level.setBlock(placePos, predicted, 11);
                }
            }

            if (visualSwing && hand == InteractionHand.MAIN_HAND) {
                MeteorClient.mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                connection.send(new ServerboundSwingPacket(hand));
            }
        }
    }

    public enum Method {
        Default,
        Grim;
    }
}
