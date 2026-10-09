package com.diego.sorcery.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SorceryModels {

    private SorceryModels() {
    }

    public record Applicant(
        String id,
        String firstName,
        String familyName,
        int age,
        List<String> virtues,
        List<String> weaknesses,
        LocalDate applicationDate
    ) {
        public String fullName() {
            return firstName + " " + familyName;
        }
    }

    public record DateRange(LocalDate from, LocalDate to) {
    }

    public record AgeRange(int min, int max) {
    }

    public record AgePoints(int from, int to, int points) {
    }

    public record Points(
        Map<String, Integer> virtue,
        Map<String, Integer> family,
        Map<String, Integer> weakness,
        List<AgePoints> age
    ) {
    }

    public record HouseRule(String name, int beds, Points points) {
    }

    public record CouncilRules(
        String year,
        DateRange applicationDates,
        AgeRange ageRange,
        int places,
        List<String> bannedFamilies,
        List<String> unacceptableWeaknesses,
        List<String> invitations,
        Points points,
        List<HouseRule> houses
    ) {
    }

    public enum RejectionReason {
        BANNED_FAMILY,
        AGE_OUT_OF_RANGE,
        UNACCEPTABLE_WEAKNESS,
        APPLICATION_OUT_OF_DATES,
        NO_PLACES_LEFT
    }

    public record AcceptedApplicant(
        Applicant applicant,
        int position,
        int admissionScore,
        boolean invited,
        String house,
        int houseScore
    ) {
    }

    public record RejectedApplicant(
        Applicant applicant,
        RejectionReason reason,
        Integer position,
        Integer admissionScore
    ) {
    }

    public record HouseState(
        String name,
        int beds,
        List<String> applicantIds
    ) {
        public int occupied() {
            return applicantIds.size();
        }

        public int available() {
            return beds - occupied();
        }
    }

    public record SelectionResult(
        String year,
        List<AcceptedApplicant> accepted,
        List<RejectedApplicant> rejected,
        List<HouseState> houses
    ) {
    }
}