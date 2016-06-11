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

import java.lang.ref.WeakReference;

@SuppressWarnings("WeakerAccess")
public abstract class Subscriber {

    @NonNull
    public final WeakReference<Object> host;

    private boolean mValid = true;


    public Subscriber(@NonNull Object host) {
        this.host = new WeakReference<>(host);
    }

    void dispatchEvent(@NonNull Event event) {
        Object object = host.get();
        if (object != null) {
            onEvent(object, event);
        }
    }

    protected abstract void onEvent(@NonNull Object host, @NonNull final Event event);

    boolean isValid() {
        return mValid && host.get() != null;
    }

    void invalidate() {
        mValid = false;
    }

    @Override
    public String toString() {
        Object object = host.get();
        return "[Subscriber \"" + (object != null ? object.getClass().getName() : null) + "\" : " + hashCode() + "]";
    }
}