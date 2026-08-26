package bep.hax.profile;

import bep.hax.Bep;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.profiles.Profile;
import meteordevelopment.meteorclient.systems.profiles.Profiles;
import meteordevelopment.orbit.EventHandler;

public class BundledProfile {
    public static final String PROFILE_NAME = "BepHax 2b2t";
    private static final String RESOURCE_DIR = "/assets/bephax/profile/";
    private static final String[] FILES = new String[]{"modules.nbt", "hud.nbt"};
    private static BundledProfile INSTANCE;
    private boolean done;

    public static void init() {
        if (INSTANCE == null) {
            INSTANCE = new BundledProfile();
            MeteorClient.EVENT_BUS.subscribe(INSTANCE);
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (!this.done) {
            this.done = true;
            MeteorClient.EVENT_BUS.unsubscribe(this);

            try {
                this.install();
            } catch (Exception e) {
                Bep.LOG.error("Failed to install bundled profile", e);
            }
        }
    }

    private void install() throws IOException {
        File folder = new File(Profiles.FOLDER, "BepHax 2b2t");
        folder.mkdirs();

        for (String file : FILES) {
            this.extract("/assets/bephax/profile/" + file, new File(folder, file));
        }

        if (Profiles.get().get("BepHax 2b2t") == null) {
            Profile profile = new Profile();
            profile.name.set("BepHax 2b2t");
            profile.modules.set(true);
            profile.hud.set(true);
            profile.macros.set(false);
            profile.waypoints.set(false);
            Profiles.get().getAll().add(profile);
            Profiles.get().save();
            Bep.LOG.info("Registered bundled profile '{}'", "BepHax 2b2t");
        }
    }

    private void extract(String resource, File dest) throws IOException {
        if (!dest.exists()) {
            try (InputStream in = BundledProfile.class.getResourceAsStream(resource)) {
                if (in == null) {
                    Bep.LOG.error("Missing bundled profile resource {}", resource);
                    return;
                }

                Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
