package io.quarkus.arc.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Grouping {
    @FunctionalInterface
    public interface Builder<E, G> {
        G apply(int id, List<E> elements);
    }

    public static <E, G> List<G> of(Collection<E> elements, int groupLimit, Builder<E, G> builder) {
        List<G> groups = new ArrayList<>();
        List<E> elementsInGroup = new ArrayList<>();
        int groupCounter = 0;
        int elementsCounter = 0;
        for (E element : elements) {
            elementsInGroup.add(element);
            elementsCounter++;

            if (elementsCounter == groupLimit) {
                groups.add(builder.apply(groupCounter, List.copyOf(elementsInGroup)));

                groupCounter++;
                elementsCounter = 0;
                elementsInGroup.clear();
            }
        }
        if (!elementsInGroup.isEmpty()) {
            groups.add(builder.apply(groupCounter, List.copyOf(elementsInGroup)));
        }
        return List.copyOf(groups);
    }
}
