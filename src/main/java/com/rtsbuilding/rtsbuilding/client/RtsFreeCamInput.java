package com.rtsbuilding.rtsbuilding.client;

import net.minecraft.client.player.Input;

/**
 * Dummy input that freezes all player movement keys.
 * Swapped in during RTS mode so WASD only controls the camera,
 * not the player's body.  Pattern taken from Tweakeroo's
 * {@code fi.dy.masa.tweakeroo.util.DummyMovementInput}.
 */
public class RtsFreeCamInput extends Input {

    @Override
    public void tick(final boolean sneaking, final float speedModifier) {
        // NO-OP: zero out every movement flag.
        this.forwardImpulse = 0.0F;
        this.leftImpulse    = 0.0F;
        this.jumping        = false;
        this.shiftKeyDown   = false;
        this.down           = false;
        this.up             = false;
        this.left           = false;
        this.right          = false;
    }
}
