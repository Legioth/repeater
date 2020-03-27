package org.github.legioth.repeater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.function.SerializableBiConsumer;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.shared.Registration;

/**
 * A controller for multiple repeated items in a container. The repeater
 * maintains a list of components in a container based on a corresponding list
 * of data items. When given an updated list, it takes care of adding, removing
 * and reordering the components efficiently without the help of fine-grained
 * information about the changes in the list.
 * <p>
 * The minimal configuration is based on a container component and a callback
 * for creating a new child component based on an item instance. It is also
 * possible a callback that is run to update existing components and a callback
 * that is run when an item has been removed.
 * <p>
 * The items can be supplied automatically from a data provider or manually as a
 * list, array or a stream. When using a data provider, then
 * {@link DataProvider#refreshAll()} or {@link DataProvider#refreshItem(Object)}
 * should be used to trigger the components to update. This also happens
 * automatically when applying a filter or sort order to an existing data
 * provider. When setting items manually, then the same method should be run
 * again with the items to show to trigger the components to update.
 * 
 * @param <T>
 *            the item type
 */
public class Repeater<T> {
    @SuppressWarnings("rawtypes")
    private static final SerializableBiConsumer NO_OP = (a, b) -> {
        // nop
    };

    private final SerializableFunction<T, ? extends Component> componentFactory;

    private final SerializableBiConsumer<Component, T> updater;
    private final SerializableBiConsumer<Component, T> remover;

    private final HashMap<T, Component> components = new HashMap<>();

    private final Component container;

    private Registration dataProviderRegistration;

    /**
     * Creates a new repeater based on a container component and a callback for
     * creating new child components. This constructor doesn't provide a way of
     * updating existing child components - it is assumed that the items are
     * either immutable or capable or receiving separate events to update
     * themselves.
     * 
     * @param container
     *            an empty container to show items in, not <code>null</code>
     * @param componentFactory
     *            a callback for creating a new child component for an item
     */
    public Repeater(Component container, SerializableFunction<T, Component> componentFactory) {
        <Component> this(container, componentFactory, (SerializableBiConsumer<Component, T>) NO_OP);
    }

    /**
     * Creates a new repeater based on a container component, a callback for
     * creating new child components and a callback for updating existing items.
     * The update callback can be used to ensure the child component is updated
     * to show the latest values from the item instance.
     * 
     * @param container
     *            an empty container to show items in, not <code>null</code>
     * @param componentFactory
     *            a callback for creating a new child component for an item
     * @param updateHandler
     *            a callback that will be run when a component is reused with
     *            the same item as before
     */
    public <C extends Component> Repeater(Component container, SerializableFunction<T, C> componentFactory,
            SerializableBiConsumer<C, T> updateHandler) {
        <C> this(container, componentFactory, updateHandler, (SerializableBiConsumer<C, T>) NO_OP);
    }

    /**
     * Creates a new repeater based on a container component, a callback for
     * creating new child components and a callback for updating existing items.
     * The update callback can be used to ensure the child component is updated
     * to show the latest values from the item instance. The remove callback can
     * be used to clean up resources.
     * 
     * @param container
     *            an empty container to show items in, not <code>null</code>
     * @param componentFactory
     *            a callback for creating a new child component for an item
     * @param updateHandler
     *            a callback that will be run when a component is reused with
     *            the same item as before
     * @param removeHandler
     *            a callback that will be run when a previous item is no longer
     *            present and its corresponding component will be removed
     */
    @SuppressWarnings("unchecked")
    public <C extends Component> Repeater(Component container, SerializableFunction<T, C> componentFactory,
            SerializableBiConsumer<C, T> updateHandler, SerializableBiConsumer<C, T> removeHandler) {
        if (container.getElement().getChildCount() != 0) {
            throw new IllegalArgumentException("The container must be empty");
        }
        this.container = container;
        this.componentFactory = componentFactory;
        this.updater = (SerializableBiConsumer<Component, T>) updateHandler;
        this.remover = (SerializableBiConsumer<Component, T>) removeHandler;
    }

    /**
     * Sets a data provider to get items from. The repeater will subscribe to
     * refresh events from the data provider and automatically update its child
     * components. All items will always be loaded from the data provider
     * without any lazy loading.
     * 
     * @param dataProvider
     *            the data provider to use, or <code>null</code> to remove
     *            listeners from a previous data provider and remove all child
     *            components
     */
    public void setDataProvider(DataProvider<T, ?> dataProvider) {
        if (dataProvider == null) {
            showItems(Collections.emptyList());
            return;
        }

        if (dataProviderRegistration != null) {
            dataProviderRegistration.remove();
        }

        dataProviderRegistration = new Registration() {
            private Registration listenerRegistration = null;
            private Registration beforeClientResponseRegistration = null;

            private final Registration attachRegistration = container.addAttachListener(attachEvent -> {
                attach(attachEvent.getUI());
            });

            private final Registration detachRegistration = container.addDetachListener(detachEvent -> {
                listenerRegistration.remove();
                listenerRegistration = null;

                if (beforeClientResponseRegistration != null) {
                    beforeClientResponseRegistration.remove();
                    beforeClientResponseRegistration = null;
                }
            });

            {
                container.getUI().ifPresent(this::attach);
            }

            private void attach(UI ui) {
                listenerRegistration = dataProvider.addDataProviderListener(event -> scheduleRefresh(ui));
                scheduleRefresh(ui);
            }

            private void scheduleRefresh(UI ui) {
                if (beforeClientResponseRegistration != null) {
                    return;
                }

                beforeClientResponseRegistration = ui.beforeClientResponse(container, event -> {
                    beforeClientResponseRegistration = null;
                    /*
                     * TODO Optimize to only refresh a single item if indicated
                     * by the event (taking into account that there may be
                     * multiple affected single items in the same
                     * beforeClientResponse)
                     */

                    List<T> items = dataProvider.fetch(new Query<>(0, Integer.MAX_VALUE, null, null, null))
                            .collect(Collectors.toList());
                    refresh(items);
                });
            }

            @Override
            public void remove() {
                attachRegistration.remove();
                detachRegistration.remove();
                if (listenerRegistration != null) {
                    listenerRegistration.remove();
                }
                if (beforeClientResponseRegistration != null) {
                    beforeClientResponseRegistration.remove();
                }
            }
        };
    }

    /**
     * Show items based on a stream. If some item in the stream was also shown
     * previously, then the component corresponding to that item will be reused
     * instead of creating a new one.
     * <p>
     * If a data provider has been set previously, then it is cleared by running
     * this method.
     * 
     * @param items
     *            a stream of items to show, not <code>null</code>
     */
    public void showItems(Stream<T> items) {
        showItems(items.collect(Collectors.toList()));
    }

    /**
     * Show items based on an array. If some item in the array was also shown
     * previously, then the component corresponding to that item will be reused
     * instead of creating a new one.
     * <p>
     * If a data provider has been set previously, then it is cleared by running
     * this method.
     * 
     * @param items
     *            items to show, not <code>null</code>
     */
    public void showItems(@SuppressWarnings("unchecked") T... items) {
        showItems(Arrays.asList(items));
    }

    /**
     * Show items based on a list. If some item in the list was also shown
     * previously, then the component corresponding to that item will be reused
     * instead of creating a new one.
     * <p>
     * If a data provider has been set previously, then it is cleared by running
     * this method.
     * 
     * @param items
     *            a list of items to show, not <code>null</code>
     */
    public void showItems(List<T> items) {
        if (dataProviderRegistration != null) {
            dataProviderRegistration.remove();
        }

        refresh(items);
    }

    private void refresh(List<T> items) {
        Element containerElement = container.getElement();

        // Map old elements to their item
        HashMap<Element, T> elementToItem = new HashMap<>();
        components.forEach((item, component) -> elementToItem.put(component.getElement(), item));

        // Collect new index of all retained items and instantiate new ones
        HashMap<T, Integer> newIndices = new HashMap<>();
        for (T item : items) {
            if (components.containsKey(item)) {
                if (newIndices.containsKey(item)) {
                    throw new IllegalArgumentException(
                            "Multiple copies of the same item is not supported. Encountered duplicate item: " + item);
                }
                newIndices.put(item, Integer.valueOf(newIndices.size()));
                updater.accept(components.get(item), item);
            } else {
                components.put(item, componentFactory.apply(item));
            }
        }

        // Collect old index for all retained items and remove others
        HashMap<T, Integer> oldIndices = new HashMap<>();
        List<Element> toRemove = new ArrayList<>();
        containerElement.getChildren().forEach(child -> {
            T item = elementToItem.get(child);
            if (newIndices.containsKey(item)) {
                oldIndices.put(item, Integer.valueOf(oldIndices.size()));
            } else {
                remover.accept(components.remove(item), item);
                toRemove.add(child);
            }
        });

        // Remove from the end to play more nicely with the backing ArrayList
        for (int i = toRemove.size() - 1; i >= 0; i--) {
            containerElement.removeChild(toRemove.get(i));
        }

        // Put each element at its right location with minimal changes
        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);

            Element child = components.get(item).getElement();
            Element currentContainerElement = i < containerElement.getChildCount() ? containerElement.getChild(i)
                    : null;

            if (!child.equals(currentContainerElement)) {
                /*
                 * We can either insert the child at current position or remove
                 * elements until child is in the right location. Those removed
                 * elements are added back at their right location when
                 * encountered in the items list.
                 * 
                 * We assume that only one small block of items needs to be
                 * moved. The block to move either starts with the current item
                 * or with the item at the current position in the container.
                 * 
                 * If we were to move the block starting at the current item,
                 * then the element at the current position in the container is
                 * offset from its desired location by the size of that block.
                 * If we were to move the block starting at the current position
                 * in the container, then the current item is offset by the size
                 * of that block. We choose to move the smaller block based on
                 * those offset sizes.
                 */
                boolean insert;
                if (currentContainerElement == null || !oldIndices.containsKey(item)) {
                    // No more elements in container or a new item
                    insert = true;
                } else {
                    T containerItem = elementToItem.get(currentContainerElement);

                    int containerOffset = getItemOffset(containerItem, newIndices, oldIndices);
                    int itemOffset = getItemOffset(item, newIndices, oldIndices);

                    insert = containerOffset <= itemOffset;
                }

                if (insert) {
                    containerElement.insertChild(i, child);
                } else {
                    while (!child.equals(containerElement.getChild(i))) {
                        containerElement.removeChild(i);
                    }
                }
            }
        }
    }

    private static <T> int getItemOffset(T item, HashMap<T, Integer> newIndices, HashMap<T, Integer> oldIndices) {
        return Math.abs(newIndices.get(item).intValue() - oldIndices.get(item).intValue());
    }
}
