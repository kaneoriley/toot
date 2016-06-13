/*
 * Copyright (C) 2007 The Guava Authors
 * Copyright (C) 2012 Square, Inc.
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.oriley.toot;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("WeakerAccess")
public class Bus {

    private static final String DEFAULT = "default-bus";
    private static final boolean DEBUG = false;

    @NonNull
    private final ConcurrentMap<Class<?>, Set<Subscriber>> mSubscribers = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, Producer> mProducers = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, Set<Class<?>>> mFlattenHierarchyCache = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, ProducerFactory> mProducerFactoryCache = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, SubscriberFactory> mSubscriberFactoryCache = new ConcurrentHashMap<>();

    @NonNull
    private final String mTag;

    @NonNull
    private final ThreadEnforcer mEnforcer;

    @NonNull
    private final ThreadLocal<ConcurrentLinkedQueue<DispatchInfo>> mDispatchQueue =
            new ThreadLocal<ConcurrentLinkedQueue<DispatchInfo>>() {
                @Override
                protected ConcurrentLinkedQueue<DispatchInfo> initialValue() {
                    return new ConcurrentLinkedQueue<>();
                }
            };

    @NonNull
    private final ThreadLocal<Boolean> mDispatching = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };


    @SuppressWarnings("unused")
    public Bus() {
        this(ThreadEnforcer.ANY, DEFAULT);
    }

    @SuppressWarnings("unused")
    public Bus(@NonNull String tag) {
        this(ThreadEnforcer.ANY, tag);
    }

    @SuppressWarnings("unused")
    public Bus(@NonNull ThreadEnforcer enforcer) {
        this(enforcer, DEFAULT);
    }

    public Bus(@NonNull ThreadEnforcer enforcer, @NonNull String tag) {
        mEnforcer = enforcer;
        mTag = tag;
    }


    @SuppressWarnings("unused")
    public void register(@NonNull Object object) {
        mEnforcer.enforce(this);

        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass(), true);
        for (Class<?> type : registerTypes) {
            register(object, type);
        }
    }

    private <T> void register(@NonNull Object object, @NonNull Class<T> objectClass) {
        SubscriberFactory subscriberFactory = findSubscriberFactoryForClass(objectClass);
        ProducerFactory producerFactory = findProducerFactoryForClass(objectClass);

        if (subscriberFactory == null && producerFactory == null) {
            log("No Subscriber or Producer found for: %s.", objectClass);
            return;
        }

        if (producerFactory != null) {
            List<Class<?>> producerClasses = producerFactory.getProducedClasses();
            for (Class<?> type : producerClasses) {

                final Producer producer = producerFactory.getProducer(object);
                Producer previousProducer = mProducers.putIfAbsent(type, producer);
                //checking if the previous producer existed
                if (previousProducer != null) {
                    throw new IllegalArgumentException("Producer method for type " + type
                            + " found on type " + producer.toString()
                            + ", but already registered by type " + previousProducer.toString() + ".");
                }
                Set<Subscriber> subscribers = mSubscribers.get(type);
                if (subscribers != null && !subscribers.isEmpty()) {
                    for (Subscriber subscriber : subscribers) {
                        dispatchProducedEvent(subscriber, producer, type);
                    }
                }
            }
        }

        if (subscriberFactory != null) {
            Subscriber subscriber = null;
            List<Class<?>> subscriberClasses = subscriberFactory.getSubscribedClasses();
            for (Class<?> type : subscriberClasses) {
                Set<Subscriber> subscribers = mSubscribers.get(type);
                if (subscribers == null) {
                    //concurrent put if absent
                    Set<Subscriber> subscriberSet = new CopyOnWriteArraySet<>();
                    subscribers = mSubscribers.putIfAbsent(type, subscriberSet);
                    if (subscribers == null) {
                        subscribers = subscriberSet;
                    }
                }

                subscriber = subscriberFactory.getSubscriber(object);
                if (!subscribers.add(subscriber)) {
                    log("Failed to add subscriber: %s, event: %s.", subscriber, type);
                    return;
                } else {
                    log("Registered subscriber: %s, event: %s.", subscriber, type);
                }
            }

            for (Class<?> type : subscriberClasses) {
                Producer producer = mProducers.get(type);
                if (producer != null && producer.isValid() && subscriber != null) {
                    if (!producer.isValid()) {
                        break;
                    }
                    if (subscriber.isValid()) {
                        log("Dispatching To subscriber: %s, event: %s.", subscriber, type);
                        dispatchProducedEvent(subscriber, producer, type);
                    } else {
                        log("Not dispatching to invalid subscriber: %s.", subscriber);
                    }
                }
            }

        }
    }

    @SuppressWarnings("unused")
    public void unregister(@NonNull Object object) {
        mEnforcer.enforce(this);

        Set<Class<?>> unregisterTypes = flattenHierarchy(object.getClass(), true);
        for (Class<?> type : unregisterTypes) {
            unregister(object, type);
        }
    }

    private void unregister(@NonNull Object object, @NonNull Class<?> objectClass) {
        SubscriberFactory subscriberFactory = findSubscriberFactoryForClass(objectClass);
        ProducerFactory producerFactory = findProducerFactoryForClass(objectClass);

        if (subscriberFactory == null && producerFactory == null) {
            log("No subscriber or producer found for: %s.", objectClass);
            return;
        }

        if (producerFactory != null) {
            List<Class<?>> producerClasses = producerFactory.getProducedClasses();
            for (Class<?> cls : producerClasses) {
                Producer producer = mProducers.get(cls);

                if (producer == null) {
                    log("Missing event producer for an annotated method. Is %s registered?", objectClass);
                    return;
                }

                producer.invalidate();
                mProducers.remove(cls);
            }
        }

        if (subscriberFactory != null) {
            List<Class<?>> subscriberClasses = subscriberFactory.getSubscribedClasses();
            for (Class<?> type : subscriberClasses) {
                boolean removed = false;
                Set<Subscriber> currentSubscribers = mSubscribers.get(type);
                for (Subscriber subscriber : currentSubscribers) {
                    if (subscriber.host.get() == object) {
                        subscriber.invalidate();
                        currentSubscribers.remove(subscriber);
                        log("Unregistered Subscriber: %s, Event: %s.", subscriber, type);
                        removed = true;
                    }
                }

                if (!removed) {
                    log( "Missing event subscriber for an annotated method. Is %s registered?", objectClass);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public <E> void post(@NonNull E event) {
        mEnforcer.enforce(this);

        Set<Class<?>> dispatchTypes = flattenHierarchy(event.getClass(), false);

        boolean dispatched = false;
        for (Class<?> eventType : dispatchTypes) {
            Set<Subscriber> subscribers = mSubscribers.get(eventType);

            if (subscribers != null && !subscribers.isEmpty()) {
                dispatched = true;
                for (Subscriber subscriber : subscribers) {
                    enqueueEvent(event, subscriber);
                }
            }
        }

        if (!dispatched && !(event instanceof DeadEvent)) {
            post(new DeadEvent<>(this, event));
        }

        dispatchQueuedEvents();
    }

    @Nullable
    private SubscriberFactory findSubscriberFactoryForClass(Class<?> cls) {
        SubscriberFactory factory = mSubscriberFactoryCache.get(cls);
        if (factory != null) {
            log("Subscriber cached in factory map for %s.", cls);
            return factory;
        }

        try {
            Class<?> factoryClass = Class.forName(cls.getName() + SubscriberFactory.CLASS_SUFFIX);
            //noinspection unchecked
            factory = (SubscriberFactory) factoryClass.newInstance();
            log("Subscriber loaded factory class for %s.", cls);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log("Subscriber not found for %s.", cls, cls.getSuperclass());
            factory = null;
        }

        if (factory != null) {
            mSubscriberFactoryCache.put(cls, factory);
        }
        return factory;
    }

    @Nullable
    private ProducerFactory findProducerFactoryForClass(Class<?> cls) {
        ProducerFactory factory = mProducerFactoryCache.get(cls);
        if (factory != null) {
            log("Producer cached in factory map for %s.", cls);
            return factory;
        }

        try {
            Class<?> factoryClass = Class.forName(cls.getName() + ProducerFactory.CLASS_SUFFIX);
            //noinspection unchecked
            factory = (ProducerFactory) factoryClass.newInstance();
            log("Producer loaded factory class for %s.", cls);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            log("Producer not found for %s.", cls);
            factory = null;
        }

        if (factory != null) {
            mProducerFactoryCache.put(cls, factory);
        }
        return factory;
    }

    private <E> void enqueueEvent(@NonNull E event, @NonNull Subscriber subscriber) {
        mDispatchQueue.get().offer(new DispatchInfo<>(event, subscriber));
    }

    private void dispatchQueuedEvents() {
        if (mDispatching.get()) {
            return;
        }

        mDispatching.set(true);
        try {
            while (true) {
                DispatchInfo dispatchInfo = mDispatchQueue.get().poll();
                if (dispatchInfo == null) {
                    break;
                }

                if (dispatchInfo.subscriber.isValid()) {
                    dispatch(dispatchInfo.event, dispatchInfo.subscriber);
                }
            }
        } finally {
            mDispatching.set(false);
        }
    }

    private <E> void dispatchProducedEvent(@NonNull Subscriber subscriber,
                                                         @NonNull Producer producer,
                                                         @NonNull Class<E> eventClass) {
        if (producer.isValid()) {
            E event = producer.dispatchProduceEvent(eventClass);
            if (event != null) {
                dispatch(event, subscriber);
            } else {
                throw new IllegalStateException(producer.toString() + " returned null for event type " + eventClass);
            }
        } else {
            throw new IllegalStateException(producer.toString() + " has been invalidated and can no longer produce events.");
        }
    }

    private <E> void dispatch(@NonNull E event, @NonNull Subscriber subscriber) {
        if (subscriber.isValid()) {
            subscriber.dispatchEvent(event);
        } else {
            throw new IllegalStateException(subscriber.toString() + " has been invalidated and can no longer receive events.");
        }
    }

    @NonNull
    private Set<Class<?>> flattenHierarchy(@NonNull Class<?> concreteClass, boolean requireFactory) {
        Set<Class<?>> classes = mFlattenHierarchyCache.get(concreteClass);
        if (classes == null) {
            Set<Class<?>> classesCreation = getClassesFor(concreteClass, requireFactory);
            classes = mFlattenHierarchyCache.putIfAbsent(concreteClass, classesCreation);
            if (classes == null) {
                classes = classesCreation;
            }
        }

        return classes;
    }

    @NonNull
    private Set<Class<?>> getClassesFor(@NonNull Class<?> concreteClass, boolean requireFactory) {
        List<Class<?>> parents = new LinkedList<>();
        Set<Class<?>> classes = new HashSet<>();

        parents.add(concreteClass);

        while (!parents.isEmpty()) {
            Class<?> clazz = parents.remove(0);

            boolean validClass = true;
            if (requireFactory) {
                SubscriberFactory subscriberFactory = findSubscriberFactoryForClass(clazz);
                ProducerFactory producerFactory = findProducerFactoryForClass(clazz);
                validClass = (subscriberFactory != null || producerFactory != null);
            }

            if (validClass) {
                classes.add(clazz);
            }

            Class<?> parent = clazz.getSuperclass();
            if (parent != null) {
                String name = parent.getName();
                if (!name.startsWith("java.") && !name.startsWith("android.")) {
                    parents.add(parent);
                }
            }
        }

        return classes;
    }

    private static void log(@NonNull String message, @Nullable Object... args) {
        if (DEBUG) {
            if (args != null) {
                message = String.format(message, args);
            }
            System.out.println("TOOT -- " + message);
        }
    }

    @Override
    public String toString() {
        return "[Bus \"" + mTag + "\"]";
    }

    static class DispatchInfo<E> {

        @NonNull
        final E event;

        @NonNull
        final Subscriber subscriber;


        DispatchInfo(@NonNull E event, @NonNull Subscriber subscriber) {
            this.event = event;
            this.subscriber = subscriber;
        }
    }
}
