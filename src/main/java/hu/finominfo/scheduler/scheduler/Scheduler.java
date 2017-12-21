package hu.finominfo.scheduler.scheduler;

import hu.finominfo.scheduler.people.Person;

import java.lang.reflect.Array;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by kks on 2017.12.18..
 */
public class Scheduler {
    private final Random random = new Random();
    private final Map<String, Person> people;
    private final Map<Integer, Set<String>> scheduled = new HashMap<>();
    private final Map<Integer, Set<String>> hated = new HashMap<>();
    private final List<Integer> weekends = new ArrayList<>();
    private final int numOfDays;
    private final LocalDate localDate;
    private volatile boolean weekendIsScheduledNow = false;

    public Scheduler(Map<String, Person> people, LocalDate date) {
        this.people = people;
        this.numOfDays = date.lengthOfMonth();
        for (int i = 0; i < numOfDays + 1; i++) {
            scheduled.put(i, new HashSet<>());
            hated.put(i, new HashSet<>());
        }
        this.localDate = date.withDayOfMonth(1);
        setHated();
        setWanted();
        countWeekends();
        setWeekends();
        if (!weekendIsScheduledNow) {
            setWeekdays();
        }
    }


    // --------------------------------------------------------------------------------------------------

    private void setHated() {
        people.entrySet().stream().forEach(entry -> entry.getValue().getHatedDays().forEach(hatedDay -> {
            Set<String> set = hated.get(hatedDay);
            set.add(entry.getKey());
            if (set.size() > people.size() - 2) {
                throw new RuntimeException("Too much people hate the same day: " +
                        set.stream().map(e -> e.toString() + " ").reduce("", String::concat));
            }
        }));
        hated.entrySet().stream().forEach(entry -> {
            Set<String> set = entry.getValue();
            if (set.size() == people.size() - 2) { //Only 2 person remain for that day
                final Set<String> possibleNames = new HashSet<>();
                possibleNames.addAll(people.keySet());
                possibleNames.removeAll(set);
                scheduled.get(entry.getKey()).addAll(possibleNames);
                System.out.println(possibleNames.stream().map(e -> e.toString() + " ").reduce("", String::concat) +
                        " were added to " + entry.getKey() + ", because everybody else hate that day.");
            }

        });
    }

    // --------------------------------------------------------------------------------------------------

    private void setWanted() {
        people.entrySet().stream().forEach(entry -> entry.getValue().getWantedDays().forEach(wantedDay -> {
            Set<String> set = scheduled.get(wantedDay);
            set.add(entry.getKey());
            if (set.size() == 2) {
                if (set.stream().allMatch(name -> !people.get(name).isExperienced())) {
                    throw new RuntimeException("Two not experienced people want the same day: " +
                            set.stream().map(e -> e.toString() + " ").reduce("", String::concat));
                }
            }
            if (set.size() > 2) {
                throw new RuntimeException("More than two people want the same day: " +
                        set.stream().map(e -> e.toString() + " ").reduce("", String::concat));
            }
        }));
    }

    // --------------------------------------------------------------------------------------------------

    private void countWeekends() {
        LocalDate result = localDate;
        while (result.getMonthValue() == localDate.getMonthValue()) {
            if (result.getDayOfWeek() == DayOfWeek.SATURDAY) {
                weekends.add(result.getDayOfMonth());
            }
            result = result.plusDays(1);
        }
    }

    private void uniteSaturdaysAndSundays() {
        for (int i = 0; i < weekends.size(); i++) {
            int saturdayNumber = weekends.get(i);
            Set<String> saturday = scheduled.get(saturdayNumber);
            Set<String> sunday = scheduled.get(saturdayNumber + 1);
            saturday.addAll(sunday);
            sunday.addAll(saturday);
            if (saturday.size() > 2) {
                throw new RuntimeException("More than two people want the " + (i + 1) + ". weekend: " +
                        saturday.stream().map(e -> e.toString() + " ").reduce("", String::concat));
            }
        }
    }

    private void setWeekends() {
        uniteSaturdaysAndSundays();
        setMostCriticalWeekends();
        uniteSaturdaysAndSundays();
        setRemainingWeekends();
    }

    private void setMostCriticalWeekends() {
        final List<Set<String>> possibilitiesOfWeekends = new ArrayList<>();
        final List<Integer> sizeOfPossibilitiesOfWeekends = new ArrayList<>();

        for (int i = 0; i < weekends.size(); i++) {
            int saturdayNumber = weekends.get(i);
            Set<String> saturday = scheduled.get(saturdayNumber);
            final Set<String> possibilities = getPossibilities(saturdayNumber);
            possibilitiesOfWeekends.add(possibilities);
            if (saturday.size() + possibilities.size() < 2) {
                throw new RuntimeException("There is not enough people on " + saturdayNumber + ". " +
                        saturday.stream().map(e -> e.toString() + " ").reduce("", String::concat));
            } else if (saturday.size() + possibilities.size() == 2) {
                saturday.addAll(possibilities);
                if (saturday.stream().allMatch(name -> !people.get(name).isExperienced())) {
                    throw new RuntimeException("Two not experienced people on the same weekend: " +
                            saturday.stream().map(e -> e.toString() + " ").reduce("", String::concat));
                }
                sizeOfPossibilitiesOfWeekends.add(1000);
            } else {
                sizeOfPossibilitiesOfWeekends.add(possibilities.size());
            }
        }
        List<Integer> threeLowestSizeOfPossibilities = getThreeLowest(sizeOfPossibilitiesOfWeekends);
        Set<String> saturdayWas = null;
        int iWas = -10;

        if (threeLowestSizeOfPossibilities.get(0) < threeLowestSizeOfPossibilities.get(2)) {
            for (int i = 0; i < sizeOfPossibilitiesOfWeekends.size(); i++) {
                if (threeLowestSizeOfPossibilities.get(0) == sizeOfPossibilitiesOfWeekends.get(i)) {
                    int saturdayNumber = weekends.get(i);
                    Set<String> saturday = scheduled.get(saturdayNumber);
                    final Set<String> possibilities = getPossibilities(saturdayNumber);
                    thereIsPossibleNames(possibilities, saturday);
                    iWas = i;
                    saturdayWas = saturday;
                    sizeOfPossibilitiesOfWeekends.set(i, 1000);
                    System.out.println("1 weekend was set: " + saturdayNumber);
                    break;
                }
            }
        }

        if (threeLowestSizeOfPossibilities.get(1) < threeLowestSizeOfPossibilities.get(2)) {
            for (int i = 0; i < sizeOfPossibilitiesOfWeekends.size(); i++) {
                if (threeLowestSizeOfPossibilities.get(1) == sizeOfPossibilitiesOfWeekends.get(i)) {
                    int saturdayNumber = weekends.get(i);
                    Set<String> saturday = scheduled.get(saturdayNumber);
                    final Set<String> possibilities = getPossibilities(saturdayNumber);
                    if (Math.abs(iWas - i) == 1) {
                        possibilities.removeAll(saturdayWas);
                    }
                    thereIsPossibleNames(possibilities, saturday);
                    System.out.println("2 weekend was set: " + saturdayNumber);
                    break;
                }
            }
        }
    }

    private List<Integer> getThreeLowest(List<Integer> list) {
        int[] lowestValues = new int[3];
        Arrays.fill(lowestValues, Integer.MAX_VALUE);
        for (int n : list) {
            if (n < lowestValues[2]) {
                lowestValues[2] = n;
                Arrays.sort(lowestValues);
            }
        }
        List<Integer> intList = new ArrayList<Integer>();
        for (int index = 0; index < lowestValues.length; index++) {
            intList.add(lowestValues[index]);
        }
        return intList;
    }

    private void setRemainingWeekends() {
        for (int i = 0; i < weekends.size(); i++) {
            int saturdayNumber = weekends.get(i);
            Set<String> saturday = scheduled.get(saturdayNumber);
            while (saturday.size() < 2) {
                weekendIsScheduledNow = true;
                final Set<String> possibilities = getPossibilities(saturdayNumber);
                if (possibilities.isEmpty()) {
                    noPossibleNames(i, saturday);
                } else {
                    thereIsPossibleNames(possibilities, saturday);
                }
                uniteSaturdaysAndSundays();
            }
        }
    }

    private Set<String> getPossibilities(int saturdayNumber) {

        int sundayNumber = saturdayNumber + 1;
        int fridayNumber = saturdayNumber - 1;
        int mondayNumber = saturdayNumber + 2;

        final Set<String> possibilities = new HashSet<>();
        possibilities.addAll(people.keySet());
        weekends.stream().forEach(satNum -> possibilities.removeAll(scheduled.get(satNum)));

        possibilities.removeAll(hated.get(saturdayNumber));
        possibilities.removeAll(hated.get(sundayNumber));

        possibilities.removeAll(scheduled.get(fridayNumber));
        possibilities.removeAll(scheduled.get(mondayNumber));
        return possibilities;
    }

    private void noPossibleNames(int i, Set<String> saturday) {
        int otherSaturdayNumber = weekends.get(i < weekends.size() / 2 ? weekends.size() - 1 : 0);
        Set<String> otherSaturday = scheduled.get(otherSaturdayNumber);
        if (saturday.isEmpty()) {
            saturday.addAll(otherSaturday);
        } else {
            if (people.get(saturday.iterator().next()).isExperienced()) {
                saturday.add(otherSaturday.iterator().next());
            } else {
                Optional<String> anyExperienced = otherSaturday.stream().filter(name -> people.get(name).isExperienced()).findAny();
                if (anyExperienced.isPresent()) {
                    saturday.add(anyExperienced.get());
                } else {
                    throw new RuntimeException("There is no experienced people on " + otherSaturdayNumber + ". " +
                            otherSaturday.stream().map(e -> e.toString() + " ").reduce("", String::concat));
                }
            }
        }
    }

    private void thereIsPossibleNames(Set<String> possibilities, Set<String> saturday) {
        if (saturday.isEmpty()) {
            try2AddNoFoFirst(saturday, possibilities);
        } else {
            if (people.get(saturday.iterator().next()).isExperienced()) {
                try2AddNoFoFirst(saturday, possibilities);
            } else {
                try2AddExperienced(saturday, possibilities);
            }
        }
    }

    private void try2AddNoFoFirst(Set<String> saturday, Set<String> possibilities) {
        Optional<String> anyNoFo = possibilities.stream().filter(name -> !people.get(name).isExperienced()).findAny();
        if (anyNoFo.isPresent()) {
            saturday.add(anyNoFo.get());
        } else {
            try2AddExperienced(saturday, possibilities);
        }
    }

    private void try2AddExperienced(Set<String> saturday, Set<String> possibilities) {
        Optional<String> anyExperienced = possibilities.stream().filter(name -> people.get(name).isExperienced()).findAny();
        if (anyExperienced.isPresent()) {
            saturday.add(anyExperienced.get());
        } else {
            throw new RuntimeException("There is no experienced people in possibleNames " +
                    possibilities.stream().map(e -> e.toString() + " ").reduce("", String::concat));
        }
    }

    // --------------------------------------------------------------------------------------------------

    private void setWeekdays() {

    }

    // --------------------------------------------------------------------------------------------------

    public Map<Integer, Set<String>> getScheduled() {
        return scheduled;
    }
}
