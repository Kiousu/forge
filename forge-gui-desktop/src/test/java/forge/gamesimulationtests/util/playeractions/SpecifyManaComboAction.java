package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

import java.util.HashMap;
import java.util.Map;

/**
 * Action that lets tests specify how to split mana production across colors.
 * Maps color bytes (from MagicColor) to amounts.
 */
public class SpecifyManaComboAction extends BasePlayerAction {
    private final Map<Byte, Integer> manaSpec;

    public SpecifyManaComboAction(PlayerSpecification player, Map<Byte, Integer> manaSpec) {
        super(player);
        this.manaSpec = new HashMap<>(manaSpec);
    }

    public Map<Byte, Integer> getManaSpec() {
        return manaSpec;
    }
}
