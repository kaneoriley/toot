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

abstract class Producer<T extends Event> {

    @SuppressWarnings("WeakerAccess")
    @NonNull
    final Object delegate;

    private final int mHashCode;

    private boolean mValid = true;


    protected Producer(@NonNull Object delegate) {
        this.delegate = delegate;
        this.mHashCode = delegate.hashCode();
    }


    boolean isValid() {
        return mValid;
    }

    void invalidate() {
        mValid = false;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }

        //noinspection SimplifiableIfStatement
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return delegate.equals(((Subscriber) o).delegate);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return "[Producer \"" + delegate.getClass().getName() + "\"]";
    }

    @NonNull
    abstract T produceEvent();
}