package forge.gamesimulationtests.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.game.GameEntityCounterTable;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.card.CardLists;
import forge.game.card.CardPredicates;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.cost.*;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;
import forge.util.Aggregates;
import forge.util.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cost decision maker for tests. Mirrors HumanCostDecision logic but replaces
 * GUI interactions with programmatic choices (pick first N valid cards, always
 * confirm, delegate to player controller for choices like colors/types).
 */
public class TestCostDecision extends CostDecisionMakerBase {
	private final boolean mandatory;

	public TestCostDecision(Player player, boolean effect, SpellAbility ability, Card source) {
		super(player, effect, ability, source);
		this.mandatory = ability.getPayCosts() != null && ability.getPayCosts().isMandatory();
	}

	@Override
	public boolean paysRightAfterDecision() {
		return true;
	}

	@Override
	public PaymentDecision visit(CostAddMana cost) {
		return PaymentDecision.number(cost.getAbilityAmount(ability));
	}

	@Override
	public PaymentDecision visit(CostChooseColor cost) {
		int c = cost.getAbilityAmount(ability);
		ColorSet chosen = player.getController().chooseColors("Choose a color", ability, c, c, ColorSet.WUBRG);
		return PaymentDecision.colors(chosen);
	}

	@Override
	public PaymentDecision visit(CostChooseCreatureType cost) {
		String choice = player.getController().chooseSomeType("Creature", ability, CardType.getAllCreatureTypes(), true);
		if (choice == null) {
			return null;
		}
		return PaymentDecision.type(choice);
	}

	@Override
	public PaymentDecision visit(CostCollectEvidence cost) {
		CardCollection list = CardLists.filter(
				player.getCardsIn(ZoneType.Graveyard),
				CardPredicates.canExiledBy(ability, isEffect()));
		int total = cost.getAbilityAmount(ability);
		// Pick cards from graveyard until we meet the CMC threshold
		CardCollection selected = new CardCollection();
		int cmc = 0;
		for (Card c : list) {
			if (cmc >= total) {
				break;
			}
			selected.add(c);
			cmc += c.getCMC();
		}
		if (cmc < total) {
			return null;
		}
		return PaymentDecision.card(selected);
	}

	@Override
	public PaymentDecision visit(CostDiscard cost) {
		CardCollectionView hand = player.getCardsIn(ZoneType.Hand);
		String discardType = cost.getType();

		if (cost.payCostFromSource()) {
			return hand.contains(source) ? PaymentDecision.card(source) : null;
		}

		if (discardType.equals("Hand")) {
			if (hand.size() > 1 && ability.getActivatingPlayer() != null) {
				hand = ability.getActivatingPlayer().getController().orderMoveToZoneList(hand, ZoneType.Graveyard, ability);
			}
			return PaymentDecision.card(hand);
		}

		if (discardType.equals("LastDrawn")) {
			Card lastDrawn = player.getLastDrawnCard();
			return hand.contains(lastDrawn) ? PaymentDecision.card(lastDrawn) : null;
		}

		int c = cost.getAbilityAmount(ability);

		if (discardType.equals("Random")) {
			CardCollectionView randomSubset = new CardCollection(Aggregates.random(hand, c));
			if (randomSubset.size() > 1 && ability.getActivatingPlayer() != null) {
				randomSubset = ability.getActivatingPlayer().getController()
						.orderMoveToZoneList(randomSubset, ZoneType.Graveyard, ability);
			}
			return PaymentDecision.card(randomSubset);
		}

		if (discardType.contains("+WithDifferentNames")) {
			CardCollection discarded = new CardCollection();
			CardCollectionView remaining = hand;
			while (c > 0 && !remaining.isEmpty()) {
				Card first = remaining.getFirst();
				discarded.add(first);
				remaining = CardLists.filter(remaining, CardPredicates.sharesNameWith(first).negate());
				c--;
			}
			return c > 0 ? null : PaymentDecision.card(discarded);
		}

		if (discardType.contains("+WithSameName")) {
			String type = TextUtil.fastReplace(discardType, "+WithSameName", "");
			hand = CardLists.getValidCards(hand, type.split(";"), player, source, ability);
			if (c == 0) {
				return PaymentDecision.card(new CardCollection());
			}
			// Find cards that share a name
			CardCollection discarded = new CardCollection();
			if (!hand.isEmpty()) {
				Card first = hand.getFirst();
				discarded.add(first);
				CardCollection sameName = CardLists.filter(hand, CardPredicates.nameEquals(first.getName()));
				sameName.remove(first);
				for (int i = 1; i < c && !sameName.isEmpty(); i++) {
					discarded.add(sameName.getFirst());
					sameName.remove(sameName.getFirst());
				}
			}
			return discarded.size() >= c ? PaymentDecision.card(discarded) : null;
		}

		String[] validType = discardType.split(";");
		hand = CardLists.getValidCards(hand, validType, player, source, ability);
		if (hand.size() < c) {
			return null;
		}
		return PaymentDecision.card(hand.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostDamage cost) {
		int c = cost.getAbilityAmount(ability);
		return PaymentDecision.number(c);
	}

	@Override
	public PaymentDecision visit(CostDraw cost) {
		if (!cost.canPay(ability, player, isEffect())) {
			return null;
		}
		int c = cost.getAbilityAmount(ability);
		List<Player> res = cost.getPotentialPlayers(player, ability);
		PaymentDecision decision = PaymentDecision.players(res);
		decision.c = c;
		return decision;
	}

	@Override
	public PaymentDecision visit(CostExile cost) {
		String type = cost.getType();
		Card onlyPayable = null;
		if (cost.payCostFromSource()) {
			onlyPayable = source;
		}
		if (type.equals("OriginalHost")) {
			onlyPayable = ability.getOriginalHost();
		}
		if (onlyPayable != null) {
			if (onlyPayable.canExiledBy(ability, isEffect())
					&& onlyPayable.getZone() == player.getZone(cost.from.get(0))) {
				return PaymentDecision.card(onlyPayable);
			}
			return null;
		}

		boolean fromTopGrave = false;
		if (type.contains("FromTopGrave")) {
			type = TextUtil.fastReplace(type, "FromTopGrave", "");
			fromTopGrave = true;
		}

		// Strip modifiers for validation
		if (type.contains("+withTotalCMCEQ")) {
			type = type.split("\\+withTotalCMCEQ")[0];
		}
		if (type.contains("+withTotalCMCGE")) {
			type = type.split("\\+withTotalCMCGE")[0];
		}
		if (type.contains("+withTotalManaSymbols_")) {
			type = type.split("\\+withTotalManaSymbols_")[0];
		}
		if (type.contains("+withSharedCardType")) {
			type = TextUtil.fastReplace(type, "+withSharedCardType", "");
		}
		if (type.contains("+withTypesGE")) {
			type = type.split("\\+withTypesGE")[0];
		}

		CardCollection list;
		if (cost.zoneRestriction != 1) {
			list = new CardCollection(player.getGame().getCardsIn(cost.from));
		} else {
			list = new CardCollection(player.getCardsIn(cost.from));
		}

		if (type.equals("All")) {
			return PaymentDecision.card(list);
		}
		list = CardLists.getValidCards(list, type.split(";"), player, source, ability);
		list = CardLists.filter(list, CardPredicates.canExiledBy(ability, isEffect()));

		int c = cost.getAbilityAmount(ability);
		if (list.size() < c) {
			return null;
		}
		if (c == 0) {
			return PaymentDecision.number(0);
		}

		if (fromTopGrave) {
			Collections.reverse(list);
		}

		return PaymentDecision.card(list.subList(0, Math.min(c, list.size())));
	}

	@Override
	public PaymentDecision visit(CostExileFromStack cost) {
		String type = cost.getType();
		List<SpellAbility> saList = new ArrayList<>();
		for (SpellAbilityStackInstance si : player.getGame().getStack()) {
			Card stC = si.getSourceCard();
			SpellAbility stSA = si.getSpellAbility().getRootAbility();
			if (stC.isValid(type.split(";"), ability.getActivatingPlayer(), source, ability) && stSA.isSpell()) {
				saList.add(stSA);
			}
		}
		if (type.equals("All")) {
			return PaymentDecision.spellabilities(saList);
		}
		int c = cost.getAbilityAmount(ability);
		if (saList.size() < c) {
			return null;
		}
		return PaymentDecision.spellabilities(saList.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostExiledMoveToGrave cost) {
		int c = cost.getAbilityAmount(ability);
		Player activator = ability.getActivatingPlayer();
		CardCollection list = CardLists.getValidCards(
				activator.getGame().getCardsIn(ZoneType.Exile),
				cost.getType().split(";"), activator, source, ability);
		if (list.size() < c) {
			return null;
		}
		return PaymentDecision.card(list.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostExert cost) {
		if (cost.payCostFromSource()) {
			if (source.getController() == ability.getActivatingPlayer() && source.isInPlay()) {
				return PaymentDecision.card(source);
			}
			return null;
		}
		int c = cost.getAbilityAmount(ability);
		if (c == 0) {
			return PaymentDecision.number(0);
		}
		CardCollectionView list = CardLists.getValidCards(
				player.getCardsIn(ZoneType.Battlefield),
				cost.getType().split(";"), player, source, ability);
		if (list.size() < c) {
			return null;
		}
		return PaymentDecision.card(list.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostEnlist cost) {
		CardCollection list = CostEnlist.getCardsForEnlisting(player);
		if (list.isEmpty()) {
			return null;
		}
		return PaymentDecision.card(list.getFirst());
	}

	@Override
	public PaymentDecision visit(CostFlipCoin cost) {
		return PaymentDecision.number(cost.getAbilityAmount(ability));
	}

	@Override
	public PaymentDecision visit(CostForage cost) {
		// Try sacrificing food first
		CardCollection food = CardLists.filter(
				player.getCardsIn(ZoneType.Battlefield),
				CardPredicates.isType("Food"),
				CardPredicates.canBeSacrificedBy(ability, isEffect()));
		if (!food.isEmpty()) {
			return PaymentDecision.card(food.getFirst());
		}
		// Otherwise exile 3 from graveyard
		CardCollection exile = CardLists.filter(
				player.getCardsIn(ZoneType.Graveyard),
				CardPredicates.canExiledBy(ability, isEffect()));
		if (exile.size() >= 3) {
			return PaymentDecision.card(exile.subList(0, 3));
		}
		return null;
	}

	@Override
	public PaymentDecision visit(CostRollDice cost) {
		return PaymentDecision.number(cost.getAbilityAmount(ability));
	}

	@Override
	public PaymentDecision visit(CostGainControl cost) {
		int c = cost.getAbilityAmount(ability);
		CardCollectionView list = player.getCardsIn(ZoneType.Battlefield);
		CardCollectionView validCards = CardLists.getValidCards(list, cost.getType().split(";"), player, source, ability);
		validCards = CardLists.filter(validCards, crd -> crd.canBeControlledBy(player));
		if (validCards.size() < c) {
			return null;
		}
		return PaymentDecision.card(validCards.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostGainLife cost) {
		int c = cost.getAbilityAmount(ability);
		List<Player> oppsThatCanGainLife = new ArrayList<>();
		for (Player opp : cost.getPotentialTargets(player, ability)) {
			if (opp.canGainLife()) {
				oppsThatCanGainLife.add(opp);
			}
		}
		if (cost.getCntPlayers() == Integer.MAX_VALUE) {
			return PaymentDecision.players(oppsThatCanGainLife);
		}
		if (oppsThatCanGainLife.isEmpty()) {
			return null;
		}
		return PaymentDecision.players(Lists.newArrayList(oppsThatCanGainLife.get(0)));
	}

	@Override
	public PaymentDecision visit(CostMill cost) {
		return PaymentDecision.number(cost.getAbilityAmount(ability));
	}

	@Override
	public PaymentDecision visit(CostPayLife cost) {
		int c = cost.getAbilityAmount(ability);
		if (player.canPayLife(c, isEffect(), ability)) {
			return PaymentDecision.number(c);
		}
		return null;
	}

	@Override
	public PaymentDecision visit(CostPayEnergy cost) {
		int c = cost.getAbilityAmount(ability);
		if (player.canPayEnergy(c)) {
			return PaymentDecision.number(c);
		}
		return null;
	}

	@Override
	public PaymentDecision visit(CostPayShards cost) {
		int c = cost.getAbilityAmount(ability);
		if (player.canPayShards(c)) {
			return PaymentDecision.number(c);
		}
		return null;
	}

	@Override
	public PaymentDecision visit(CostPartMana cost) {
		// Mana payment is handled interactively by CostPartMana.payAsDecided
		// which calls player.getController().payManaCost()
		return new PaymentDecision(0);
	}

	@Override
	public PaymentDecision visit(CostPromiseGift cost) {
		PlayerCollection opponents = cost.getPotentialPlayers(player, ability);
		if (opponents.isEmpty()) {
			return null;
		}
		Player giftee = player.getController().chooseSingleEntityForEffect(
				opponents, null, ability, "Choose an opponent to promise a gift", false, null, null);
		if (giftee == null) {
			return null;
		}
		return PaymentDecision.players(Lists.newArrayList(giftee));
	}

	@Override
	public PaymentDecision visit(CostPutCardToLib cost) {
		int c = cost.getAbilityAmount(ability);
		CardCollection list = CardLists.getValidCards(
				cost.sameZone ? player.getGame().getCardsIn(cost.getFrom()) : player.getCardsIn(cost.getFrom()),
				cost.getType().split(";"), player, source, ability);

		if (cost.payCostFromSource()) {
			return source.getZone() == player.getZone(cost.from)
					? PaymentDecision.card(source) : null;
		}

		if (list.size() < c) {
			return null;
		}
		return PaymentDecision.card(list.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostPutCounter cost) {
		int c = cost.getAbilityAmount(ability);
		if (cost.payCostFromSource()) {
			return PaymentDecision.card(source);
		}
		CardCollectionView typeList = CardLists.getValidCards(
				source.getGame().getCardsIn(ZoneType.Battlefield),
				cost.getType().split(";"), player, ability.getHostCard(), ability);
		typeList = CardLists.filter(typeList, CardPredicates.canReceiveCounters(cost.getCounter()));
		if (typeList.isEmpty()) {
			return null;
		}
		return PaymentDecision.card(typeList.getFirst());
	}

	@Override
	public PaymentDecision visit(CostBlight cost) {
		return this.visit((CostPutCounter) cost);
	}

	@Override
	public PaymentDecision visit(CostReturn cost) {
		int c = cost.getAbilityAmount(ability);
		if (cost.payCostFromSource()) {
			Card card = ability.getHostCard();
			if (card.getController() == player && card.isInPlay()) {
				return PaymentDecision.card(card);
			}
			return null;
		}
		CardCollectionView validCards = CardLists.getValidCards(
				ability.getActivatingPlayer().getCardsIn(ZoneType.Battlefield),
				cost.getType().split(";"), player, source, ability);
		if (validCards.size() < c) {
			return null;
		}
		return PaymentDecision.card(validCards.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostReveal cost) {
		if (cost.payCostFromSource()) {
			return PaymentDecision.card(source);
		}
		if (cost.getType().equals("Hand")) {
			return PaymentDecision.card(player.getCardsIn(ZoneType.Hand));
		}

		int num = cost.getAbilityAmount(ability);
		if (num == 0) {
			return PaymentDecision.number(0);
		}

		CardCollectionView hand = player.getCardsIn(cost.getRevealFrom());
		if (cost.getType().equals("SameColor")) {
			CardCollectionView hand2 = hand;
			hand = CardLists.filter(hand, c -> {
				for (Card card : hand2) {
					if (!card.equals(c) && card.sharesColorWith(c)) {
						return true;
					}
				}
				return false;
			});
		} else {
			hand = CardLists.getValidCards(hand, cost.getType().split(";"), player, source, ability);
		}

		if (hand.size() < num) {
			return null;
		}
		return PaymentDecision.card(hand.subList(0, num));
	}

	@Override
	public PaymentDecision visit(CostBehold cost) {
		int num = cost.getAbilityAmount(ability);
		CardCollectionView hand = player.getCardsIn(cost.getRevealFrom());
		hand = CardLists.getValidCards(hand, cost.getType().split(";"), player, source, ability);
		if (hand.size() < num) {
			return null;
		}
		return PaymentDecision.card(hand.subList(0, num));
	}

	@Override
	public PaymentDecision visit(CostBeholdExile cost) {
		return this.visit((CostBehold) cost);
	}

	@Override
	public PaymentDecision visit(CostRevealChosen cost) {
		return PaymentDecision.number(1);
	}

	@Override
	public PaymentDecision visit(CostRemoveAnyCounter cost) {
		int c = cost.getAbilityAmount(ability);
		CardCollectionView list = CardLists.getValidCards(
				player.getCardsIn(ZoneType.Battlefield),
				cost.getType().split(";"), player, source, ability);
		list = CardLists.filter(list, CardPredicates.hasCounters());

		GameEntityCounterTable counterTable = new GameEntityCounterTable();
		int remaining = c;
		for (Card card : list) {
			if (remaining <= 0) {
				break;
			}
			for (Map.Entry<CounterType, Integer> entry : card.getCounters().entrySet()) {
				if (remaining <= 0) {
					break;
				}
				if (cost.counter != null && !cost.counter.equals(entry.getKey())) {
					continue;
				}
				if (!card.canRemoveCounters(entry.getKey())) {
					continue;
				}
				int toRemove = Math.min(remaining, entry.getValue());
				counterTable.put(null, card, entry.getKey(), toRemove);
				remaining -= toRemove;
			}
		}
		if (remaining > 0) {
			return null;
		}
		return PaymentDecision.counters(counterTable);
	}

	@Override
	public PaymentDecision visit(CostRemoveCounter cost) {
		String amount = cost.getAmount();
		CounterType cntrs = cost.counter;
		boolean anyCounters = cntrs == null;

		int cntRemoved = 1;
		if (!amount.equals("All")) {
			cntRemoved = cost.getAbilityAmount(ability);
		}

		if (cost.payCostFromSource()) {
			int maxCounters = anyCounters ? source.getNumAllCounters() : source.getCounters(cntrs);
			if (amount.equals("All")) {
				cntRemoved = maxCounters;
			}
			if (maxCounters < cntRemoved) {
				return null;
			}
			GameEntityCounterTable counterTable = generateCounterTable(source, cntrs, cntRemoved);
			if (counterTable.isEmpty()) {
				return null;
			}
			return PaymentDecision.counters(counterTable);
		} else if (cost.getType().equals("OriginalHost")) {
			Card origHost = ability.getOriginalHost();
			int maxCounters = anyCounters ? origHost.getNumAllCounters() : origHost.getCounters(cntrs);
			if (amount.equals("All")) {
				cntRemoved = maxCounters;
			}
			if (maxCounters < cntRemoved) {
				return null;
			}
			GameEntityCounterTable counterTable = generateCounterTable(origHost, cntrs, cntRemoved);
			if (counterTable.isEmpty()) {
				return null;
			}
			return PaymentDecision.counters(counterTable);
		}

		CardCollectionView validCards = CardLists.getValidCards(
				player.getCardsIn(cost.zone), cost.getType().split(";"), player, source, ability);
		validCards = anyCounters ? CardLists.filterAnyCounters(validCards, cntRemoved)
				: CardLists.filter(validCards, CardPredicates.hasCounter(cntrs, cntRemoved));
		if (validCards.isEmpty()) {
			return null;
		}
		Card selected = validCards.getFirst();
		GameEntityCounterTable counterTable = generateCounterTable(selected, cntrs, cntRemoved);
		if (counterTable.isEmpty()) {
			return null;
		}
		return PaymentDecision.counters(counterTable);
	}

	private GameEntityCounterTable generateCounterTable(Card c, CounterType cType, int cntToRemove) {
		GameEntityCounterTable counterTable = new GameEntityCounterTable();
		if (cType != null) {
			counterTable.put(null, c, cType, cntToRemove);
		} else {
			Map<CounterType, Integer> cMap = counterTable.filterToRemove(c);
			for (CounterType ct : ImmutableList.copyOf(cMap.keySet())) {
				if (!c.canRemoveCounters(ct)) {
					cMap.remove(ct);
				}
			}
			if (cMap.isEmpty()) {
				return counterTable;
			}
			if (cMap.size() == 1) {
				counterTable.put(null, c, cMap.entrySet().iterator().next().getKey(), cntToRemove);
			} else {
				// Multiple counter types: pick first type and remove from it
				for (Map.Entry<CounterType, Integer> entry : cMap.entrySet()) {
					int toRemove = Math.min(cntToRemove, entry.getValue());
					if (toRemove > 0) {
						counterTable.put(null, c, entry.getKey(), toRemove);
						cntToRemove -= toRemove;
					}
					if (cntToRemove <= 0) {
						break;
					}
				}
			}
		}
		return counterTable;
	}

	@Override
	public PaymentDecision visit(CostSacrifice cost) {
		String type = cost.getType();

		if (cost.payCostFromSource()) {
			if (source.getController() == ability.getActivatingPlayer()
					&& source.canBeSacrificedBy(ability, isEffect())) {
				return PaymentDecision.card(source);
			}
			return null;
		}

		if (type.equals("OriginalHost")) {
			Card host = ability.getOriginalHost();
			if (host.getController() == ability.getActivatingPlayer()
					&& host.canBeSacrificedBy(ability, isEffect())) {
				return PaymentDecision.card(host);
			}
			return null;
		}

		boolean differentNames = false;
		if (type.contains("+WithDifferentNames")) {
			type = type.replace("+WithDifferentNames", "");
			differentNames = true;
		}

		CardCollectionView list = CardLists.filter(
				player.getCardsIn(ZoneType.Battlefield),
				CardPredicates.canBeSacrificedBy(ability, isEffect()));
		list = CardLists.getValidCards(list, type.split(";"), player, source, ability);

		if (cost.getAmount().equals("All")) {
			return PaymentDecision.card(list);
		}

		int c = cost.getAbilityAmount(ability);
		if (c == 0) {
			return PaymentDecision.number(0);
		}

		if (differentNames) {
			CardCollection chosen = new CardCollection();
			CardCollectionView remaining = list;
			while (c > 0 && !remaining.isEmpty()) {
				Card first = remaining.getFirst();
				chosen.add(first);
				remaining = CardLists.filter(remaining, CardPredicates.sharesNameWith(first).negate());
				c--;
			}
			return c > 0 ? null : PaymentDecision.card(chosen);
		}

		if (list.size() < c) {
			return null;
		}
		return PaymentDecision.card(list.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostTap cost) {
		return PaymentDecision.number(1);
	}

	@Override
	public PaymentDecision visit(CostTapType cost) {
		String type = cost.getType();

		if (type.equals("OriginalHost")) {
			Card host = ability.getOriginalHost();
			return host.canTap() ? PaymentDecision.card(host) : null;
		}

		boolean sameType = false;
		if (type.contains(".sharesCreatureTypeWith")) {
			sameType = true;
			type = TextUtil.fastReplace(type, ".sharesCreatureTypeWith", "");
		}

		boolean totalPower = false;
		String totalP = "";
		if (type.contains("+withTotalPowerGE")) {
			totalPower = true;
			totalP = type.split("withTotalPowerGE")[1];
			type = TextUtil.fastReplace(type, TextUtil.concatNoSpace("+withTotalPowerGE", totalP), "");
		}

		CardCollection typeList = CardLists.getValidCards(
				player.getCardsIn(ZoneType.Battlefield),
				type.split(";"), player, source, ability);
		typeList = CardLists.filter(typeList,
				ability.isCrew() ? CardPredicates.CAN_CREW : CardPredicates.CAN_TAP);

		Integer c = null;
		if (!cost.getAmount().equals("Any")) {
			c = cost.getAbilityAmount(ability);
		}

		if (c != null && c == 0) {
			return PaymentDecision.number(0);
		}

		if (sameType) {
			CardCollection list2 = typeList;
			typeList = CardLists.filter(typeList, c12 -> {
				for (Card card : list2) {
					if (!card.equals(c12) && card.sharesCreatureTypeWith(c12)) {
						return true;
					}
				}
				return false;
			});
		}

		if (totalPower) {
			int needed = Integer.parseInt(totalP);
			// Select creatures greedily until total power is met
			CardCollection selected = new CardCollection();
			int totalPow = 0;
			for (Card card : typeList) {
				selected.add(card);
				totalPow += card.getNetPower();
				if (totalPow >= needed) {
					break;
				}
			}
			return totalPow >= needed ? PaymentDecision.card(selected) : null;
		}

		if (c != null && c > typeList.size()) {
			return null;
		}

		int count = c != null ? c : typeList.size();
		return PaymentDecision.card(typeList.subList(0, Math.min(count, typeList.size())));
	}

	@Override
	public PaymentDecision visit(CostUntapType cost) {
		CardCollection typeList = CardLists.getValidCards(
				player.getGame().getCardsIn(ZoneType.Battlefield),
				cost.getType().split(";"), player, source, ability);
		typeList = CardLists.filter(typeList, c -> c.canUntap(null, false)
				&& (c.getCounters(CounterEnumType.STUN) == 0 || c.canRemoveCounters(CounterEnumType.STUN)));
		int c = cost.getAbilityAmount(ability);
		if (typeList.size() < c) {
			return null;
		}
		return PaymentDecision.card(typeList.subList(0, c));
	}

	@Override
	public PaymentDecision visit(CostUntap cost) {
		return PaymentDecision.number(1);
	}

	@Override
	public PaymentDecision visit(CostUnattach cost) {
		CardCollection cardToUnattach = cost.findCardToUnattach(source, player, ability);
		if (cardToUnattach.isEmpty()) {
			return null;
		}
		return PaymentDecision.card(cardToUnattach.getFirst());
	}
}
