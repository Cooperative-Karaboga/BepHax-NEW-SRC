package bep.hax.util.prox;

import bep.hax.util.LogUtil;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class ProxTransport {
    public static final BlockPos[] ALL_OFFSETS = createAllOffsets();
    private static final Map<BlockPos, Integer> OFFSET_LOOKUP = createOffsetLookup();
    private static final int PDU_BITS = Mth.floor(Math.log(ALL_OFFSETS.length) / Math.log(2.0));
    private static final int MAX_USABLE_PDU = 1 << PDU_BITS;
    private static final int[] MAGIC_PDUS = new int[]{ALL_OFFSETS.length - 1, ALL_OFFSETS.length - 19};
    private static final int PACKET_ID_BITS = 6;
    public static final int MAX_PACKET_BYTES = 2048;
    private static final int MAX_DISPATCHES_PER_SECOND = 20;
    private static final long READER_STALE_MS = 3000L;
    private static final int MAX_READERS = 128;
    private static final int MAX_PENDING_BURSTS = 8;
    private static ProxTransport instance;
    private final Map<Integer, List<ProxTransport.Handler>> handlers = new ConcurrentHashMap<>();
    private final Map<Integer, ProxTransport.Reader> readers = new HashMap<>();
    private final ArrayDeque<List<Integer>> pending = new ArrayDeque<>();
    private int cleanupTicks = 0;

    private ProxTransport() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static ProxTransport getInstance() {
        if (instance == null) {
            instance = new ProxTransport();
        }

        return instance;
    }

    public static int pack(int vendorId, int packetId) {
        return vendorId << 6 | packetId;
    }

    public void register(int vendorId, int packetId, ProxTransport.Handler handler) {
        this.handlers.computeIfAbsent(pack(vendorId, packetId), k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void unregister(int vendorId, int packetId, ProxTransport.Handler handler) {
        int packed = pack(vendorId, packetId);
        List<ProxTransport.Handler> list = this.handlers.get(packed);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) {
                this.handlers.remove(packed, list);
            }
        }
    }

    public int send(int vendorId, int packetId, byte[] data) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
            if (data == null || data.length > 2048) {
                return -1;
            }

            if (this.pending.size() >= 8) {
                return -1;
            }

            List<Integer> pdus = encode(vendorId, packetId, data);
            this.pending.add(pdus);
            return pdus.size();
        } else {
            return -1;
        }
    }

    public void flush() {
        if (!this.pending.isEmpty()) {
            if (MeteorClient.mc.player != null && MeteorClient.mc.getConnection() != null) {
                Vec3 eye = MeteorClient.mc.player.getEyePosition();
                BlockPos eyeBase = new BlockPos(
                    Mth.floor(eye.x), Mth.floor(eye.y), Mth.floor(eye.z)
                );

                for (List<Integer> pdus : this.pending) {
                    for (int pdu : pdus) {
                        BlockPos pos = eyeBase.offset(ALL_OFFSETS[pdu]);
                        MeteorClient.mc.player.connection.send(new ServerboundPlayerActionPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
                    }
                }

                this.pending.clear();
            } else {
                this.pending.clear();
            }
        }
    }

    public static int packetCount(int dataLength) {
        int bits = (5 + dataLength) * 8;
        return MAGIC_PDUS.length + (bits + PDU_BITS - 1) / PDU_BITS;
    }

    private static List<Integer> encode(int vendorId, int packetId, byte[] data) {
        int length = 2 + data.length;
        byte[] framed = new byte[5 + data.length];
        framed[0] = (byte)(length >> 16 & 0xFF);
        framed[1] = (byte)(length >> 8 & 0xFF);
        framed[2] = (byte)(length & 0xFF);
        int packed = pack(vendorId, packetId);
        framed[3] = (byte)(packed >> 8 & 0xFF);
        framed[4] = (byte)(packed & 0xFF);
        System.arraycopy(data, 0, framed, 5, data.length);
        ArrayList<Integer> pdus = new ArrayList<>(packetCount(data.length));

        for (int magic : MAGIC_PDUS) {
            pdus.add(magic);
        }

        int current = 0;
        int currentBits = 0;

        for (byte b : framed) {
            for (int i = 0; i < 8; i++) {
                current |= (b >>> i & 1) << currentBits;
                if (++currentBits == PDU_BITS) {
                    pdus.add(current);
                    current = 0;
                    currentBits = 0;
                }
            }
        }

        if (currentBits > 0) {
            pdus.add(current);
        }

        return pdus;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (!this.handlers.isEmpty()) {
            if (event.packet instanceof ClientboundBlockDestructionPacket packet) {
                if (packet.getProgress() == 255) {
                    int senderId = packet.getId();
                    BlockPos pos = packet.getPos();
                    MeteorClient.mc.execute(() -> this.handleAbort(senderId, pos));
                }
            }
        }
    }

    private void handleAbort(int senderId, BlockPos pos) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.level != null) {
            if (senderId != MeteorClient.mc.player.getId()) {
                Entity entity = MeteorClient.mc.level.getEntity(senderId);
                if (entity instanceof Player) {
                    if (this.readers.size() < 128 || this.readers.containsKey(senderId)) {
                        this.readers.computeIfAbsent(senderId, k -> new ProxTransport.Reader()).handle(senderId, pos);
                    }
                }
            }
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (!this.readers.isEmpty()) {
            if (++this.cleanupTicks >= 40) {
                this.cleanupTicks = 0;
                long now = System.currentTimeMillis();
                this.readers.entrySet().removeIf(e -> {
                    if (now - e.getValue().lastReceivedAt > 6000L) {
                        return true;
                    }

                    Entity entity = MeteorClient.mc.level != null ? MeteorClient.mc.level.getEntity(e.getKey()) : null;
                    return !(entity instanceof Player) || entity.isRemoved();
                });
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        this.readers.clear();
        this.pending.clear();
    }

    private void dispatch(int senderId, int packedId, byte[] data) {
        List<ProxTransport.Handler> list = this.handlers.get(packedId);
        if (list != null && !list.isEmpty()) {
            if ((MeteorClient.mc.level != null ? MeteorClient.mc.level.getEntity(senderId) : null) instanceof Player sender) {
                for (ProxTransport.Handler handler : list) {
                    try {
                        handler.onReceived(sender, data);
                    } catch (Exception ex) {
                        LogUtil.error("ProxTransport handler failed: " + ex);
                    }
                }
            }
        }
    }

    private static BlockPos[] createAllOffsets() {
        List<Vec3> origins = new ArrayList<>();

        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    origins.add(new Vec3(x, y, z));
                }
            }
        }

        int[] axis = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6};
        List<BlockPos> offsets = new ArrayList<>();

        for (int x : axis) {
            for (int y : axis) {
                label44:
                for (int z : axis) {
                    BlockPos offset = new BlockPos(x, y, z);
                    Vec3 center = Vec3.atCenterOf(offset);

                    for (Vec3 origin : origins) {
                        if (origin.distanceToSqr(center) > 36.0) {
                            continue label44;
                        }
                    }

                    offsets.add(offset);
                }
            }
        }

        return offsets.toArray(new BlockPos[0]);
    }

    private static Map<BlockPos, Integer> createOffsetLookup() {
        Map<BlockPos, Integer> map = new HashMap<>();

        for (int i = 0; i < ALL_OFFSETS.length; i++) {
            map.put(ALL_OFFSETS[i], i);
        }

        return map;
    }

    public interface Handler {
        void onReceived(Player var1, byte[] var2);
    }

    private class Reader {
        private static final int PHASE_IDLE = 0;
        private static final int PHASE_MAGIC1 = 1;
        private static final int PHASE_DATA = 2;
        private int phase = 0;
        private BlockPos eyeBase = null;
        private long lastReceivedAt = -1L;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int currentByte = 0;
        private int currentBits = 0;
        private int expectedLength = -1;
        private int packedId = -1;
        private int dispatchWindowStart = 0;
        private int dispatchCount = 0;

        void handle(int senderId, BlockPos pos) {
            long now = System.currentTimeMillis();
            if (this.lastReceivedAt != -1L && now - this.lastReceivedAt > 3000L) {
                this.reset();
            }

            this.lastReceivedAt = now;
            if (this.phase == 1) {
                if (this.pduAt(pos) == ProxTransport.MAGIC_PDUS[1]) {
                    this.phase = 2;
                    this.resetData();
                    return;
                }

                this.phase = 0;
            }

            if (this.phase == 0) {
                this.eyeBase = pos.subtract(ProxTransport.ALL_OFFSETS[ProxTransport.MAGIC_PDUS[0]]);
                this.phase = 1;
            } else {
                int pdu = this.pduAt(pos);
                if (pdu != -1) {
                    if (pdu >= ProxTransport.MAX_USABLE_PDU) {
                        this.reset();
                    } else {
                        this.readPdu(pdu);
                        byte[] buf = null;
                        if (this.expectedLength == -1 && this.bytes.size() >= 3) {
                            buf = this.bytes.toByteArray();
                            this.expectedLength = (buf[0] & 255) << 16 | (buf[1] & 255) << 8 | buf[2] & 255;
                            if (this.expectedLength < 2 || this.expectedLength > 2050) {
                                this.reset();
                                return;
                            }
                        }

                        if (this.expectedLength != -1 && this.packedId == -1 && this.bytes.size() >= 5) {
                            if (buf == null) {
                                buf = this.bytes.toByteArray();
                            }

                            this.packedId = (buf[3] & 255) << 8 | buf[4] & 255;
                        }

                        if (this.expectedLength != -1 && this.packedId != -1 && this.bytes.size() >= 3 + this.expectedLength) {
                            if (buf == null) {
                                buf = this.bytes.toByteArray();
                            }

                            byte[] data = Arrays.copyOfRange(buf, 5, 3 + this.expectedLength);
                            int id = this.packedId;
                            this.reset();
                            if (this.allowDispatch()) {
                                ProxTransport.this.dispatch(senderId, id, data);
                            }
                        }
                    }
                }
            }
        }

        private boolean allowDispatch() {
            int second = (int)(System.currentTimeMillis() / 1000L);
            if (second != this.dispatchWindowStart) {
                this.dispatchWindowStart = second;
                this.dispatchCount = 0;
            }

            return ++this.dispatchCount <= 20;
        }

        private void readPdu(int pdu) {
            for (int i = 0; i < ProxTransport.PDU_BITS; i++) {
                this.currentByte = this.currentByte | (pdu >>> i & 1) << this.currentBits;
                if (++this.currentBits == 8) {
                    this.bytes.write(this.currentByte);
                    this.currentByte = 0;
                    this.currentBits = 0;
                }
            }
        }

        private int pduAt(BlockPos pos) {
            if (this.eyeBase == null) {
                return -1;
            }

            Integer index = ProxTransport.OFFSET_LOOKUP.get(pos.subtract(this.eyeBase));
            return index == null ? -1 : index;
        }

        private void resetData() {
            this.bytes.reset();
            this.currentByte = 0;
            this.currentBits = 0;
            this.expectedLength = -1;
            this.packedId = -1;
        }

        private void reset() {
            this.phase = 0;
            this.eyeBase = null;
            this.resetData();
        }
    }
}
