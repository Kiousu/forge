package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

import java.util.Arrays;
import java.util.List;

/**
 * Action that lets tests specify which cards to choose by name.
 * Used for discard, zone change, and other card selection effects.
 */
public class ChooseCardsAction extends BasePlayerAction {
    private final List<String> cardNames;

    public ChooseCardsAction(PlayerSpecification player, String... cardNames) {
        super(player);
        this.cardNames = Arrays.asList(cardNames);
    }

    public ChooseCardsAction(PlayerSpecification player, List<String> cardNames) {
        super(player);
        this.cardNames = cardNames;
    }

    public List<String> getCardNames() {
        return cardNames;
    }
}
