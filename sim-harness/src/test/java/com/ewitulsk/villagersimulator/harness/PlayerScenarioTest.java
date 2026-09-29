package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.content.buildings.Building;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.players.Dialogue;
import com.ewitulsk.villagersimulator.content.players.PlayerCommands;
import com.ewitulsk.villagersimulator.content.players.PlayersModule;
import com.ewitulsk.villagersimulator.content.social.Social;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3: the player as a sim agent. All in virtual time. */
class PlayerScenarioTest {

    private static EntityId join(Scenario s, String name) {
        AtomicReference<EntityId> p = new AtomicReference<>();
        s.apply(new PlayerCommands.Join("uuid-" + name, name, p::set));
        return p.get();
    }

    private static String say(Scenario s, EntityId villager, EntityId player, String option) {
        AtomicReference<String> reply = new AtomicReference<>();
        s.apply(new Dialogue.Choose(villager, player, option, reply::set));
        return reply.get();
    }

    private static String give(Scenario s, EntityId villager, EntityId player, String item) {
        AtomicReference<String> reply = new AtomicReference<>();
        s.apply(new PlayersModule.Gift(villager, player, item, reply::set));
        return reply.get();
    }

    private static EntityId building(Scenario s, EntityId village, String type) {
        return s.village(village).buildings().stream()
                .filter(b -> s.world().get(b, Buildings.BUILDING).type().path().equals(type)).findFirst().orElseThrow();
    }

    private static void standIn(Scenario s, EntityId player, EntityId building) {
        Building b = s.world().get(building, Buildings.BUILDING);
        var t = Buildings.type(s.world(), building);
        s.apply(new PlayerCommands.Position(player, b.x() + t.sizeX() / 2.0, b.y() + 1, b.z() + t.sizeZ() / 2.0));
    }

    @Test
    void joiningTwiceGivesTheSameRecord() {
        Scenario s = Scenario.start();
        EntityId a = join(s, "Alex");
        assertEquals(a, join(s, "Alex"));
    }

    @Test
    void dialogueOptionsReflectHowWellTheyKnowYou() {
        Scenario s = Scenario.start(SimTime.hours(6));
        EntityId village = s.spawnHamlet("Talkton", 3, 8);
        EntityId v = s.village(village).residents().get(0);
        EntityId me = join(s, "Alex");

        Dialogue.View first = s.world().query(Dialogue.view(v, me, ""));
        assertEquals("Hello, stranger.", first.greeting());
        Dialogue.Option invite = first.options().stream().filter(o -> o.id().equals(Dialogue.INVITE)).findFirst().orElseThrow();
        assertFalse(invite.enabled());
        assertEquals("They don't know you well enough", invite.reason());

        say(s, v, me, Dialogue.TALK);
        say(s, v, me, Dialogue.COMPLIMENT);
        give(s, v, me, "minecraft:cake");
        assertTrue(s.world().relationships().friendship(v, me) >= Dialogue.INVITE_FRIENDSHIP,
                "friendship " + s.world().relationships().friendship(v, me));
        Dialogue.View later = s.world().query(Dialogue.view(v, me, ""));
        assertTrue(later.options().stream().anyMatch(o -> o.id().equals(Dialogue.INVITE) && o.enabled()));
        assertTrue(later.greeting().startsWith("Good to see you"), later.greeting());
    }

    @Test
    void repeatingYourselfWearsThin() {
        Scenario s = Scenario.start(SimTime.hours(6));
        EntityId v = s.village(s.spawnHamlet("Talkton", 3, 8)).residents().get(0);
        EntityId me = join(s, "Alex");
        say(s, v, me, Dialogue.COMPLIMENT);
        float once = s.world().relationships().friendship(v, me);
        assertEquals("You said that already...", say(s, v, me, Dialogue.COMPLIMENT));
        assertTrue(s.world().relationships().friendship(v, me) < once);
    }

    @Test
    void giftsDependOnTheItemAndHowOftenYouGive() {
        Scenario s = Scenario.start(SimTime.hours(6));
        EntityId village = s.spawnHamlet("Giftwell", 3, 8);
        EntityId a = s.village(village).residents().get(0), b = s.village(village).residents().get(1);
        EntityId me = join(s, "Alex");

        give(s, a, me, "minecraft:diamond");
        float first = s.world().relationships().friendship(a, me);
        assertTrue(first > 3, "a diamond is welcome: " + first);
        give(s, a, me, "minecraft:diamond");
        assertTrue(s.world().relationships().friendship(a, me) - first < first, "a second gift the same day is worth less");

        assertEquals("Why would you give me this?", give(s, b, me, "minecraft:rotten_flesh"));
        assertTrue(s.world().relationships().friendship(b, me) < 0);
    }

    @Test
    void reputationSpreadsThroughGossip() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Rumourford", 5, 8);
        EntityId me = join(s, "Alex");
        EntityId friend = s.village(village).residents().get(0);
        s.world().relationships().changeFriendship(friend, me, 80);

        s.warp(SimTime.days(3));
        List<EntityId> others = s.village(village).residents().subList(1, 8);
        long heard = others.stream().filter(r -> s.world().relationships().friendship(r, me) > 0).count();
        assertTrue(heard >= 4, "most of the village has heard good things (" + heard + " of 7)");
        assertTrue(PlayerCommands.reputation(s.world(), me, village) > 0);
    }

    @Test
    void anInvitationIsBookedAndKeptWhenThePlayerShowsUp() {
        Scenario s = Scenario.start(SimTime.hours(7)); // 13:00
        EntityId village = s.spawnHamlet("Meetham", 9, 8);
        EntityId v = s.village(village).residents().stream()
                .filter(r -> !s.world().get(r, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGER).employed())
                .findFirst().orElseThrow();
        EntityId me = join(s, "Alex");
        s.world().relationships().changeFriendship(v, me, 30);

        String reply = say(s, v, me, Dialogue.INVITE);
        assertTrue(reply.startsWith("I'd love to!"), reply);
        EntityId tavern = building(s, village, "tavern");
        long meetAt = com.ewitulsk.villagersimulator.content.social.Appointments.of(s.world(), v).get(0).start();
        PlanEntry booked = s.world().get(v, Plans.PLAN).entries().stream()
                .filter(e -> tavern.equals(e.venue()) && e.start() == meetAt).findFirst().orElseThrow();

        s.warp(booked.start() - s.now());
        standIn(s, me, tavern);
        s.warp(booked.end() - s.now() + SimTime.minutes(30));
        s.apply(new PlayerCommands.Position(me, 10_000, 64, 10_000)); // leave

        List<EventRecord> kept = s.world().events().ofType(Social.EVENT_APPOINTMENT_KEPT);
        assertTrue(kept.stream().anyMatch(r -> r.actor().equals(v) && r.witnesses().contains(me)), "appointment kept");
    }

    @Test
    void hangingOutAtTheTavernMakesFriends() {
        Scenario s = Scenario.start(SimTime.hours(10)); // 16:00
        EntityId village = s.spawnHamlet("Pubton", 13, 8);
        EntityId me = join(s, "Alex");
        standIn(s, me, building(s, village, "tavern"));
        s.warp(SimTime.hours(4));
        s.apply(new PlayerCommands.Position(me, 10_000, 64, 10_000));
        long met = s.village(village).residents().stream().filter(r -> s.world().relationships().friendship(r, me) > 1).count();
        assertTrue(met >= 1, "met someone at the tavern");
    }
}
