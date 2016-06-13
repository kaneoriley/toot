[![Release](https://jitpack.io/v/com.github.oriley-me/toot.svg)](https://jitpack.io/#com.github.oriley-me/toot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://travis-ci.org/oriley-me/toot.svg?branch=master)](https://travis-ci.org/oriley-me/toot)
[![Dependency Status](https://www.versioneye.com/user/projects/575a1e057757a0004a1deb66/badge.svg?style=flat)](https://www.versioneye.com/user/projects/575a1e057757a0004a1deb66)<br/>

<a href="http://www.methodscount.com/?lib=com.github.oriley-me.toot%3Atoot-runtime%3A0.1.1"><img src="https://img.shields.io/badge/toot_runtime-methods: 112 | deps: 20 | size: 17 KB-f44336.svg"></img></a><br>
<a href="http://www.methodscount.com/?lib=com.github.oriley-me.toot%3Atoot-android%3A0.1.1"><img src="https://img.shields.io/badge/toot_android-+methods: 15 | +deps: 0 | +size: 1 KB-ff9800.svg"></img></a>

# Toot
![Logo](artwork/icon.png)

Toot is an event bus designed to decouple different parts of your application while still allowing them to communicate efficiently.

Forked from Otto (which in turn forked from Guava), Toot adds the speed of using compile time code generation, rather
than using reflection like the predecessors. On top of that, Toot supports subscribers in super classes, and doesn't
throw an exception if you try to unregister a class which is not registered.

*For usage instructions please see [the website](http://oriley-me.github.io/toot).*

## License

    Copyright 2016 Kane O'Riley
    Copyright 2012 Square, Inc.
    Copyright 2010 Google, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.