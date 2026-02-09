package forge.gamesimulationtests.util.playeractions;

import forge.card.MagicColor;
import forge.gamesimulationtests.util.player.PlayerSpecification;

/**
 * Action that lets tests specify which color(s) to choose.
 */
public class ChooseColorAction extends BasePlayerAction {
    private final byte[] colors;

    public ChooseColorAction(PlayerSpecification player, byte... colors) {
        super(player);
        this.colors = colors;
    }

    /**
     * Convenience constructor using MagicColor.Color enums.
     */
    public ChooseColorAction(PlayerSpecification player, MagicColor.Color... colorEnums) {
        super(player);
        this.colors = new byte[colorEnums.length];
        for (int i = 0; i < colorEnums.length; i++) {
            this.colors[i] = colorEnums[i].getColorMask();
        }
    }

    public byte[] getColors() {
        return colors;
    }
}
