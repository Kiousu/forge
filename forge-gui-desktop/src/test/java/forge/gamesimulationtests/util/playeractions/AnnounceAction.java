package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

/**
 * Action that lets tests specify a value for "announce" requirements (e.g., X cost, "announce a number").
 */
public class AnnounceAction extends BasePlayerAction {
    private final int value;

    public AnnounceAction(PlayerSpecification player, int value) {
        super(player);
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
