package com.diego.sorcery.service;

import com.diego.sorcery.model.SorceryModels.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SelectionService {

    private record Candidate(Applicant applicant, int score, boolean invited) {
    }

    private static final Comparator<Candidate> RANKING =
        Comparator.comparingInt(Candidate::score).reversed()
            .thenComparingInt(c -> c.applicant().age())
            .thenComparing(c -> c.applicant().familyName())
            .thenComparing(c -> c.applicant().firstName())
            .thenComparing(c -> c.applicant().id());

    public SelectionResult select(
        List<Applicant> applications, CouncilRules rules
    ) {
        validate(applications, rules);

        List<Candidate> candidates = new ArrayList<>();
        List<RejectedApplicant> rejected = new ArrayList<>();

        for (Applicant applicant : applications) {
            boolean invited = rules.invitations().contains(
                applicant.fullName());

            RejectionReason veto = invited ? null : veto(applicant, rules);

            if (veto != null) {
                rejected.add(new RejectedApplicant(
                    applicant, veto, null, null));
            }
            else {
                candidates.add(new Candidate(
                    applicant, admissionScore(applicant, rules), invited));
            }
        }

        candidates.sort(RANKING);

        Map<String, Integer> positions = new java.util.HashMap<>();

        for (int index = 0; index < candidates.size(); index++) {
            positions.put(candidates.get(index).applicant().id(), index + 1);
        }

        int invitationCount = (int) candidates.stream()
            .filter(Candidate::invited)
            .count();

        if (invitationCount > rules.places()) {
            throw new IllegalArgumentException(
                "Invited applicants exceed the available places");
        }

        int ordinaryPlaces = rules.places() - invitationCount;
        List<Candidate> selected = new ArrayList<>();

        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);

            if (candidate.invited()) {
                selected.add(candidate);
            }
            else if (ordinaryPlaces > 0) {
                selected.add(candidate);
                ordinaryPlaces--;
            }
            else {
                rejected.add(new RejectedApplicant(
                    candidate.applicant(),
                    RejectionReason.NO_PLACES_LEFT,
                    index + 1,
                    candidate.score()));
            }
        }

        // Selection preserves the admission ranking.
        // Invitations reserve places but do not receive priority for houses.
        List<List<String>> occupants = new ArrayList<>();

        for (HouseRule house : rules.houses()) {
            occupants.add(new ArrayList<>());
        }

        List<AcceptedApplicant> accepted = new ArrayList<>();

        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            int bestHouse = -1;
            int bestScore = Integer.MIN_VALUE;

            for (int h = 0; h < rules.houses().size(); h++) {
                HouseRule house = rules.houses().get(h);

                if (occupants.get(h).size() >= house.beds()) {
                    continue;
                }

                int score = traitScore(candidate.applicant(), house.points());

                // Strict comparison preserves JSON order in a tie.
                if (bestHouse == -1 || score > bestScore) {
                    bestHouse = h;
                    bestScore = score;
                }
            }

            if (bestHouse == -1) {
                throw new IllegalStateException("No house has a free bed");
            }

            occupants.get(bestHouse).add(candidate.applicant().id());

            accepted.add(new AcceptedApplicant(
                candidate.applicant(),
                positions.get(candidate.applicant().id()),
                candidate.score(),
                candidate.invited(),
                rules.houses().get(bestHouse).name(),
                bestScore));
        }

        List<HouseState> houses = new ArrayList<>();

        for (int h = 0; h < rules.houses().size(); h++) {
            HouseRule house = rules.houses().get(h);

            houses.add(new HouseState(
                house.name(), house.beds(), List.copyOf(occupants.get(h))));
        }

        return new SelectionResult(
            rules.year(),
            List.copyOf(accepted),
            List.copyOf(rejected),
            List.copyOf(houses));
    }

    private RejectionReason veto(Applicant applicant, CouncilRules rules) {
        if (rules.bannedFamilies().contains(applicant.familyName())) {
            return RejectionReason.BANNED_FAMILY;
        }

        if (applicant.age() < rules.ageRange().min() ||
            applicant.age() > rules.ageRange().max()) {

            return RejectionReason.AGE_OUT_OF_RANGE;
        }

        if (applicant.weaknesses().stream().anyMatch(
            rules.unacceptableWeaknesses()::contains)) {

            return RejectionReason.UNACCEPTABLE_WEAKNESS;
        }

        if (applicant.applicationDate().isBefore(
                rules.applicationDates().from()) ||
            applicant.applicationDate().isAfter(
                rules.applicationDates().to())) {

            return RejectionReason.APPLICATION_OUT_OF_DATES;
        }

        return null;
    }

    private int admissionScore(Applicant applicant, CouncilRules rules) {
        int score = traitScore(applicant, rules.points());

        for (AgePoints group : rules.points().age()) {
            if (applicant.age() >= group.from() &&
                applicant.age() <= group.to()) {

                score += group.points();
                break;
            }
        }

        return score;
    }

    private int traitScore(Applicant applicant, Points points) {
        return sum(applicant.virtues(), points.virtue())
            + sum(applicant.weaknesses(), points.weakness())
            + points.family().getOrDefault(applicant.familyName(), 0);
    }

    private int sum(List<String> values, Map<String, Integer> points) {
        return values.stream()
            .mapToInt(value -> points.getOrDefault(value, 0))
            .sum();
    }

    private void validate(List<Applicant> applications, CouncilRules rules) {
        if (applications == null || rules == null) {
            throw new IllegalArgumentException(
                "Applications and council rules are required");
        }

        if (rules.places() < 0 ||
            rules.houses() == null || rules.houses().size() != 4) {

            throw new IllegalArgumentException(
                "Places must be nonnegative and exactly four houses are required");
        }

        Set<String> houseNames = new HashSet<>();
        long beds = 0;

        for (HouseRule house : rules.houses()) {
            if (house == null || house.name() == null ||
                house.name().isBlank() || house.beds() < 0 ||
                !houseNames.add(house.name())) {

                throw new IllegalArgumentException(
                    "House names must be unique and beds nonnegative");
            }

            validatePoints(house.points(), false);
            beds += house.beds();
        }

        if (beds != rules.places()) {
            throw new IllegalArgumentException(
                "Total house beds must equal the available places");
        }

        if (rules.year() == null || rules.year().isBlank() ||
            rules.ageRange() == null ||
            rules.ageRange().min() < 0 ||
            rules.ageRange().min() > rules.ageRange().max() ||
            rules.applicationDates() == null ||
            rules.applicationDates().from() == null ||
            rules.applicationDates().to() == null ||
            rules.applicationDates().from().isAfter(
                rules.applicationDates().to()) ||
            !validStrings(rules.bannedFamilies()) ||
            !validStrings(rules.unacceptableWeaknesses()) ||
            !validStrings(rules.invitations())) {

            throw new IllegalArgumentException("Invalid council rules");
        }

        validatePoints(rules.points(), true);

        List<AgePoints> ageGroups = rules.points().age();

        for (int i = 0; i < ageGroups.size(); i++) {
            AgePoints group = ageGroups.get(i);

            if (group == null || group.from() < 0 ||
                group.from() > group.to()) {

                throw new IllegalArgumentException("Invalid age points range");
            }

            for (int j = 0; j < i; j++) {
                AgePoints previous = ageGroups.get(j);

                if (group.from() <= previous.to() &&
                    previous.from() <= group.to()) {

                    throw new IllegalArgumentException(
                        "Age points ranges must not overlap");
                }
            }
        }

        Set<String> ids = new HashSet<>();

        for (Applicant applicant : applications) {
            if (applicant == null ||
                applicant.id() == null || applicant.id().isBlank() ||
                !ids.add(applicant.id()) ||
                applicant.firstName() == null ||
                applicant.firstName().isBlank() ||
                applicant.familyName() == null ||
                applicant.familyName().isBlank() ||
                applicant.age() < 0 ||
                applicant.applicationDate() == null ||
                !validStrings(applicant.virtues()) ||
                applicant.virtues().isEmpty() ||
                !validStrings(applicant.weaknesses())) {

                throw new IllegalArgumentException(
                    "Invalid applicant or duplicate applicant ID");
            }
        }
    }

    private void validatePoints(Points points, boolean requireAge) {
        if (points == null ||
            !validMap(points.virtue()) ||
            !validMap(points.family()) ||
            !validMap(points.weakness()) ||
            (requireAge && points.age() == null)) {

            throw new IllegalArgumentException("Invalid points configuration");
        }
    }

    private boolean validMap(Map<String, Integer> points) {
        return points != null && points.entrySet().stream().allMatch(
            entry -> entry.getKey() != null &&
                !entry.getKey().isBlank() && entry.getValue() != null);
    }

    private boolean validStrings(List<String> values) {
        return values != null && values.stream().allMatch(
            value -> value != null && !value.isBlank());
    }
}