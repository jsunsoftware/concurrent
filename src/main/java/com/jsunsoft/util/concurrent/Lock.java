package com.jsunsoft.util.concurrent;

/*
 * Copyright 2017 JSun Software
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

import com.jsunsoft.util.Executable;

import java.util.Collection;

public interface Lock {

    <X extends Throwable> void lock(Object objectToLock, Executable<X> executable) throws InterruptedException, X;

    <X extends Throwable> void lock(Collection<?> objectsToLock, Executable<X> executable) throws InterruptedException, X;


}
