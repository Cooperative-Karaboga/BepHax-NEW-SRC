package bep.hax.capes;

import java.util.List;
import java.util.UUID;

public class CapeData {
    public record BadgeUser(String uuid, String username, List<String> badges) {
        public UUID toUUID() {
            try {
                return UUID.fromString(this.uuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public record Cape(String name, String url, List<CapeData.CapeUser> uuids) {
    }

    public record CapeResponse(boolean success, String token, long tokenExpires, List<CapeData.Cape> capes, List<CapeData.BadgeUser> badges, String error) {
    }

    public record CapeUser(String uuid, String username) {
        public UUID toUUID() {
            try {
                return UUID.fromString(this.uuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
