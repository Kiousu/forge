package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

/**
 * Action that lets tests specify new targets for "choose new targets" effects.
 * Target can be specified by card name or player specification.
 */
public class ChooseTargetsAction extends BasePlayerAction {
    private final String targetCardName;
    private final PlayerSpecification targetPlayer;

    /**
     * Target a card by name.
     */
    public ChooseTargetsAction(PlayerSpecification player, String targetCardName) {
        super(player);
        this.targetCardName = targetCardName;
        this.targetPlayer = null;
    }

    /**
     * Target a player.
     */
    public ChooseTargetsAction(PlayerSpecification player, PlayerSpecification targetPlayer) {
        super(player);
        this.targetCardName = null;
        this.targetPlayer = targetPlayer;
    }

    public String getTargetCardName() {
        return targetCardName;
    }

    public PlayerSpecification getTargetPlayer() {
        return targetPlayer;
    }
}
