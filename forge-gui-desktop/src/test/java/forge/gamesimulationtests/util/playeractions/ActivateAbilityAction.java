package forge.gamesimulationtests.util.playeractions;

import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.gamesimulationtests.util.card.CardSpecification;
import forge.gamesimulationtests.util.card.CardSpecificationHandler;
import forge.gamesimulationtests.util.player.PlayerSpecification;

public class ActivateAbilityAction extends BasePlayerAction {
	private final CardSpecification cardWithAbility;
	private final String abilityPrefix;

	public ActivateAbilityAction( PlayerSpecification player, CardSpecification cardWithAbility ) {
		super( player );
		this.cardWithAbility = cardWithAbility;
		this.abilityPrefix = null;
	}

	public ActivateAbilityAction( PlayerSpecification player, CardSpecification cardWithAbility, String abilityPrefix ) {
		super( player );
		this.cardWithAbility = cardWithAbility;
		this.abilityPrefix = abilityPrefix;
	}

	public void activateAbility( Player player, Game game ) {
		Card actualCardWithAbility = CardSpecificationHandler.INSTANCE.find( game, cardWithAbility );

		List<SpellAbility> abilities = actualCardWithAbility.getAllPossibleAbilities( player, true );
		if( abilities.isEmpty() ) {
			throw new IllegalStateException( "No abilities found for " + actualCardWithAbility );
		}

		SpellAbility ability;
		if( abilityPrefix != null ) {
			ability = null;
			for( SpellAbility sa : abilities ) {
				String desc = sa.getDescription();
				if( desc != null && desc.startsWith( abilityPrefix ) ) {
					ability = sa;
					break;
				}
			}
			if( ability == null ) {
				throw new IllegalStateException( "No ability starting with '" + abilityPrefix + "' found for " + actualCardWithAbility );
			}
		} else if( abilities.size() == 1 ) {
			ability = abilities.get( 0 );
		} else {
			// Multiple abilities: pick first non-mana ability
			ability = null;
			for( SpellAbility sa : abilities ) {
				if( !sa.isManaAbility() ) {
					ability = sa;
					break;
				}
			}
			if( ability == null ) {
				// All are mana abilities, just use first
				ability = abilities.get( 0 );
			}
		}

		game.getStack().add( ability );
	}
}
