package com.diego.sorcery.service;

import com.diego.sorcery.model.SorceryModels.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class SelectionServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 15);

    private final SelectionService service = new SelectionService();

    @Test
    public void vetoesFollowTheRequiredPriority() {
        List<Applicant> applicants = List.of(
            applicant("A", "Ana", "Banned", 9,
                List.of("cruelty"), DATE.minusYears(1)),
            applicant("B", "Bea", "Other", 9,
                List.of("cruelty"), DATE.minusYears(1)),
            applicant("C", "Carla", "Other", 12,
                List.of("cruelty"), DATE.minusYears(1)),
            applicant("D", "Diana", "Other", 12,
                List.of(), DATE.minusYears(1)));

        SelectionResult result = service.select(
            applicants, rules(List.of(), emptyPoints(), houses()));

        assertEquals(0, result.accepted().size());
        assertEquals(List.of(
            RejectionReason.BANNED_FAMILY,
            RejectionReason.AGE_OUT_OF_RANGE,
            RejectionReason.UNACCEPTABLE_WEAKNESS,
            RejectionReason.APPLICATION_OUT_OF_DATES),
            result.rejected().stream()
                .map(RejectedApplicant::reason).toList());
    }

    @Test
    public void invitationOverridesVetoesAndReservesAPlace() {
        List<Applicant> applicants = List.of(
            applicant("A", "Ana", "Other", 12),
            applicant("B", "Bea", "Other", 12),
            applicant("C", "Carla", "Other", 12),
            applicant("D", "Diana", "Other", 12),
            applicant("I", "Invited", "Banned", 9,
                List.of("cruelty"), DATE.minusYears(1)));

        Points points = new Points(
            Map.of(), Map.of(), Map.of("cruelty", -10), List.of());

        SelectionResult result = service.select(
            applicants, rules(List.of("Invited Banned"), points, houses()));

        assertEquals(4, result.accepted().size());

        AcceptedApplicant invited = result.accepted().stream()
            .filter(AcceptedApplicant::invited)
            .findFirst().orElseThrow();

        assertEquals("I", invited.applicant().id());
        assertEquals(5, invited.position());
        assertEquals(-10, invited.admissionScore());

        assertEquals(1, result.rejected().size());
        assertEquals("D", result.rejected().get(0).applicant().id());
        assertEquals(RejectionReason.NO_PLACES_LEFT,
            result.rejected().get(0).reason());
        assertEquals(Integer.valueOf(4),
            result.rejected().get(0).position());
    }

    @Test
    public void rankingUsesAgeThenFamilyThenFirstName() {
        List<Applicant> applicants = List.of(
            applicant("A", "Zoe", "Alpha", 13),
            applicant("B", "Ana", "Beta", 12),
            applicant("C", "Zoe", "Alpha", 12),
            applicant("D", "Ana", "Alpha", 12));

        SelectionResult result = service.select(
            applicants, rules(List.of(), emptyPoints(), houses()));

        assertEquals(List.of("D", "C", "B", "A"),
            result.accepted().stream()
                .map(a -> a.applicant().id()).toList());
    }

    @Test
    public void admissionAddsAllTraitsAndAgeAndIgnoresUnknownValues() {
        Applicant applicant = new Applicant(
            "A", "Ana", "Alpha", 12,
            List.of("courage", "loyalty", "unknown"),
            List.of("shyness", "impatience", "unknown"), DATE);

        Points points = new Points(
            Map.of("courage", 5, "loyalty", 3),
            Map.of("Alpha", 2),
            Map.of("shyness", -1, "impatience", -4),
            List.of(new AgePoints(10, 12, 7)));

        SelectionResult result = service.select(
            List.of(applicant), rules(List.of(), points, houses()));

        assertEquals(12, result.accepted().get(0).admissionScore());
        assertEquals(0, result.accepted().get(0).houseScore());
    }

    @Test
    public void houseScoringUsesTraitsAndFallsBackWhenFull() {
        Points preferred = new Points(
            Map.of("courage", 5),
            Map.of("Alpha", 2),
            Map.of("shyness", 3),
            List.of());

        List<HouseRule> houses = List.of(
            new HouseRule("Lion", 1, emptyPoints()),
            new HouseRule("Serpent", 1, preferred),
            new HouseRule("Raven", 1, emptyPoints()),
            new HouseRule("Badger", 1, emptyPoints()));

        List<Applicant> applicants = List.of(
            applicant("A", "Ana", "Alpha", 12,
                List.of("shyness"), DATE),
            applicant("B", "Bea", "Alpha", 12,
                List.of("shyness"), DATE));

        SelectionResult result = service.select(
            applicants, rules(List.of(), emptyPoints(), houses));

        assertEquals("Serpent", result.accepted().get(0).house());
        assertEquals(10, result.accepted().get(0).houseScore());
        assertEquals("Lion", result.accepted().get(1).house());
        assertEquals(0, result.accepted().get(1).houseScore());
    }

    @Test
    public void zeroScoreHouseTiesFollowFileOrderAndRespectCapacity() {
        List<Applicant> applicants = List.of(
            applicant("A", "Ana", "Other", 12),
            applicant("B", "Bea", "Other", 12),
            applicant("C", "Carla", "Other", 12),
            applicant("D", "Diana", "Other", 12),
            applicant("E", "Eva", "Other", 12));

        SelectionResult result = service.select(
            applicants, rules(List.of(), emptyPoints(), houses()));

        assertEquals(List.of("Lion", "Serpent", "Raven", "Badger"),
            result.accepted().stream()
                .map(AcceptedApplicant::house).toList());

        for (HouseState house : result.houses()) {
            assertEquals(1, house.occupied());
            assertEquals(0, house.available());
        }

        assertEquals("E", result.rejected().get(0).applicant().id());
        assertEquals(Integer.valueOf(5),
            result.rejected().get(0).position());
    }

    @Test
    public void dateAndAgeBoundariesAreInclusive() {
        List<Applicant> applicants = List.of(
            applicant("A", "Ana", "Other", 10, List.of(),
                LocalDate.of(2026, 6, 1)),
            applicant("B", "Bea", "Other", 18, List.of(),
                LocalDate.of(2026, 6, 30)));

        SelectionResult result = service.select(
            applicants, rules(List.of(), emptyPoints(), houses()));

        assertEquals(2, result.accepted().size());
        assertTrue(result.rejected().isEmpty());
    }

    @Test
    public void duplicateApplicantIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            service.select(
                List.of(
                    applicant("A", "Ana", "Other", 12),
                    applicant("A", "Bea", "Other", 12)),
                rules(List.of(), emptyPoints(), houses())));
    }

    private static Applicant applicant(
        String id, String firstName, String familyName, int age
    ) {
        return applicant(id, firstName, familyName, age, List.of(), DATE);
    }

    private static Applicant applicant(
        String id, String firstName, String familyName, int age,
        List<String> weaknesses, LocalDate date
    ) {
        return new Applicant(
            id, firstName, familyName, age,
            List.of("courage"), weaknesses, date);
    }

    private static Points emptyPoints() {
        return new Points(Map.of(), Map.of(), Map.of(), List.of());
    }

    private static List<HouseRule> houses() {
        return List.of(
            new HouseRule("Lion", 1, emptyPoints()),
            new HouseRule("Serpent", 1, emptyPoints()),
            new HouseRule("Raven", 1, emptyPoints()),
            new HouseRule("Badger", 1, emptyPoints()));
    }

    private static CouncilRules rules(
        List<String> invitations, Points points, List<HouseRule> houses
    ) {
        return new CouncilRules(
            "2026-2027",
            new DateRange(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)),
            new AgeRange(10, 18),
            4,
            List.of("Banned"),
            List.of("cruelty"),
            invitations,
            points,
            houses);
    }
}