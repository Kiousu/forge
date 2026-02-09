package forge.gamesimulationtests;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

public class ExileOnMovedTest extends UIPathTest {

    // --- Outpost Siege (Khans) helpers ---

    private Card setupOutpostSiegeKhansAndExileTopCard(Game game, Player p, String topCardName) {
        addCardToZone(topCardName, p, ZoneType.Library);
        Card outpostSiege = addCardToZone("Outpost Siege", p, ZoneType.Battlefield);
        outpostSiege.setChosenMode("Khans");

        game.getAction().checkStateEffects(true);
        game.getPhaseHandler().devModeSet(PhaseType.UNTAP, p);
        game.getPhaseHandler().devAdvanceToPhase(PhaseType.UPKEEP);
        playUntilStackClear(game);
        game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN1);

        AssertJUnit.assertEquals(1, p.getCardsIn(ZoneType.Exile).size());
        Card exiledCard = p.getCardsIn(ZoneType.Exile).get(0);
        AssertJUnit.assertEquals(topCardName, exiledCard.getName());
        AssertJUnit.assertFalse(exiledCard.mayPlay(p).isEmpty());
        return exiledCard;
    }

    // --- Outpost Siege standalone tests ---

    @Test
    public void testOutpostSiegeKhansCanCastExiledCard() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        Card exiledCard = setupOutpostSiegeKhansAndExileTopCard(game, p, "Hill Giant");
        AssertJUnit.assertTrue(castSpell(p, exiledCard.getFirstSpellAbility()));
        playUntilStackClear(game);

        AssertJUnit.assertEquals(1, countCardsWithName(game, "Hill Giant", ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, countCardsWithName(game, "Hill Giant", ZoneType.Exile));
    }

    @Test
    public void testOutpostSiegeKhansCardRemainsCastableAfterOtherSpell() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);

        Card exiledCard = setupOutpostSiegeKhansAndExileTopCard(game, p, "Hill Giant");
        Card shock = addCardToZone("Shock", p, ZoneType.Hand);
        SpellAbility shockSa = shock.getFirstSpellAbility();
        shockSa.getTargets().add(opp);
        AssertJUnit.assertTrue(castSpell(p, shockSa));
        playUntilStackClear(game);

        AssertJUnit.assertFalse("Exiled card should still be castable after another spell.",
                exiledCard.mayPlay(p).isEmpty());
        AssertJUnit.assertTrue(castSpell(p, exiledCard.getFirstSpellAbility()));
        playUntilStackClear(game);
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Hill Giant", ZoneType.Battlefield));
        AssertJUnit.assertEquals(0, countCardsWithName(game, "Hill Giant", ZoneType.Exile));
    }

    @Test
    public void testOutpostSiegeKhansPermissionExpiresEndOfTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        Card exiledCard = setupOutpostSiegeKhansAndExileTopCard(game, p, "Hill Giant");
        AssertJUnit.assertFalse(exiledCard.mayPlay(p).isEmpty());

        playUntilNextTurn(game);

        AssertJUnit.assertTrue(exiledCard.isInZone(ZoneType.Exile));
        AssertJUnit.assertTrue(exiledCard.mayPlay(p).isEmpty());
    }

    // --- Shared helpers for ExileOnMoved tests ---

    private boolean isEffectCardFromSource(Card c, String sourceName) {
        Card source = c.getEffectSource();
        return c.isImmutable() && source != null && sourceName.equals(source.getName());
    }

    private int countEffectCardsFromSource(Player p, ZoneType zoneType, String sourceName) {
        int count = 0;
        for (Card c : p.getCardsIn(zoneType)) {
            if (isEffectCardFromSource(c, sourceName)) {
                count++;
            }
        }
        return count;
    }

    private Card findSingleEffectCardFromSource(Player p, ZoneType zoneType, String sourceName) {
        Card found = null;
        for (Card c : p.getCardsIn(zoneType)) {
            if (!isEffectCardFromSource(c, sourceName)) {
                continue;
            }
            if (found != null) {
                AssertJUnit.fail("Expected a single effect card from " + sourceName + " in " + zoneType);
            }
            found = c;
        }
        return found;
    }

    private Card getSingleRememberedCard(Card effect, String sourceName) {
        Card found = null;
        for (Object remembered : effect.getRemembered()) {
            if (!(remembered instanceof Card)) {
                continue;
            }
            if (found != null) {
                AssertJUnit.fail("Expected a single remembered card on effect from " + sourceName);
            }
            found = (Card) remembered;
        }
        AssertJUnit.assertNotNull("No remembered card found on effect from " + sourceName, found);
        return found;
    }

    private Card findSingleCardByName(Player p, ZoneType zoneType, String cardName) {
        Card found = null;
        for (Card c : p.getCardsIn(zoneType)) {
            if (!cardName.equals(c.getName())) {
                continue;
            }
            if (found != null) {
                AssertJUnit.fail("Expected a single " + cardName + " in " + zoneType);
            }
            found = c;
        }
        AssertJUnit.assertNotNull("Expected to find " + cardName + " in " + zoneType, found);
        return found;
    }

    private SpellAbility findAbilityWithExileOnMoved(Card c, String originSpec) {
        for (SpellAbility sa : c.getSpellAbilities()) {
            if (sa.hasParam("ExileOnMoved") && originSpec.equals(sa.getParam("ExileOnMoved"))) {
                return sa;
            }
        }
        return null;
    }

    private void castSpellWithCardTarget(Game game, Player p, Card spellCard, Card target) {
        SpellAbility sa = spellCard.getFirstSpellAbility();
        sa.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);
    }

    private Card findCardByIdInZone(Player p, ZoneType zoneType, int cardId) {
        for (Card c : p.getCardsIn(zoneType)) {
            if (c.getId() == cardId) {
                return c;
            }
        }
        return null;
    }

    // --- Outpost Siege ExileOnMoved ---

    @Test
    public void testOutpostSiegeCastingExiledCardCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);

        Card exiledCard = setupOutpostSiegeKhansAndExileTopCard(game, p, "Hill Giant");
        Card effect = findSingleEffectCardFromSource(p, ZoneType.Command, "Outpost Siege");
        AssertJUnit.assertNotNull("Missing Outpost Siege effect card in command zone", effect);
        AssertJUnit.assertEquals("Hill Giant", getSingleRememberedCard(effect, "Outpost Siege").getName());

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Outpost Siege");
        AssertJUnit.assertTrue(castSpell(p, exiledCard.getFirstSpellAbility()));
        playUntilStackClear(game);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Outpost Siege");

        AssertJUnit.assertTrue("Casting exiled card should clean up the effect.",
                effectsAfter < effectsBefore);
    }

    @Test
    public void testOutpostSiegeUnrelatedSpellDoesNotCleanUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);

        setupOutpostSiegeKhansAndExileTopCard(game, p, "Hill Giant");
        Card effect = findSingleEffectCardFromSource(p, ZoneType.Command, "Outpost Siege");
        AssertJUnit.assertNotNull(effect);

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Outpost Siege");
        Card shock = addCardToZone("Shock", p, ZoneType.Hand);
        SpellAbility shockSa = shock.getFirstSpellAbility();
        shockSa.getTargets().add(opp);
        AssertJUnit.assertTrue(castSpell(p, shockSa));
        playUntilStackClear(game);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Outpost Siege");

        AssertJUnit.assertEquals("Unrelated spell should not affect Outpost Siege effect.",
                effectsBefore, effectsAfter);
    }

    // --- Passwall Adept ExileOnMoved ---

    @Test
    public void testPasswallAdeptBouncingTargetCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card passwallAdept = addCard("Passwall Adept", p);
        Card target = addCard("Hill Giant", p);
        Card unsummon = addCardToZone("Unsummon", p, ZoneType.Hand);

        SpellAbility sa = findAbilityWithExileOnMoved(passwallAdept, "Battlefield");
        AssertJUnit.assertNotNull("Passwall Adept activated ability not found", sa);
        sa.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);

        AssertJUnit.assertNotNull(findSingleEffectCardFromSource(p, ZoneType.Command, "Passwall Adept"));
        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Passwall Adept");
        castSpellWithCardTarget(game, p, unsummon, target);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Passwall Adept");

        AssertJUnit.assertTrue("Bouncing the remembered creature should clean up the effect.",
                effectsAfter < effectsBefore);
    }

    @Test
    public void testPasswallAdeptBouncingUnrelatedDoesNotCleanUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card passwallAdept = addCard("Passwall Adept", p);
        Card target = addCard("Hill Giant", p);
        Card unrelated = addCard("Grizzly Bears", p);
        Card unsummon = addCardToZone("Unsummon", p, ZoneType.Hand);

        SpellAbility sa = findAbilityWithExileOnMoved(passwallAdept, "Battlefield");
        AssertJUnit.assertNotNull(sa);
        sa.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);

        AssertJUnit.assertNotNull(findSingleEffectCardFromSource(p, ZoneType.Command, "Passwall Adept"));
        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Passwall Adept");
        castSpellWithCardTarget(game, p, unsummon, unrelated);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Passwall Adept");

        AssertJUnit.assertEquals("Bouncing unrelated creature should not affect effect.",
                effectsBefore, effectsAfter);
    }

    // --- Mission Briefing ExileOnMoved ---

    @Test
    public void testMissionBriefingCastingRememberedCardCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card missionBriefing = addCardToZone("Mission Briefing", p, ZoneType.Hand);
        addCardToZone("Ponder", p, ZoneType.Graveyard);

        AssertJUnit.assertTrue(castSpell(p, missionBriefing.getFirstSpellAbility()));
        playUntilStackClear(game);

        Card effect = findSingleEffectCardFromSource(p, ZoneType.Command, "Mission Briefing");
        AssertJUnit.assertNotNull(effect);
        AssertJUnit.assertEquals("Ponder", getSingleRememberedCard(effect, "Mission Briefing").getName());

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Mission Briefing");
        Card ponderInYard = findSingleCardByName(p, ZoneType.Graveyard, "Ponder");
        AssertJUnit.assertTrue(castSpell(p, ponderInYard.getFirstSpellAbility()));
        playUntilStackClear(game);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Mission Briefing");

        AssertJUnit.assertTrue("Casting remembered card should clean up the effect.",
                effectsAfter < effectsBefore);
    }

    @Test
    public void testMissionBriefingMovingRememberedByOtherMeansDoesNotCleanUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card missionBriefing = addCardToZone("Mission Briefing", p, ZoneType.Hand);
        addCardToZone("Ponder", p, ZoneType.Graveyard);
        Card callToMind = addCardToZone("Call to Mind", p, ZoneType.Hand);

        AssertJUnit.assertTrue(castSpell(p, missionBriefing.getFirstSpellAbility()));
        playUntilStackClear(game);

        AssertJUnit.assertNotNull(findSingleEffectCardFromSource(p, ZoneType.Command, "Mission Briefing"));

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Mission Briefing");
        Card ponderInYard = findSingleCardByName(p, ZoneType.Graveyard, "Ponder");
        castSpellWithCardTarget(game, p, callToMind, ponderInYard);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Mission Briefing");

        AssertJUnit.assertEquals("Moving remembered card by other means should not clean up effect.",
                effectsBefore, effectsAfter);
    }

    // --- Havengul Lich ExileOnMoved ---

    @Test
    public void testHavengulLichCastingFromGraveyardCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        addCard("Havengul Lich", p);
        Card target = addCardToZone("Runeclaw Bear", p, ZoneType.Graveyard);

        Card lich = findCardWithName(game, "Havengul Lich");
        SpellAbility sa = findAbilityWithExileOnMoved(lich, "Graveyard");
        AssertJUnit.assertNotNull("Havengul Lich activated ability not found", sa);
        sa.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);

        AssertJUnit.assertNotNull(findSingleEffectCardFromSource(p, ZoneType.Command, "Havengul Lich"));

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Havengul Lich");
        Card bearInYard = findSingleCardByName(p, ZoneType.Graveyard, "Runeclaw Bear");
        AssertJUnit.assertTrue(castSpell(p, bearInYard.getFirstSpellAbility()));
        playUntilStackClear(game);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Havengul Lich");

        AssertJUnit.assertTrue("Casting remembered creature from graveyard should clean up effect.",
                effectsAfter < effectsBefore);
    }

    @Test
    public void testHavengulLichBouncingUnrelatedDoesNotCleanUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        addCard("Havengul Lich", p);
        Card target = addCardToZone("Runeclaw Bear", p, ZoneType.Graveyard);
        Card unrelated = addCard("Grizzly Bears", p);
        Card unsummon = addCardToZone("Unsummon", p, ZoneType.Hand);

        Card lich = findCardWithName(game, "Havengul Lich");
        SpellAbility sa = findAbilityWithExileOnMoved(lich, "Graveyard");
        AssertJUnit.assertNotNull(sa);
        sa.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);

        AssertJUnit.assertNotNull(findSingleEffectCardFromSource(p, ZoneType.Command, "Havengul Lich"));

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Havengul Lich");
        castSpellWithCardTarget(game, p, unsummon, unrelated);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Havengul Lich");

        AssertJUnit.assertEquals("Bouncing unrelated creature should not affect effect.",
                effectsBefore, effectsAfter);
    }

    // --- Teferi's Time Twist ExileOnMoved ---

    @Test
    public void testTeferiTimeTwistReturnsAtEndOfTurnAndCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card teferi = addCardToZone("Teferi's Time Twist", p, ZoneType.Hand);
        Card hillGiant = addCard("Hill Giant", p);

        SpellAbility sa = teferi.getFirstSpellAbility();
        sa.getTargets().add(hillGiant);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Hill Giant", ZoneType.Exile));

        game.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN);
        playUntilStackClear(game);

        Card returned = findCardByIdInZone(p, ZoneType.Battlefield, hillGiant.getId());
        AssertJUnit.assertNotNull("Teferi target should return to battlefield", returned);
        AssertJUnit.assertEquals("Teferi target should enter with +1/+1 counter", 1,
                returned.getCounters(CounterEnumType.P1P1));
        AssertJUnit.assertEquals(0,
                countEffectCardsFromSource(p, ZoneType.Command, "Teferi's Time Twist"));
    }

    @Test
    public void testTeferiTimeTwistUnrelatedMovementDoesNotAffectExiledCard() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card teferi = addCardToZone("Teferi's Time Twist", p, ZoneType.Hand);
        Card hillGiant = addCard("Hill Giant", p);
        Card unrelated = addCard("Grizzly Bears", p);
        Card unsummon = addCardToZone("Unsummon", p, ZoneType.Hand);

        SpellAbility sa = teferi.getFirstSpellAbility();
        sa.getTargets().add(hillGiant);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Hill Giant", ZoneType.Exile));

        int effectsBefore = countEffectCardsFromSource(p, ZoneType.Command, "Teferi's Time Twist");
        castSpellWithCardTarget(game, p, unsummon, unrelated);
        int effectsAfter = countEffectCardsFromSource(p, ZoneType.Command, "Teferi's Time Twist");

        AssertJUnit.assertEquals("Bouncing unrelated creature should not affect Teferi effect.",
                effectsBefore, effectsAfter);
        AssertJUnit.assertEquals(1, countCardsWithName(game, "Hill Giant", ZoneType.Exile));
    }

    // --- Recommission ExileOnMoved ---

    @Test
    public void testRecommissionReturnsCreatureWithCounterAndCleansUpEffect() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);

        Card recommission = addCardToZone("Recommission", p, ZoneType.Hand);
        Card target = addCardToZone("Runeclaw Bear", p, ZoneType.Graveyard);

        SpellAbility sa = recommission.getFirstSpellAbility();
        SpellAbility dbReturn = sa.getSubAbility();
        AssertJUnit.assertNotNull("Recommission should have DBReturn sub-ability", dbReturn);
        dbReturn.getTargets().add(target);
        AssertJUnit.assertTrue(castSpell(p, sa));
        playUntilStackClear(game);

        Card returned = findCardByIdInZone(p, ZoneType.Battlefield, target.getId());
        AssertJUnit.assertNotNull("Recommission target should return to battlefield", returned);
        AssertJUnit.assertEquals("Recommission target should enter with +1/+1 counter", 1,
                returned.getCounters(CounterEnumType.P1P1));
        AssertJUnit.assertEquals(0,
                countEffectCardsFromSource(p, ZoneType.Command, "Recommission"));
    }
}
