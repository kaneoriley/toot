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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("WeakerAccess")
public class Bus {

    private static final String DEFAULT = "default-bus";

    @NonNull
    private final ConcurrentMap<Class<?>, Set<Subscriber>> mSubscribers = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, Producer> mProducers = new ConcurrentHashMap<>();

    @NonNull
    private final ConcurrentMap<Class<?>, Set<Class<?>>> mFlattenHierarchyCache = new ConcurrentHashMap<>();

    @NonNull
    private final String mTag;

    @NonNull
    private final ThreadEnforcer mEnforcer;

    @NonNull
    private final Finder mFinder;

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

    private volatile boolean mStrictMode;


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
        mFinder = new TootFinder();
    }


    @SuppressWarnings("unused")
    public void register(@NonNull Object object) {
        mEnforcer.enforce(this);

        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            register(object, type);
        }
    }

    private void register(@NonNull Object object, @NonNull Class<?> objectClass) {
        Map<Class<?>, Producer> foundProducers = mFinder.createProducers(object, objectClass);
        for (Class<?> type : foundProducers.keySet()) {

            final Producer producer = foundProducers.get(type);
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
                    dispatchProducedEvent(subscriber, producer);
                }
            }
        }

        Map<Class<?>, Subscriber> foundSubscribersMap = mFinder.createSubscribers(object, objectClass);
        for (Class<?> type : foundSubscribersMap.keySet()) {
            Set<Subscriber> subscribers = mSubscribers.get(type);
            if (subscribers == null) {
                //concurrent put if absent
                Set<Subscriber> subscriberSet = new CopyOnWriteArraySet<>();
                subscribers = mSubscribers.putIfAbsent(type, subscriberSet);
                if (subscribers == null) {
                    subscribers = subscriberSet;
                }
            }

            final Subscriber foundSubscriber = foundSubscribersMap.get(type);
            if (!subscribers.add(foundSubscriber)) {
                if (mStrictMode) {
                    throw new IllegalArgumentException("Object " + object  + " already registered.");
                } else {
                    return;
                }
            }
        }

        for (Map.Entry<Class<?>, Subscriber> entry : foundSubscribersMap.entrySet()) {
            Class<?> type = entry.getKey();
            Producer producer = mProducers.get(type);
            if (producer != null && producer.isValid()) {
                Subscriber foundSubscriber = entry.getValue();
                if (!producer.isValid()) {
                    break;
                }
                if (foundSubscriber.isValid()) {
                    dispatchProducedEvent(foundSubscriber, producer);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public void unregister(@NonNull Object object) {
        mEnforcer.enforce(this);

        Set<Class<?>> unregisterTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : unregisterTypes) {
            unregister(object, type);
        }
    }

    private void unregister(@NonNull Object object, @NonNull Class<?> objectClass) {
        Map<Class<?>, Producer> producersInListener = mFinder.createProducers(object, objectClass);
        for (Map.Entry<Class<?>, Producer> entry : producersInListener.entrySet()) {
            final Class<?> key = entry.getKey();
            Producer producer = mProducers.get(key);
            Producer value = entry.getValue();

            if (value == null || !value.equals(producer)) {
                if (mStrictMode) {
                    throw new IllegalArgumentException(
                            "Missing event producer for an annotated method. Is " + object.getClass() + " registered?");
                } else {
                    return;
                }
            }

            mProducers.remove(key).invalidate();
        }

        Map<Class<?>, Subscriber> subscribers = mFinder.createSubscribers(object, objectClass);
        for (Map.Entry<Class<?>, Subscriber> entry : subscribers.entrySet()) {
            Set<Subscriber> currentSubscribers = mSubscribers.get(entry.getKey());
            Subscriber subscriber = entry.getValue();

            if (currentSubscribers == null || !currentSubscribers.contains(subscriber)) {
                if (mStrictMode) {
                    throw new IllegalArgumentException(
                            "Missing event subscriber for an annotated method. Is " + object.getClass()
                                    + " registered?");
                } else {
                    return;
                }
            }

            subscriber.invalidate();
            currentSubscribers.remove(subscriber);
        }
    }

    @SuppressWarnings("unused")
    public <T extends Event> void post(@NonNull T event) {
        mEnforcer.enforce(this);

        Set<Class<?>> dispatchTypes = flattenHierarchy(event.getClass());

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

    @SuppressWarnings("unused")
    public void setStrictMode(boolean strictMode) {
        mStrictMode = strictMode;
    }

    private <T extends Event> void enqueueEvent(@NonNull T event, @NonNull Subscriber<T> subscriber) {
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

    private <T extends Event> void dispatchProducedEvent(@NonNull Subscriber<T> subscriber, Producer<T> producer) {
        if (producer.isValid()) {
            dispatch(producer.produceEvent(), subscriber);
        } else {
            throw new IllegalStateException(producer.toString() + " has been invalidated and can no longer produce events.");
        }
    }

    private <T extends Event> void dispatch(@NonNull T event, @NonNull Subscriber<T> subscriber) {
        if (subscriber.isValid()) {
            subscriber.onEvent(event);
        } else {
            throw new IllegalStateException(subscriber.toString() + " has been invalidated and can no longer receive events.");
        }
    }

    @NonNull
    private Set<Class<?>> flattenHierarchy(@NonNull Class<?> concreteClass) {
        Set<Class<?>> classes = mFlattenHierarchyCache.get(concreteClass);
        if (classes == null) {
            Set<Class<?>> classesCreation = getClassesFor(concreteClass);
            classes = mFlattenHierarchyCache.putIfAbsent(concreteClass, classesCreation);
            if (classes == null) {
                classes = classesCreation;
            }
        }

        return classes;
    }

    @NonNull
    private Set<Class<?>> getClassesFor(@NonNull Class<?> concreteClass) {
        List<Class<?>> parents = new LinkedList<>();
        Set<Class<?>> classes = new HashSet<>();

        parents.add(concreteClass);

        while (!parents.isEmpty()) {
            Class<?> clazz = parents.remove(0);
            classes.add(clazz);

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

    @Override
    public String toString() {
        return "[Bus \"" + mTag + "\"]";
    }

    static class DispatchInfo<T extends Event> {

        @NonNull
        final T event;

        @NonNull
        final Subscriber<T> subscriber;


        DispatchInfo(@NonNull T event, @NonNull Subscriber<T> subscriber) {
            this.event = event;
            this.subscriber = subscriber;
        }
    }
}
