package org.github.legioth.repeater;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

public class PersonPanel extends Div {
    private final Span title = new Span();
    private final Span name = new Span();
    private final Span occupation = new Span();

    public PersonPanel() {
        this(null);
    }

    public PersonPanel(Person person) {
        add(title, new Text(" "), name, new Text(" is a "), occupation);

        setPerson(person);
    }

    public void setPerson(Person person) {
        if (person == null) {
            person = new Person("", "", "");
        }
        title.setText(person.getTitle());
        name.setText(person.getName());
        occupation.setText(person.getOccupation());
    }
}
