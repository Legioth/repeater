package org.github.legioth.repeater;

import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Person {
    private String title;

    private String name;

    private String occupation;

    public Person(String title, String name, String occupation) {
        this.title = title;
        this.name = name;
        this.occupation = occupation;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public static Person generate(Random random) {
        return new Person(pick(random, "CEO", "VP", "Manager", "Worker", "Trainee"),
                pick(random, "Bob", "Jim", "Rod", "Kim", "Ted", "Hal", "Ann", "Zoe", "Han"),
                pick(random, "Designer", "Programmer", "Tester", "Marketer", "Researcher"));
    }

    public static ArrayList<Person> generate(int count, Random random) {
        return IntStream.range(0, count).mapToObj(ignore -> generate(random))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String pick(Random random, String... strings) {
        return strings[random.nextInt(strings.length)];
    }
}
