package org.github.legioth.repeater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.router.Route;

@Route("")
public class DemoView extends VerticalLayout {
    @FunctionalInterface
    private interface SectionInitializer {
        void init(VerticalLayout container, HorizontalLayout controls, ArrayList<Person> persons, Random random);
    }

    public DemoView() {
        addDemoSection("Manually set items", DemoView::initManual);
        addDemoSection("DataProvider", DemoView::initDataProvider);
    }

    private static void initDataProvider(VerticalLayout container, HorizontalLayout controls, ArrayList<Person> persons,
            Random random) {
        ListDataProvider<Person> dataProvider = DataProvider.ofCollection(persons);

        Repeater<Person> repeater = new Repeater<>(container, PersonPanel::new, PersonPanel::setPerson);
        repeater.setDataProvider(dataProvider);

        controls.add(new Button("Sort by title", event -> {
            dataProvider.setSortOrder(Person::getTitle, SortDirection.ASCENDING);
        }), new Button("Clear sorting", event -> {
            dataProvider.setSortComparator(null);
        }), new Button("Show only testers", event -> {
            dataProvider.setFilterByValue(Person::getOccupation, "Tester");
        }), new Button("Clear filter", event -> {
            dataProvider.clearFilters();
        }), new Button("Update first", event -> {
            Person first = persons.get(0);
            updatePerson(first, random);
            dataProvider.refreshItem(first);
        }));
    }

    private static void initManual(VerticalLayout container, HorizontalLayout controls, ArrayList<Person> persons,
            Random random) {
        Repeater<Person> repeater = new Repeater<>(container, PersonPanel::new, PersonPanel::setPerson);
        repeater.showItems(persons);

        controls.add(new Button("Remove random", event -> {
            int size = persons.size();
            if (size < 1) {
                Notification.show("Too few items");
                return;
            }
            persons.remove(random.nextInt(size));

            repeater.showItems(persons);
        }), new Button("Remove last", event -> {
            int size = persons.size();
            if (size < 1) {
                Notification.show("Too few items");
                return;
            }
            persons.remove(size - 1);

            repeater.showItems(persons);
        }), new Button("Add random", event -> {
            persons.add(random.nextInt(persons.size() + 1), Person.generate(random));

            repeater.showItems(persons);
        }), new Button("Add last", event -> {
            persons.add(Person.generate(random));

            repeater.showItems(persons);
        }), new Button("Swap random", event -> {
            if (persons.size() < 2) {
                Notification.show("Too few items");
                return;
            }

            swap(persons, random);

            repeater.showItems(persons);
        }), new Button("Update first", event -> {
            int size = persons.size();
            if (size < 1) {
                Notification.show("Too few items");
                return;
            }
            Person target = persons.get(0);
            updatePerson(target, random);

            repeater.showItems(persons);
        }));
    }

    private static void swap(ArrayList<Person> persons, Random random) {
        int size = persons.size();
        int first = random.nextInt(size);
        int second = random.nextInt(size - 1);
        if (second >= first) {
            second++;
        }

        Collections.swap(persons, first, second);
    }

    private static void updatePerson(Person target, Random random) {
        Person newData = Person.generate(random);
        switch (random.nextInt(3)) {
        case 0:
            target.setTitle(newData.getTitle());
            break;
        case 1:
            target.setName(newData.getName());
            break;
        case 2:
            target.setOccupation(newData.getOccupation());
            break;
        }
    }

    public void addDemoSection(String caption, SectionInitializer initializer) {
        VerticalLayout container = new VerticalLayout();
        container.setPadding(false);
        // Not using 42 since it only gives one Tester
        Random random = new Random(43);
        ArrayList<Person> persons = Person.generate(8, random);
        HorizontalLayout controls = new HorizontalLayout();

        initializer.init(container, controls, persons, random);

        add(new H1(caption), new VerticalLayout(controls, container));
    }
}
