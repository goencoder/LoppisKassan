package se.goencoder.loppiskassan.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A simple publish/subscribe event bus for decoupling panels and controllers.
 */
public class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    public static EventBus getInstance() {
        return INSTANCE;
    }

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    private EventBus() {
    }

    /**
     * Subscribe to events of the given type.
     *
     * @param eventType the class representing the event type
     * @param listener the consumer to invoke when the event is published
     * @param <T> event type
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * Publish an event to all registered listeners of its type.
     *
     * @param event the event instance to publish
     * @param <T> event type
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> consumers = listeners.get(event.getClass());
        if (consumers != null) {
            for (Consumer<?> consumer : consumers) {
                ((Consumer<T>) consumer).accept(event);
            }
        }
    }
}

