package eu.amadeos.apps.bookeditor.domain.book;

import java.util.Objects;

public record Person(String name, Role role) {
    public Person {
        name = name == null ? "" : name.strip();
        Objects.requireNonNull(role, "role");
    }
}
