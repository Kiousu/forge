package forge.gamesimulationtests.util.playeractions;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.spellability.LandAbility;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamesimulationtests.util.player.PlayerSpecification;

/**
 * Action that lets tests play a land from hand.
 */
public class PlayLandFromHandAction extends BasePlayerAction {
    private final String landName;

    public PlayLandFromHandAction(PlayerSpecification player, String landName) {
        super(player);
        this.landName = landName;
    }

    public SpellAbility playLandFromHand(Player player, Game game) {
        CardCollectionView cardsInHand = player.getCardsIn(ZoneType.Hand);
        Card cardToPlay = null;
        for (Card card : cardsInHand) {
            if (landName.equals(card.getName())) {
                cardToPlay = card;
                break;
            }
        }
        if (cardToPlay == null) {
            throw new IllegalStateException("Couldn't find land " + landName + " in hand");
        }

        for (SpellAbility sa : cardToPlay.getAllPossibleAbilities(player, true)) {
            if (sa instanceof LandAbility) {
                return sa;
            }
        }
        throw new IllegalStateException("No land ability found for " + landName);
    }

    public String getLandName() {
        return landName;
    }
}
