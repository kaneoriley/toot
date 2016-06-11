/*
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

@SuppressWarnings("WeakerAccess")
public abstract class SubscriberFactory {

    static final String CLASS_SUFFIX = "$$SubscriberFactory";

    @NonNull
    protected final List<Class<? extends Event>> mSubscribedClasses = new ArrayList<>();

    @NonNull
    final List<Class<? extends Event>> getSubscribedClasses() {
        return mSubscribedClasses;
    }

    @NonNull
    protected abstract Subscriber getSubscriber(@NonNull final Object host);
}
