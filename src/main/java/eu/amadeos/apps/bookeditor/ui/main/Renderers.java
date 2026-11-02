package eu.amadeos.apps.bookeditor.ui.main;

import eu.amadeos.commons.i18n.Messages;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;
import java.util.function.Function;

/** List renderers that show an enum value by its text in the program's language. */
public final class Renderers {
    private Renderers() {
    }

    /** Shows each value as {@code msg.get(prefix + key(value))}. */
    public static <T> ListCellRenderer<Object> keyed(Messages msg, String prefix, Function<T, String> key) {
        return new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                String text = value == null ? "" : msg.get(prefix + key.apply((T) value));
                return super.getListCellRendererComponent(list, text, index, sel, focus);
            }
        };
    }
}
