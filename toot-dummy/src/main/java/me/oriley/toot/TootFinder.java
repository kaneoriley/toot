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

import java.util.Collections;
import java.util.Map;

@SuppressWarnings("unused")
public final class TootFinder implements Finder {

    @NonNull
    @Override
    public Map<Class<?>, Producer> createProducers(@NonNull Object listener, @NonNull Class<?> targetClass) {
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public Map<Class<?>, Subscriber> createSubscribers(@NonNull Object listener, @NonNull Class<?> targetClass) {
        return Collections.emptyMap();
    }
}
