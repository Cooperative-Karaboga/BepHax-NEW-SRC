package bep.hax.mixin;

import bep.hax.accessor.InputAccessor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientInput.class)
public abstract class InputMixin implements InputAccessor {
    @Shadow
    public Vec2 moveVector;
    @Unique
    private float bephax$overrideForward = Float.NaN;
    @Unique
    private float bephax$overrideSideways = Float.NaN;

    @Shadow
    public abstract Vec2 getMoveVector();

    @Override
    public float getMovementForward() {
        if (!Float.isNaN(this.bephax$overrideForward)) {
            return this.bephax$overrideForward;
        } else {
            return this.moveVector != null ? this.moveVector.y : 0.0F;
        }
    }

    @Override
    public void setMovementForward(float value) {
        this.bephax$overrideForward = value;
        this.applyOverrides();
    }

    @Override
    public float getMovementSideways() {
        if (!Float.isNaN(this.bephax$overrideSideways)) {
            return this.bephax$overrideSideways;
        } else {
            return this.moveVector != null ? this.moveVector.x : 0.0F;
        }
    }

    @Override
    public void setMovementSideways(float value) {
        this.bephax$overrideSideways = value;
        this.applyOverrides();
    }

    @Unique
    private void applyOverrides() {
        if (this.moveVector != null) {
            float sideways = Float.isNaN(this.bephax$overrideSideways) ? this.moveVector.x : this.bephax$overrideSideways;
            float forward = Float.isNaN(this.bephax$overrideForward) ? this.moveVector.y : this.bephax$overrideForward;
            if (!Float.isNaN(this.bephax$overrideSideways) && !Float.isNaN(this.bephax$overrideForward)) {
                float length = (float)Math.sqrt(sideways * sideways + forward * forward);
                if (length > 1.0E-4) {
                    sideways /= length;
                    forward /= length;
                }
            }

            this.moveVector = new Vec2(sideways, forward);
            this.bephax$overrideForward = Float.NaN;
            this.bephax$overrideSideways = Float.NaN;
        }
    }
}
