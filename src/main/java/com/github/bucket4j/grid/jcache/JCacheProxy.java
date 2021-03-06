
/*
 *  Copyright 2015-2017 Vladimir Bukhtoyarov
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.github.bucket4j.grid.jcache;

import com.github.bucket4j.grid.CommandResult;
import com.github.bucket4j.grid.GridBucketState;
import com.github.bucket4j.grid.GridCommand;
import com.github.bucket4j.grid.GridProxy;

import javax.cache.Cache;
import java.io.Serializable;

public class JCacheProxy<K extends Serializable> implements GridProxy {

    private final Cache<K, GridBucketState> cache;
    private final K key;

    public JCacheProxy(Cache<K, GridBucketState> cache, K key) {
        this.cache = cache;
        this.key = key;
    }

    @Override
    public CommandResult execute(GridCommand command) {
        return (CommandResult) cache.invoke(key, new JCacheCommand(), command);
    }

    @Override
    public void setInitialState(GridBucketState initialState) {
        cache.putIfAbsent(key, initialState);
    }

    @Override
    public Serializable getBucketKey() {
        return key;
    }

}
