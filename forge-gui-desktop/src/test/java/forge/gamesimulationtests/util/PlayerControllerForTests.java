package forge.gamesimulationtests.util;

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import forge.LobbyPlayer;
import forge.card.ColorSet;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostShard;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.*;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.*;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostPayment;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.mana.ManaConversionMatrix;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.*;
import forge.game.replacement.ReplacementEffect;
import forge.game.spellability.*;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.PlayerZone;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.gamesimulationtests.util.card.CardSpecification;
import forge.gamesimulationtests.util.card.CardSpecificationHandler;
import forge.gamesimulationtests.util.player.PlayerSpecification;
import forge.gamesimulationtests.util.player.PlayerSpecificationHandler;
import forge.gamesimulationtests.util.playeractions.*;
import forge.item.PaperCard;
import forge.player.HumanPlay;
import forge.util.Aggregates;
import forge.util.ITriggerEvent;
import forge.util.MyRandom;
import forge.util.collect.FCollectionView;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Predicate;

/**
 * Default harmless implementation for tests.
 * Test-specific behaviour can easily be added by mocking (parts of) this class.
 *
 * Note that the current PlayerController implementations seem to be responsible for handling some game logic,
 * and even aside from that, they are theoretically capable of making illegal choices (which are then not blocked by the real game logic).
 * Test cases that need to override the default behaviour of this class should make sure to do so in a way that does not invalidate their correctness.
 */
public class PlayerControllerForTests extends PlayerController {
    private PlayerActions playerActions;

    public PlayerControllerForTests(Game game, Player player, LobbyPlayer lobbyPlayer) {
        super(game, player, lobbyPlayer);
    }

    public void setPlayerActions(PlayerActions playerActions) {
        this.playerActions = playerActions;
    }

    public PlayerActions getPlayerActions() {
        return playerActions;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public void playSpellAbilityNoStack(SpellAbility effectSA, boolean mayChoseNewTargets) {
        HumanPlay.playSpellAbilityNoStack(null, player, effectSA, !mayChoseNewTargets);
    }

    @Override
    public List<PaperCard> sideboard(Deck deck, GameType gameType, String message) {
        return null; // refused to side
    }

    @Override
    public Map<Card, Integer> assignCombatDamage(Card attacker, CardCollectionView blockers, CardCollectionView remaining, int damageDealt, GameEntity defender, boolean overrideOrder) {
        // Check for test-specified override
        if (playerActions != null) {
            AssignCombatDamageAction action = playerActions.getNextActionIfApplicable(player, getGame(), AssignCombatDamageAction.class);
            if (action != null) {
                Map<Card, Integer> result = new HashMap<>();
                Map<String, Integer> spec = action.getDamageAssignment();
                for (Card blocker : blockers) {
                    Integer dmg = spec.get(blocker.getName());
                    if (dmg != null) {
                        result.put(blocker, dmg);
                    }
                }
                return result;
            }
        }

        // Default: assign damage to blockers in order, with trample remainder to defender
        Map<Card, Integer> result = new HashMap<>();
        int damageLeft = damageDealt;
        boolean hasTrample = attacker.hasKeyword(Keyword.TRAMPLE);

        for (int i = 0; i < blockers.size(); i++) {
            Card blocker = blockers.get(i);
            int lethal = blocker.getLethalDamage();
            if (lethal <= 0) {
                lethal = 0;
            }
            if (i == blockers.size() - 1 && !hasTrample) {
                // Last blocker with no trample: assign all remaining damage
                result.put(blocker, damageLeft);
                damageLeft = 0;
            } else {
                int toAssign = Math.min(lethal, damageLeft);
                result.put(blocker, toAssign);
                damageLeft -= toAssign;
            }
        }
        return result;
    }

    @Override
    public Map<GameEntity, Integer> divideShield(Card effectSource, Map<GameEntity, Integer> affected, int shieldAmount) {
        // Default: distribute evenly with remainder to first entities
        Map<GameEntity, Integer> result = new HashMap<>();
        List<GameEntity> entities = new ArrayList<>(affected.keySet());
        if (entities.isEmpty()) {
            return result;
        }
        int perEntity = shieldAmount / entities.size();
        int remainder = shieldAmount % entities.size();
        for (int i = 0; i < entities.size(); i++) {
            int amount = perEntity + (i < remainder ? 1 : 0);
            result.put(entities.get(i), amount);
        }
        return result;
    }

    @Override
    public Map<Byte, Integer> specifyManaCombo(SpellAbility sa, ColorSet colorSet, int manaAmount, boolean different) {
        // Check for test-specified override
        if (playerActions != null) {
            SpecifyManaComboAction action = playerActions.getNextActionIfApplicable(player, getGame(), SpecifyManaComboAction.class);
            if (action != null) {
                return action.getManaSpec();
            }
        }

        // Default: allocate mana across available colors
        Map<Byte, Integer> result = new HashMap<>();
        List<MagicColor.Color> colors = new ArrayList<>();
        for (MagicColor.Color c : colorSet) {
            if (c != MagicColor.Color.COLORLESS) {
                colors.add(c);
            }
        }
        if (colors.isEmpty()) {
            return result;
        }
        if (different) {
            // Assign 1 per color up to manaAmount
            int assigned = 0;
            for (MagicColor.Color c : colors) {
                if (assigned >= manaAmount) break;
                result.put(c.getColorMask(), 1);
                assigned++;
            }
        } else {
            // All mana as first available color
            result.put(colors.get(0).getColorMask(), manaAmount);
        }
        return result;
    }

    @Override
    public Integer announceRequirements(SpellAbility ability, String announce) {
        // Check for test-specified override
        if (playerActions != null) {
            AnnounceAction action = playerActions.getNextActionIfApplicable(player, getGame(), AnnounceAction.class);
            if (action != null) {
                return action.getValue();
            }
        }
        // Default: return 0 (safe minimum for X costs)
        return 0;
    }

    @Override
    public CardCollectionView choosePermanentsToSacrifice(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return chooseItems(validTargets, min);
    }

    @Override
    public CardCollectionView choosePermanentsToDestroy(SpellAbility sa, int min, int max, CardCollectionView validTargets, String message) {
        return chooseItems(validTargets, min);
    }

    @Override
    public TargetChoices chooseNewTargetsFor(SpellAbility ability, Predicate<GameObject> filter, boolean optional) {
        // Check for test-specified override
        if (playerActions != null) {
            ChooseTargetsAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseTargetsAction.class);
            if (action != null) {
                TargetChoices newTargets = new TargetChoices();
                if (action.getTargetCardName() != null) {
                    for (Card c : getGame().getCardsInGame()) {
                        if (c.getName().equals(action.getTargetCardName())) {
                            if (filter == null || filter.test(c)) {
                                newTargets.add(c);
                                return newTargets;
                            }
                        }
                    }
                } else if (action.getTargetPlayer() != null) {
                    Player target = PlayerSpecificationHandler.INSTANCE.find(getGame(), action.getTargetPlayer());
                    if (filter == null || filter.test(target)) {
                        newTargets.add(target);
                        return newTargets;
                    }
                }
            }
        }
        // Default: keep existing targets (return null means "keep old targets")
        return null;
    }

    @Override
    public Pair<SpellAbilityStackInstance, GameObject> chooseTarget(SpellAbility sa, List<Pair<SpellAbilityStackInstance, GameObject>> allTargets) {
        return chooseItem(allTargets);
    }

    @Override
    public CardCollectionView chooseCardsForEffect(CardCollectionView sourceList, SpellAbility sa, String title, int min, int max, boolean isOptional, Map<String, Object> params) {
        return chooseItems(sourceList, max);
    }

    @Override
    public List<Card> chooseContraptionsToCrank(List<Card> contraptions) {
        return contraptions;
    }

    @Override
    public boolean helpPayForAssistSpell(ManaCostBeingPaid cost, SpellAbility sa, int max, int requested) {
        // For now, don't change anything for assists in tests
        // "True" here means don't rewind spell
        return true;
    }

    @Override
    public Player choosePlayerToAssistPayment(FCollectionView<Player> optionList, SpellAbility sa, String title, int max) {
        return Iterables.getFirst(optionList, null);
    }

    @Override
    public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> optionList, DelayedReveal delayedReveal, SpellAbility sa, String title, boolean isOptional, Player targetedPlayer, Map<String, Object> params) {
        if (delayedReveal != null) {
            reveal(delayedReveal);
        }
        return chooseItem(optionList);
    }

    @Override
    public SpellAbility chooseSingleSpellForEffect(List<SpellAbility> spells, SpellAbility sa, String title,
            Map<String, Object> params) {
        return chooseItem(spells);
    }

    @Override
    public <T extends GameEntity> List<T> chooseEntitiesForEffect(FCollectionView<T> optionList, int min, int max, DelayedReveal delayedReveal, SpellAbility sa, String title, Player relatedPlayer, Map<String, Object> params) {
        if (delayedReveal != null) {
            reveal(delayedReveal);
        }
        List<T> result = new ArrayList<>();
        int count = 0;
        for (T item : optionList) {
            if (count >= min) break;
            result.add(item);
            count++;
        }
        return result;
    }

    @Override
    public boolean confirmAction(SpellAbility sa, PlayerActionConfirmMode mode, String message, List<String> options, Card cardToShow, Map<String, Object> params) {
        return true;
    }

    @Override
    public boolean confirmBidAction(SpellAbility sa, PlayerActionConfirmMode bidlife, String string, int bid, Player winner) {
        return false;
    }

    @Override
    public boolean confirmStaticApplication(Card hostCard, PlayerActionConfirmMode mode, String message, String logic) {
        return true;
    }

    @Override
    public boolean confirmTrigger(WrappedAbility wrapper) {
        return true;
    }

    @Override
    public Player chooseStartingPlayer(boolean isFirstgame) {
        return this.player;
    }

    @Override
    public CardCollection orderBlockers(Card attacker, CardCollection blockers) {
        return blockers;
    }

    @Override
    public List<Card> exertAttackers(List<Card> attackers) {
        return Lists.newArrayList(attackers);
    }

    @Override
    public List<Card> enlistAttackers(List<Card> attackers) {
        return Lists.newArrayList();
    }

    @Override
    public CardCollection orderBlocker(final Card attacker, final Card blocker, final CardCollection oldBlockers) {
        final CardCollection allBlockers = new CardCollection(oldBlockers);
        allBlockers.add(blocker);
        return allBlockers;
    }

    @Override
    public CardCollection orderAttackers(Card blocker, CardCollection attackers) {
        return attackers;
    }

    @Override
    public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String messagePrefix, boolean addSuffix) {
        //nothing needs to be done here
    }

    @Override
    public void reveal(List<CardView> cards, ZoneType zone, PlayerView owner, String messagePrefix, boolean addSuffix) {
        //nothing needs to be done here
    }

    @Override
    public void notifyOfValue(SpellAbility saSource, GameObject realtedTarget, String value) {
        //nothing needs to be done here
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForScry(CardCollection topN) {
        return ImmutablePair.of(topN, null);
    }

    @Override
    public ImmutablePair<CardCollection, CardCollection> arrangeForSurveil(CardCollection topN) {
        return ImmutablePair.of(topN, null);
    }

    @Override
    public boolean willPutCardOnTop(Card c) {
        return true;
    }

    @Override
    public CardCollectionView orderMoveToZoneList(CardCollectionView cards, ZoneType destinationZone, SpellAbility source) {
        return cards;
    }

    @Override
    public CardCollection chooseCardsToDiscardFrom(Player playerDiscard, SpellAbility sa, CardCollection validCards, int min, int max) {
        return chooseItems(validCards, min);
    }

    @Override
    public CardCollectionView chooseCardsToDelve(int genericAmount, CardCollection grave) {
        return CardCollection.EMPTY;
    }

    @Override
    public CardCollectionView chooseCardsToRevealFromHand(int min, int max, CardCollectionView valid) {
        return chooseItems(valid, min);
    }

    @Override
    public CardCollectionView chooseCardsToDiscardUnlessType(int min, CardCollectionView hand, String param, SpellAbility sa) {
        // Check for test-specified override
        if (playerActions != null) {
            ChooseCardsAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseCardsAction.class);
            if (action != null) {
                CardCollection result = new CardCollection();
                for (String name : action.getCardNames()) {
                    for (Card c : hand) {
                        if (c.getName().equals(name) && !result.contains(c)) {
                            result.add(c);
                            break;
                        }
                    }
                }
                return result;
            }
        }

        // Default: try to find a card matching the type; if found, discard it to satisfy the "unless"
        // Otherwise discard first min cards
        String[] splitTypes = param.split(",");
        CardCollection result = new CardCollection();
        for (Card c : hand) {
            if (c.isValid(splitTypes, sa.getActivatingPlayer(), sa.getHostCard(), sa)) {
                result.add(c);
                if (result.size() >= min) {
                    return result;
                }
            }
        }
        // Not enough matching type cards found, just discard first min cards
        return chooseItems(hand, min);
    }

    @Override
    public List<SpellAbility> chooseSaToActivateFromOpeningHand(List<SpellAbility> usableFromOpeningHand) {
        return usableFromOpeningHand;
    }

    @Override
    public PlayerZone chooseStartingHand(List<PlayerZone> zones) {
        return zones.get(0);
    }

    @Override
    public Mana chooseManaFromPool(List<Mana> manaChoices) {
        return chooseItem(manaChoices);
    }

    @Override
    public boolean confirmReplacementEffect(ReplacementEffect replacementEffect, SpellAbility effectSA, GameEntity affected, String question) {
        return true;
    }

    @Override
    public CardCollectionView tuckCardsViaMulligan(final Player mulliganingPlayer, int cardsToReturn) {
        CardCollectionView hand = player.getCardsIn(ZoneType.Hand);
        return hand;
    }

    @Override
    public boolean mulliganKeepHand(Player firstPlayer, int cardsToReturn) {
        return true;
    }

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        //Doing nothing is safe in most cases, but not all (creatures that must attack etc).  TODO: introduce checks?
        if (playerActions == null) {
            return;
        }
        DeclareAttackersAction declareAttackers = playerActions.getNextActionIfApplicable(player, getGame(), DeclareAttackersAction.class);
        if (declareAttackers == null) {
            return;
        }

        //TODO: check that the chosen attack configuration is legal?  (Including creatures that did not attack but should)
        //TODO: check that the chosen attack configuration was a complete match to what was requested?
        //TODO: banding (don't really care at the moment...)

        for (Map.Entry<CardSpecification, PlayerSpecification> playerAttackAssignment : declareAttackers.getPlayerAttackAssignments().entrySet()) {
            Player defender = getPlayerBeingAttacked(getGame(), player, playerAttackAssignment.getValue());
            attack(combat, playerAttackAssignment.getKey(), defender);
        }
        for (Map.Entry<CardSpecification, CardSpecification> planeswalkerAttackAssignment: declareAttackers.getPlaneswalkerAttackAssignments().entrySet()) {
            Card defender = CardSpecificationHandler.INSTANCE.find(getGame().getCardsInGame(), planeswalkerAttackAssignment.getKey());
            attack(combat, planeswalkerAttackAssignment.getKey(), defender);
        }

        if (!CombatUtil.validateAttackers(combat)) {
            throw new IllegalStateException("Illegal attack declaration!");
        }
    }

    private Player getPlayerBeingAttacked(Game game, Player attacker, PlayerSpecification defenderSpecification) {
        if (defenderSpecification != null) {
            return PlayerSpecificationHandler.INSTANCE.find(getGame().getPlayers(), defenderSpecification);
        }
        if (getGame().getPlayers().size() != 2) {
            throw new IllegalStateException("Can't use implicit defender specification in this situation!");
        }
        for (Player player : getGame().getPlayers()) {
            if (!attacker.equals(player)) {
                return player;
            }
        }
        throw new IllegalStateException("Couldn't find implicit defender!");
    }

    private void attack(Combat combat, CardSpecification attackerSpecification, GameEntity defender) {
        Card attacker = CardSpecificationHandler.INSTANCE.find(combat.getAttackingPlayer().getCreaturesInPlay(), attackerSpecification);
        if (!CombatUtil.canAttack(attacker, defender)) {
            throw new IllegalStateException(attacker + " can't attack " + defender);
        }
        combat.addAttacker(attacker, defender);
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        //Doing nothing is safe in most cases, but not all (creatures that must block, attackers that must be blocked etc).  TODO: legality checks?
        if (playerActions == null) {
            return;
        }
        DeclareBlockersAction declareBlockers = playerActions.getNextActionIfApplicable(player, getGame(), DeclareBlockersAction.class);
        if (declareBlockers == null) {
            return;
        }

        //TODO: check that the chosen block configuration is 100% legal?
        //TODO: check that the chosen block configuration was a 100% match to what was requested?
        //TODO: where do damage assignment orders get handled?

        for (Map.Entry<CardSpecification, Collection<CardSpecification>> blockingAssignment : declareBlockers.getBlockingAssignments().asMap().entrySet()) {
            Card attacker = CardSpecificationHandler.INSTANCE.find(combat.getAttackers(), blockingAssignment.getKey());
            for (CardSpecification blockerSpecification : blockingAssignment.getValue()) {
                Card blocker = CardSpecificationHandler.INSTANCE.find(getGame(), blockerSpecification);
                if (!CombatUtil.canBlock(attacker, blocker)) {
                    throw new IllegalStateException(blocker + " can't block " + blocker);
                }
                combat.addBlocker(attacker, blocker);
            }
        }
        String blockValidation = CombatUtil.validateBlocks(combat, player);
        if (blockValidation != null) {
            throw new IllegalStateException(blockValidation);
        }
    }

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        if (playerActions != null) {
            PlayLandFromHandAction playLand = playerActions.getNextActionIfApplicable(player, getGame(), PlayLandFromHandAction.class);
            if (playLand != null) {
                SpellAbility landSa = playLand.playLandFromHand(player, getGame());
                return Collections.singletonList(landSa);
            }

            CastSpellFromHandAction castSpellFromHand = playerActions.getNextActionIfApplicable(player, getGame(), CastSpellFromHandAction.class);
            if (castSpellFromHand != null) {
                castSpellFromHand.castSpellFromHand(player, getGame());
            }

            ActivateAbilityAction activateAbilityAction = playerActions.getNextActionIfApplicable(player, getGame(), ActivateAbilityAction.class);
            if (activateAbilityAction != null) {
                activateAbilityAction.activateAbility(player, getGame());
            }
        }
        return null;
    }

    @Override
    public CardCollection chooseCardsToDiscardToMaximumHandSize(int numDiscard) {
        return chooseItems(player.getZone(ZoneType.Hand).getCards(), numDiscard);
    }

    @Override
    public boolean payCombatCost(Card card, Cost cost, SpellAbility sa, String prompt) {
        return true;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, int min, int max) {
        return min;
    }

    @Override
    public boolean chooseBinary(SpellAbility sa, String question, BinaryChoiceType kindOfChoice, Boolean defaultVal) {
        return true;
    }

    @Override
    public boolean chooseFlipResult(SpellAbility sa, Player flipper, boolean[] results, boolean call) {
        return true;
    }

    @Override
    public List<AbilitySub> chooseModeForAbility(SpellAbility sa, List<AbilitySub> possible, int min, int num, boolean allowRepeat) {
        // Check for test-specified override
        if (playerActions != null) {
            ChooseModeAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseModeAction.class);
            if (action != null) {
                List<AbilitySub> chosen = new ArrayList<>();
                if (action.getModeIndices() != null) {
                    for (int idx : action.getModeIndices()) {
                        if (idx >= 0 && idx < possible.size()) {
                            chosen.add(possible.get(idx));
                        }
                    }
                } else if (action.getModePrefixes() != null) {
                    for (String prefix : action.getModePrefixes()) {
                        for (AbilitySub mode : possible) {
                            String desc = mode.getDescription();
                            if (desc != null && desc.startsWith(prefix)) {
                                chosen.add(mode);
                                break;
                            }
                        }
                    }
                }
                return chosen;
            }
        }

        // Default: select first min modes from possible list
        return new ArrayList<>(possible.subList(0, Math.min(min, possible.size())));
    }

    @Override
    public byte chooseColor(String message, SpellAbility sa, ColorSet colors) {
        if (playerActions != null) {
            ChooseColorAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseColorAction.class);
            if (action != null && action.getColors().length > 0) {
                return action.getColors()[0];
            }
        }
        return Iterables.getFirst(colors, MagicColor.Color.WHITE).getColorMask();
    }

    @Override
    public byte chooseColorAllowColorless(String message, Card card, ColorSet colors) {
        if (playerActions != null) {
            ChooseColorAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseColorAction.class);
            if (action != null && action.getColors().length > 0) {
                return action.getColors()[0];
            }
        }
        return Iterables.getFirst(colors, MagicColor.Color.COLORLESS).getColorMask();
    }

    private CardCollection chooseItems(CardCollectionView items, int amount) {
        if (items == null || items.isEmpty()) {
            return new CardCollection(items);
        }
        return (CardCollection)items.subList(0, Math.min(amount, items.size()));
    }

    private <T> T chooseItem(Iterable<T> items) {
        if (items == null) {
            return null;
        }
        return Iterables.getFirst(items, null);
    }

    @Override
    public SpellAbility getAbilityToPlay(Card hostCard, List<SpellAbility> abilities, ITriggerEvent triggerEvent) {
        // Isn't this a method invocation loop? --elcnesh
        return getAbilityToPlay(hostCard, abilities);
    }

    @Override
    public String chooseSomeType(String kindOfType, SpellAbility sa, Collection<String> validTypes, boolean isOptional) {
        return chooseItem(validTypes);
    }

    @Override
    public String chooseSector(Card assignee, String ai, List<String> sectors) {
        return chooseItem(sectors);
    }

    @Override
    public int chooseSprocket(Card assignee, boolean forceDifferent) {
        return forceDifferent && assignee.getSprocket() == 1 ? 2 : 1;
    }

    @Override
    public PlanarDice choosePDRollToIgnore(List<PlanarDice> rolls) {
        return Aggregates.random(rolls);
    }

    @Override
    public Integer chooseRollToIgnore(List<Integer> rolls) {
        return Aggregates.random(rolls);
    }

    @Override
    public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
        return new ArrayList<>();
    }

    @Override
    public Integer chooseRollToModify(List<Integer> rolls) {
        return Aggregates.random(rolls);
    }

    @Override
    public RollDiceEffect.DieRollResult chooseRollToSwap(List<RollDiceEffect.DieRollResult> rolls) {
        return Aggregates.random(rolls);
    }

    @Override
    public String chooseRollSwapValue(List<String> swapChoices, Integer currentResult, int power, int toughness) {
        return Aggregates.random(swapChoices);
    }

    @Override
    public Object vote(SpellAbility sa, String prompt, List<Object> options, ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
        return chooseItem(options);
    }

    @Override
    public ColorSet chooseColors(String message, SpellAbility sa, int min, int max, ColorSet options) {
        // Check for test-specified override
        if (playerActions != null) {
            ChooseColorAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseColorAction.class);
            if (action != null) {
                int mask = 0;
                for (byte c : action.getColors()) {
                    mask |= c;
                }
                return ColorSet.fromMask(mask);
            }
        }
        // Default: select first min colors from options
        int mask = 0;
        int count = 0;
        for (MagicColor.Color c : options) {
            if (count >= min) break;
            mask |= c.getColorMask();
            count++;
        }
        return ColorSet.fromMask(mask);
    }

    @Override
    public CounterType chooseCounterType(List<CounterType> options, SpellAbility sa, String prompt, Map<String, Object> params) {
        return Iterables.getFirst(options, CounterEnumType.P1P1);
    }

    @Override
    public String chooseKeywordForPump(final List<String> options, final SpellAbility sa, final String prompt, final Card tgtCard) {
        if (options.size() <= 1) {
            return Iterables.getFirst(options, null);
        }
        return Aggregates.random(options);
    }

    @Override
    public boolean confirmPayment(CostPart costPart, String string, SpellAbility ability) {
        return true;
    }

    @Override
    public ReplacementEffect chooseSingleReplacementEffect(List<ReplacementEffect> possibleReplacers) {
        // TODO Auto-generated method stub
        return Iterables.getFirst(possibleReplacers, null);
    }

    @Override
    public StaticAbility chooseSingleStaticAbility(String prompt, List<StaticAbility> possibleStatics) {
        // TODO Auto-generated method stub
        return Iterables.getFirst(possibleStatics, null);
    }

    @Override
    public String chooseProtectionType(String string, SpellAbility sa, List<String> choices) {
        return choices.get(0);
    }

    @Override
    public boolean payCostToPreventEffect(Cost cost, SpellAbility sa, boolean alreadyPaid, FCollectionView<Player> allPayers) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean payCostDuringRoll(final Cost cost, final SpellAbility sa, final FCollectionView<Player> allPayers) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void orderAndPlaySimultaneousSa(List<SpellAbility> activePlayerSAs) {
        // Process in reverse order (last added resolves first) matching PlayerControllerHuman behavior
        for (int i = activePlayerSAs.size() - 1; i >= 0; i--) {
            final SpellAbility next = activePlayerSAs.get(i);
            if (next.isTrigger() && !next.isCopied()) {
                HumanPlay.playSpellAbilityNoStack(null, player, next);
            } else {
                if (next.isCopied()) {
                    if (next.isSpell()) {
                        if (!next.getHostCard().isInZone(ZoneType.Stack)) {
                            next.setHostCard(player.getGame().getAction().moveToStack(next.getHostCard(), next));
                        } else {
                            player.getGame().getStackZone().add(next.getHostCard());
                        }
                    }
                    if (next.isMayChooseNewTargets()) {
                        next.setupNewTargets(player);
                    }
                }
                player.getGame().getStack().add(next);
            }
        }
    }

    @Override
    public boolean playTrigger(Card host, WrappedAbility wrapperAbility, boolean isMandatory) {
        return HumanPlay.playSpellAbilityNoStack(null, player, wrapperAbility);
    }

    @Override
    public boolean playSaFromPlayEffect(SpellAbility tgtSA) {
        // Replicate HumanPlay.playSpellAbility / HumanPlaySpellAbility.playAbility
        // using TestCostDecision for cost payment instead of GUI-based HumanCostDecision
        Card source = tgtSA.getHostCard();
        tgtSA.setActivatingPlayer(player);
        Game game = getGame();

        // Handle land abilities
        if (tgtSA.isLandAbility()) {
            if (tgtSA.canPlay()) {
                tgtSA.resolve();
            }
            return true;
        }

        source.setSplitStateToPlayAbility(tgtSA);

        // Move spell to stack
        Zone fromZone = null;
        int zonePosition = 0;
        if (tgtSA.isSpell() && !source.isCopiedSpell()) {
            fromZone = game.getZoneOf(source);
            if (fromZone != null) {
                zonePosition = fromZone.getCards().indexOf(source);
            }
            tgtSA.setHostCard(game.getAction().moveToStack(source, tgtSA));
        }

        if (!tgtSA.isCopied()) {
            tgtSA.resetPaidHash();
            tgtSA.setPaidLife(0);
        }

        if (tgtSA.isSpell() && !source.isCopiedSpell()) {
            tgtSA = GameActionUtil.addExtraKeywordCost(tgtSA);
        }

        // Announce X and other values
        announceValuesForAbility(tgtSA);

        // Setup cost payment
        Cost abCost = tgtSA.getPayCosts();
        CostPayment payment = new CostPayment(abCost, tgtSA);

        tgtSA.clearManaPaid();
        tgtSA.getPayingManaAbilities().clear();

        // Check prerequisites: restrictions, targeting, timing
        // If test code pre-set targets on any ability in the chain, skip setupTargets
        // (which would clear them) and trust the test
        boolean preCostOk = tgtSA.checkRestrictions(player)
                && (hasTargetsInChain(tgtSA) || !tgtSA.usesTargeting() || tgtSA.setupTargets());

        // Freeze stack and pay costs
        game.getStack().freezeStack(tgtSA);
        boolean paid = preCostOk && payment.payCost(
                new TestCostDecision(player, tgtSA.isTrigger(), tgtSA, tgtSA.getHostCard()));

        if (!paid) {
            payment.refundPayment();
            GameActionUtil.rollbackAbility(tgtSA, fromZone, zonePosition, payment, source);
            game.getStack().unfreezeStack();
            return false;
        }

        if (payment.isFullyPaid()) {
            game.getStack().addAndUnfreeze(tgtSA);
        }
        return true;
    }

    private void announceValuesForAbility(SpellAbility sa) {
        if (sa.isCopied() || sa.isWrapper()) {
            return;
        }
        Cost cost = sa.getPayCosts();
        Card card = sa.getHostCard();
        String announce = sa.getParam("Announce");
        if (announce != null) {
            for (String aVar : announce.split(",")) {
                String varName = aVar.trim();
                Integer value = announceRequirements(sa, varName);
                if (value == null) {
                    value = 0;
                }
                if ("X".equalsIgnoreCase(varName)) {
                    sa.setXManaCostPaid(value);
                } else {
                    sa.setSVar(varName, value.toString());
                    card.setSVar(varName, value.toString());
                }
            }
        } else if (cost != null && cost.hasXInAnyCostPart()) {
            String sVar = sa.getParamOrDefault("XAlternative", sa.getSVar("X"));
            if ("Count$xPaid".equals(sVar) || sVar.isEmpty()) {
                Integer value = announceRequirements(sa, "X");
                if (value == null) {
                    value = 0;
                }
                sa.setXManaCostPaid(value);
            }
        } else {
            sa.setXManaCostPaid(null);
        }
    }

    private boolean hasTargetsInChain(SpellAbility sa) {
        SpellAbility current = sa;
        while (current != null) {
            if (current.usesTargeting() && current.getTargets().size() > 0) {
                return true;
            }
            current = current.getSubAbility();
        }
        return false;
    }

    @Override
    public boolean chooseTargetsFor(SpellAbility currentAbility) {
        TargetRestrictions tgt = currentAbility.getTargetRestrictions();
        if (tgt == null) {
            return true;
        }
        Card card = currentAbility.getHostCard();
        int min = tgt.getMinTargets(card, currentAbility);
        int max = tgt.getMaxTargets(card, currentAbility);

        if (min == 0 && max == 0) {
            return true;
        }

        // Gather all valid candidates (cards and non-card game entities)
        List<GameEntity> allCandidates = tgt.getAllCandidates(currentAbility, true);
        List<Card> validCards = CardUtil.getValidCardsToTarget(currentAbility);

        // Combine into a single ordered list: cards first, then non-card entities
        List<GameEntity> ordered = new ArrayList<>();
        ordered.addAll(validCards);
        for (GameEntity ge : allCandidates) {
            if (!(ge instanceof Card) && !ordered.contains(ge)) {
                ordered.add(ge);
            }
        }

        if (ordered.size() < min) {
            return false;
        }

        // Select min targets
        int toSelect = Math.min(min > 0 ? min : 1, ordered.size());
        for (int i = 0; i < toSelect; i++) {
            currentAbility.getTargets().add(ordered.get(i));
        }

        // Handle divided-as-you-choose
        int amount = currentAbility.getStillToDivide();
        if (toSelect > 0 && amount > 0) {
            Iterable<GameEntity> targets = currentAbility.getTargets().getTargetEntities();
            int size = 0;
            for (GameEntity ignored : targets) {
                size++;
            }
            if (size == 1) {
                currentAbility.addDividedAllocation(
                        currentAbility.getTargets().getTargetEntities().iterator().next(), amount);
            } else if (size > 0) {
                // Distribute evenly
                int perTarget = amount / size;
                int remainder = amount % size;
                int idx = 0;
                for (GameEntity e : targets) {
                    int alloc = perTarget + (idx < remainder ? 1 : 0);
                    currentAbility.addDividedAllocation(e, alloc);
                    idx++;
                }
            }
        }

        return true;
    }

    @Override
    public boolean chooseCardsPile(SpellAbility sa, CardCollectionView pile1, CardCollectionView pile2, String faceUp) {
        return MyRandom.getRandom().nextBoolean();
    }

    @Override
    public void revealAnte(String message, Multimap<Player, PaperCard> removedAnteCards) {
        // test this!
    }

    @Override
    public void revealAISkipCards(final String message, final Map<Player, Map<DeckSection, List<? extends PaperCard>>> unplayable) {
        // TODO test this!
    }

    @Override
    public void revealUnsupported(Map<Player, List<PaperCard>> unsupported) {
        // test this!
    }

    @Override
    public List<PaperCard> chooseCardsYouWonToAddToDeck(List<PaperCard> losses) {
        // TODO Auto-generated method stub
        return losses;
    }

    @Override
    public int chooseNumber(SpellAbility sa, String title, List<Integer> values, Player relatedPlayer) {
        // TODO Auto-generated method stub
        return Iterables.getFirst(values, 0);
    }

    @Override
    public boolean payManaCost(ManaCost toPay, CostPartMana costPartMana, SpellAbility sa, String prompt, ManaConversionMatrix matrix, boolean effect) {
        return true;
    }

    @Override
    public Map<Card, ManaCostShard> chooseCardsForConvokeOrImprovise(SpellAbility sa, ManaCost manaCost,
                                                                     CardCollectionView untappedCards, boolean artifacts, boolean creatures, Integer maxReduction) {
        // TODO: AI to choose a creature to tap would go here
        // Probably along with deciding how many creatures to tap
        return new HashMap<>();
    }

    @Override
    public boolean playChosenSpellAbility(SpellAbility sa) {
        // TODO Play abilities from here
        return true;
    }

    @Override
    public Card chooseSingleCardForZoneChange(ZoneType destination,
            List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, DelayedReveal delayedReveal,
            String selectPrompt, boolean isOptional, Player decider) {
        return chooseSingleEntityForEffect(fetchList, delayedReveal, sa, selectPrompt, isOptional, decider, null);
    }

    @Override
    public List<Card> chooseCardsForZoneChange(ZoneType destination, List<ZoneType> origin, SpellAbility sa, CardCollection fetchList, int min, int max, DelayedReveal delayedReveal, String selectPrompt, Player decider) {
        if (delayedReveal != null) {
            reveal(delayedReveal);
        }
        // Check for test-specified override
        if (playerActions != null) {
            ChooseCardsAction action = playerActions.getNextActionIfApplicable(player, getGame(), ChooseCardsAction.class);
            if (action != null) {
                List<Card> result = new ArrayList<>();
                for (String name : action.getCardNames()) {
                    for (Card c : fetchList) {
                        if (c.getName().equals(name) && !result.contains(c)) {
                            result.add(c);
                            break;
                        }
                    }
                }
                return result;
            }
        }
        // Default: return first min cards from fetchList
        return new ArrayList<>(fetchList.subList(0, Math.min(min, fetchList.size())));
    }

    @Override
    public void resetAtEndOfTurn() {
        // Not used by the controller for tests
    }

    @Override
    public void autoPassCancel() {
        // Not used by the controller for tests
    }

    @Override
    public void awaitNextInput() {
        // Not used by the controller for tests
    }
    @Override
    public void cancelAwaitNextInput() {
        // Not used by the controller for tests
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, String message, Predicate<ICardFace> cpp, String name) {
        return forge.StaticData.instance().getCommonCards().streamAllFaces()
                .filter(cpp)
                .findFirst()
                .orElse(null);
    }

    @Override
    public String chooseCardName(SpellAbility sa, Predicate<ICardFace> cpp, String valid, String message) {
        ICardFace face = chooseSingleCardFace(sa, message, cpp, sa.getHostCard().getName());
        return face != null ? face.getName() : "";
    }

    @Override
    public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
        ICardFace face = chooseSingleCardFace(sa, faces, message);
        return face != null ? face.getName() : "";
    }

    @Override
    public ICardFace chooseSingleCardFace(SpellAbility sa, List<ICardFace> faces, String message) {
        return faces.isEmpty() ? null : faces.get(0);
    }

    @Override
    public CardState chooseSingleCardState(SpellAbility sa, List<CardState> states, String message, Map<String, Object> params) {
        return states.isEmpty() ? null : states.get(0);
    }

    @Override
    public List<Card> chooseCardsForSplice(SpellAbility sa, List<Card> cards) {
        return Lists.newArrayList();
    }

    @Override
    public List<OptionalCostValue> chooseOptionalCosts(SpellAbility choosen,
            List<OptionalCostValue> optionalCostValues) {
        return new ArrayList<>();
    }

    @Override
    public boolean confirmMulliganScry(Player p) {
        return true;
    }

    @Override
    public int chooseNumberForKeywordCost(SpellAbility sa, Cost cost, KeywordInterface keyword, String prompt, int max) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int chooseNumberForCostReduction(final SpellAbility sa, final int min, final int max) {
        return max;
    }

    @Override
    public CardCollection chooseCardsForEffectMultiple(Map<String, CardCollection> validMap, SpellAbility sa, String title, boolean isOptional) {
        // TODO Auto-generated method stub
        return new CardCollection();
    }

    @Override
    public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> spells, SpellAbility sa, String title,
            int num, Map<String, Object> params) {
        return spells.subList(0, Math.min(num, spells.size()));
    }

    @Override
    public List<CostPart> orderCosts(List<CostPart> costs) {
        return costs;
    }
}
