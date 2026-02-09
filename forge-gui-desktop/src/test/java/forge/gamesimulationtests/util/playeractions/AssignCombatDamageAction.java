package forge.gamesimulationtests.util.playeractions;

import forge.gamesimulationtests.util.player.PlayerSpecification;

import java.util.HashMap;
import java.util.Map;

/**
 * Action that lets tests specify exactly how combat damage should be assigned.
 * Maps card names to damage amounts.
 */
public class AssignCombatDamageAction extends BasePlayerAction {
    private final Map<String, Integer> damageAssignment;

    public AssignCombatDamageAction(PlayerSpecification player, Map<String, Integer> damageAssignment) {
        super(player);
        this.damageAssignment = new HashMap<>(damageAssignment);
    }

    public Map<String, Integer> getDamageAssignment() {
        return damageAssignment;
    }
}
