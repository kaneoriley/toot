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
import android.support.annotation.Nullable;

import java.lang.ref.WeakReference;

@SuppressWarnings("WeakerAccess")
public abstract class Producer {

    @NonNull
    public final WeakReference<Object> host;

    private boolean mValid = true;


    public Producer(@NonNull Object host) {
        this.host = new WeakReference<>(host);
    }

    @Nullable
    <E extends Event> E dispatchProduceEvent(@NonNull Class<E> eventClass) {
        Object object = host.get();
        if (object != null) {
            return produceEvent(object, eventClass);
        } else {
            return null;
        }
    }

    protected abstract <E extends Event> E produceEvent(@NonNull final Object host, @NonNull final Class<E> eventClass);

    boolean isValid() {
        return mValid && host.get() != null;
    }

    void invalidate() {
        mValid = false;
    }

    @Override
    public String toString() {
        Object object = host.get();
        return "[Producer \"" + (object != null ? object.getClass().getName() : null) + "\" : " + hashCode() + "]";
    }
}