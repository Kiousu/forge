package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

import java.util.Arrays;

/**
 * Action that lets tests specify which modes to choose for a modal ability.
 * Modes can be specified by index (0-based) or by description prefix.
 */
public class ChooseModeAction extends BasePlayerAction {
    private final int[] modeIndices;
    private final String[] modePrefixes;

    /**
     * Choose modes by index (0-based).
     */
    public ChooseModeAction(PlayerSpecification player, int... modeIndices) {
        super(player);
        this.modeIndices = Arrays.copyOf(modeIndices, modeIndices.length);
        this.modePrefixes = null;
    }

    /**
     * Choose modes by matching description prefixes.
     */
    public ChooseModeAction(PlayerSpecification player, String... modePrefixes) {
        super(player);
        this.modeIndices = null;
        this.modePrefixes = Arrays.copyOf(modePrefixes, modePrefixes.length);
    }

    public int[] getModeIndices() {
        return modeIndices;
    }

    public String[] getModePrefixes() {
        return modePrefixes;
    }
}
