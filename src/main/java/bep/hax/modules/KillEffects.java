package bep.hax.modules;

import bep.hax.Bep;
import bep.hax.util.LogUtil;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class KillEffects extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<KillEffects.EffectType> effectType = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)new Builder().name("effect-type")).description("The type of kill effect to display."))
                    .defaultValue(KillEffects.EffectType.PARTICLE))
                .build()
        );
    private final Setting<KillEffects.EntityEffectType> entityEffect = this.sgGeneral
        .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("entity-effect")).description("The entity effect to spawn."))
                        .defaultValue(KillEffects.EntityEffectType.LIGHTNING_BOLT))
                    .visible(() -> this.effectType.get() == KillEffects.EffectType.ENTITY))
                .build()
        );
    private final Setting<Integer> entityAmount = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("entity-amount")
                .description("Number of entities to spawn.")
                .defaultValue(1)
                .range(1, 5)
                .sliderRange(1, 5)
                .visible(() -> this.effectType.get() == KillEffects.EffectType.ENTITY)
                .build()
        );
    private final Setting<List<ParticleType<?>>> particleTypes = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.ParticleTypeListSetting.Builder()
                .name("particle-types")
                .description("Types of particles to spawn.")
                .defaultValue(ParticleTypes.EXPLOSION)
                .visible(() -> this.effectType.get() == KillEffects.EffectType.PARTICLE)
                .build()
        );
    private final Setting<Integer> particleAmount = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("particle-amount")
                .description("Number of particles to spawn.")
                .defaultValue(50)
                .range(10, 100)
                .sliderRange(10, 100)
                .visible(() -> this.effectType.get() == KillEffects.EffectType.PARTICLE)
                .build()
        );
    private final Setting<List<SoundEvent>> soundEvents = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.SoundEventListSetting.Builder()
                .name("sound-events")
                .description("Sound to play on kill. Only the first sound in the list is used.")
                .defaultValue(SoundEvents.LIGHTNING_BOLT_THUNDER)
                .visible(() -> this.effectType.get() == KillEffects.EffectType.PARTICLE)
                .build()
        );
    private final Setting<Integer> soundVolume = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("sound-volume")
                .description("Volume of the sound.")
                .defaultValue(100)
                .range(0, 200)
                .sliderRange(0, 200)
                .visible(() -> this.effectType.get() == KillEffects.EffectType.PARTICLE)
                .build()
        );
    private final Setting<Integer> maxDistance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("max-distance")
                .description("Maximum distance to trigger effects (blocks).")
                .defaultValue(128)
                .min(16)
                .max(256)
                .sliderRange(16, 256)
                .build()
        );
    private final Setting<Integer> cooldown = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("cooldown")
                .description("Minimum time between effects (milliseconds).")
                .defaultValue(100)
                .min(0)
                .max(500)
                .sliderRange(0, 500)
                .build()
        );
    private final SettingGroup sgEntityFilter = this.settings.createGroup("Entity Filter");
    private final Setting<Boolean> players = this.sgEntityFilter
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("players")
                .description("Trigger effects on player deaths.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> hostileMobs = this.sgEntityFilter
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("hostile-mobs")
                .description("Trigger effects on hostile mob deaths.")
                .defaultValue(true)
                .build()
        );
    private final Setting<Boolean> passiveMobs = this.sgEntityFilter
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("passive-mobs")
                .description("Trigger effects on passive mob deaths.")
                .defaultValue(true)
                .build()
        );
    private long lastEffectTime = 0L;

    public KillEffects() {
        super(Bep.CATEGORY, "kill-effects", "Displays effects when entities die.");
    }

    @Override
    public void onActivate() {
        this.lastEffectTime = 0L;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (event.packet instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 3) {
                try {
                    Entity entity = packet.getEntity(this.mc.level);
                    if (entity != null && entity != this.mc.player && this.isValidEntity(entity)) {
                        double distance = this.mc.player.distanceToSqr(entity);
                        int maxDist = this.maxDistance.get();
                        if (distance > maxDist * maxDist) {
                            return;
                        }

                        long currentTime = System.currentTimeMillis();
                        if (currentTime - this.lastEffectTime < this.cooldown.get().intValue()) {
                            return;
                        }

                        this.lastEffectTime = currentTime;
                        this.mc.execute(() -> this.triggerKillEffect(entity));
                    }
                } catch (Exception e) {
                    LogUtil.error("KillEffects packet error: " + e.getMessage());
                }
            }
        }
    }

    private void triggerKillEffect(Entity entity) {
        Vec3 pos = entity.position();
        switch ((KillEffects.EffectType)this.effectType.get()) {
            case ENTITY:
                this.spawnEntityEffect(pos);
                break;
            case PARTICLE:
                this.spawnParticleEffect(pos);
        }
    }

    private void spawnEntityEffect(Vec3 pos) {
        if (this.mc.level != null) {
            switch ((KillEffects.EntityEffectType)this.entityEffect.get()) {
                case LIGHTNING_BOLT:
                    for (int i = 0; i < this.entityAmount.get(); i++) {
                        this.spawnRealLightning(pos);
                    }
            }
        }
    }

    private void spawnRealLightning(Vec3 pos) {
        if (this.mc.level != null) {
            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, this.mc.level);
            lightning.snapTo(pos.x, pos.y - 0.5, pos.z);
            lightning.setVisualOnly(true);
            this.mc.level.addEntity(lightning);
        }
    }

    private void spawnParticleEffect(Vec3 pos) {
        if (this.mc.level != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<ParticleType<?>> selectedParticles = this.particleTypes.get();
            if (!selectedParticles.isEmpty()) {
                for (int i = 0; i < this.particleAmount.get(); i++) {
                    ParticleType<?> particleType = selectedParticles.get(i % selectedParticles.size());
                    double offsetX = (random.nextDouble() - 0.5) * 4.0;
                    double offsetY = random.nextDouble() * 2.0;
                    double offsetZ = (random.nextDouble() - 0.5) * 4.0;
                    if (particleType instanceof ParticleOptions particleEffect) {
                        this.mc
                            .level
                            .doAddParticle(
                                particleEffect,
                                false,
                                false,
                                pos.x + offsetX,
                                pos.y + offsetY,
                                pos.z + offsetZ,
                                (random.nextDouble() - 0.5) * 0.5,
                                random.nextDouble() * 0.5,
                                (random.nextDouble() - 0.5) * 0.5
                            );
                    }
                }
            }

            List<SoundEvent> selectedSounds = this.soundEvents.get();
            if (!selectedSounds.isEmpty()) {
                float volume = this.soundVolume.get().intValue() / 100.0F;
                SoundEvent sound = selectedSounds.get(0);
                this.mc
                    .level
                    .playSound(this.mc.player, pos.x, pos.y, pos.z, sound, SoundSource.AMBIENT, volume, 1.0F);
            }
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (entity == null) {
            return false;
        }

        try {
            if (entity instanceof Player) {
                return this.players.get();
            } else if (entity instanceof Monster) {
                return this.hostileMobs.get();
            } else {
                return entity instanceof AgeableMob ? this.passiveMobs.get() : false;
            }
        } catch (Exception e) {
            LogUtil.error("KillEffects entity check error: " + e.getMessage());
            return false;
        }
    }

    public enum EffectType {
        ENTITY("Entity"),
        PARTICLE("Particle");

        private final String title;

        EffectType(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }

    public enum EntityEffectType {
        LIGHTNING_BOLT("Lightning Bolt");

        private final String title;

        EntityEffectType(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return this.title;
        }
    }
}
