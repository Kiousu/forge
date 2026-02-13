package forge.adventure.data;

import forge.adventure.stage.MapStage;
import forge.adventure.util.AdventureQuestController;
import forge.adventure.util.AdventureQuestEvent;
import forge.adventure.util.AdventureQuestEventType;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdventureQuestStageTests {
    private static final String LILIANA_STONE = "Liliana's Stone";
    private static final String CHANDRA_STONE = "Chandra's Stone";

    @Test
    public void testUseObjectiveDoesNotProgressFromReceiveItemEvent() {
        // Regression: receiving an item must not satisfy a Use objective.
        withMapStageOutsidePoi(() -> {
            AdventureQuestStage stage = createActiveUseStage(1, List.of(LILIANA_STONE));

            AdventureQuestEvent event = createItemEvent(AdventureQuestEventType.RECEIVEITEM, LILIANA_STONE);
            AdventureQuestController.QuestStatus status = stage.handleEvent(event);

            Assert.assertEquals(status, AdventureQuestController.QuestStatus.ACTIVE);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);
        });
    }

    @Test
    public void testUseObjectiveIgnoresUnrelatedEventWithNullItem() {
        // Regression: unrelated events with null item payload must be safely ignored.
        withMapStageOutsidePoi(() -> {
            AdventureQuestStage stage = createActiveUseStage(1, List.of(LILIANA_STONE));

            AdventureQuestEvent event = new AdventureQuestEvent();
            event.type = AdventureQuestEventType.QUESTCOMPLETE;

            AdventureQuestController.QuestStatus status = stage.handleEvent(event);

            Assert.assertEquals(status, AdventureQuestController.QuestStatus.ACTIVE);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);
        });
    }

    @Test
    public void testUseObjectiveCompletesAfterMatchingUseItemEvents() {
        // Happy path: matching USEITEM events should advance and then complete the stage.
        withMapStageOutsidePoi(() -> {
            AdventureQuestStage stage = createActiveUseStage(2, List.of(LILIANA_STONE));

            AdventureQuestController.QuestStatus firstStatus =
                    stage.handleEvent(createItemEvent(AdventureQuestEventType.USEITEM, LILIANA_STONE));
            AdventureQuestController.QuestStatus secondStatus =
                    stage.handleEvent(createItemEvent(AdventureQuestEventType.USEITEM, LILIANA_STONE));

            Assert.assertEquals(firstStatus, AdventureQuestController.QuestStatus.ACTIVE);
            Assert.assertEquals(secondStatus, AdventureQuestController.QuestStatus.COMPLETE);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.COMPLETE);
        });
    }

    @Test
    public void testUseObjectiveDoesNotProgressForWrongItem() {
        // Negative path: using the wrong item should not advance progress.
        withMapStageOutsidePoi(() -> {
            AdventureQuestStage stage = createActiveUseStage(1, List.of(LILIANA_STONE));

            AdventureQuestController.QuestStatus status =
                    stage.handleEvent(createItemEvent(AdventureQuestEventType.USEITEM, CHANDRA_STONE));

            Assert.assertEquals(status, AdventureQuestController.QuestStatus.ACTIVE);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);
        });
    }

    @Test
    public void testUseObjectiveWithEmptyItemListAcceptsAnyUsedItem() {
        // Config behavior: empty item list means any used item is accepted.
        withMapStageOutsidePoi(() -> {
            AdventureQuestStage stage = createActiveUseStage(1, Collections.emptyList());

            AdventureQuestController.QuestStatus status =
                    stage.handleEvent(createItemEvent(AdventureQuestEventType.USEITEM, CHANDRA_STONE));

            Assert.assertEquals(status, AdventureQuestController.QuestStatus.COMPLETE);
            Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.COMPLETE);
        });
    }

    @Test
    public void testMapStageIsolationRestoresPreviousInstanceAfterFailure() {
        // Test harness safety: global MapStage.instance must always be restored.
        MapStage previous = Mockito.mock(MapStage.class);
        MapStage.instance = previous;

        Assert.expectThrows(RuntimeException.class, () ->
                withMapStageOutsidePoi(() -> {
                    throw new RuntimeException("Intentional test exception");
                })
        );

        Assert.assertSame(MapStage.instance, previous);
    }

    private static AdventureQuestStage createActiveUseStage(int requiredUses, List<String> itemNames) {
        // Build an active Use stage with deterministic location behavior for tests.
        AdventureQuestStage stage = new AdventureQuestStage();
        stage.objective = AdventureQuestController.ObjectiveTypes.Use;
        stage.count3 = requiredUses;
        stage.worldMapOK = true;
        stage.itemNames = new ArrayList<>(itemNames);
        stage.checkPrerequisites(Collections.emptyList());
        Assert.assertEquals(stage.getStatus(), AdventureQuestController.QuestStatus.ACTIVE);
        return stage;
    }

    private static AdventureQuestEvent createItemEvent(AdventureQuestEventType type, String itemName) {
        // Construct a quest event with a concrete item payload.
        AdventureQuestEvent event = new AdventureQuestEvent();
        event.type = type;
        ItemData item = new ItemData();
        item.name = itemName;
        event.item = item;
        return event;
    }

    private static void withMapStageOutsidePoi(Runnable assertions) {
        // Isolate static map state and force world-map context (not inside a POI).
        MapStage previousMapStageInstance = MapStage.instance;
        MapStage mapStage = Mockito.mock(MapStage.class);
        Mockito.when(mapStage.isInMap()).thenReturn(false);
        MapStage.instance = mapStage;
        try {
            assertions.run();
        } finally {
            MapStage.instance = previousMapStageInstance;
        }
    }
}
