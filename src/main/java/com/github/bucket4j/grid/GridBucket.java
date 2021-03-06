/*
 * Copyright 2015 Vladimir Bukhtoyarov
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.github.bucket4j.grid;

import com.github.bucket4j.AbstractBucket;
import com.github.bucket4j.BucketConfiguration;
import com.github.bucket4j.BucketState;

import java.io.Serializable;

public class GridBucket extends AbstractBucket {

    private final GridProxy gridProxy;
    private final RecoveryStrategy recoveryStrategy;

    public GridBucket(BucketConfiguration configuration, GridProxy gridProxy, RecoveryStrategy recoveryStrategy) {
        super(configuration);
        this.gridProxy = gridProxy;
        this.recoveryStrategy = recoveryStrategy;
        initializeBucket();
    }

    @Override
    protected long consumeAsMuchAsPossibleImpl(long limit) {
        return execute(new ConsumeAsMuchAsPossibleCommand(limit));
    }

    @Override
    protected boolean tryConsumeImpl(long tokensToConsume) {
        return execute(new TryConsumeCommand(tokensToConsume));
    }

    @Override
    protected boolean consumeOrAwaitImpl(long tokensToConsume, long waitIfBusyTimeLimit) throws InterruptedException {
        final boolean isWaitingLimited = waitIfBusyTimeLimit > 0;
        final ConsumeOrCalculateTimeToCloseDeficitCommand consumeCommand = new ConsumeOrCalculateTimeToCloseDeficitCommand(tokensToConsume);
        final long methodStartTimeNanos = isWaitingLimited? configuration.getTimeMeter().currentTimeNanos() : 0;

        while (true) {
            long nanosToCloseDeficit = execute(consumeCommand);
            if (nanosToCloseDeficit == 0) {
                return true;
            }
            if (nanosToCloseDeficit == Long.MAX_VALUE) {
                return false;
            }

            if (isWaitingLimited) {
                long currentTimeNanos = configuration.getTimeMeter().currentTimeNanos();
                long methodDuration = currentTimeNanos - methodStartTimeNanos;
                if (methodDuration >= waitIfBusyTimeLimit) {
                    return false;
                }
                long sleepingTimeLimit = waitIfBusyTimeLimit - methodDuration;
                if (nanosToCloseDeficit >= sleepingTimeLimit) {
                    return false;
                }
            }
            configuration.getTimeMeter().parkNanos(nanosToCloseDeficit);
        }
    }

    @Override
    public BucketState createSnapshot() {
        return execute(new CreateSnapshotCommand());
    }

    private <T extends Serializable> T execute(GridCommand<T> command) {
        CommandResult<T> result = gridProxy.execute(command);
        if (!result.isBucketNotFound()) {
            return result.getData();
        }

        // the bucket was removed or lost, it is need to apply recovery strategy
        if (recoveryStrategy == RecoveryStrategy.THROW_BUCKET_NOT_FOUND_EXCEPTION) {
            throw new BucketNotFoundException(gridProxy.getBucketKey());
        } else {
            initializeBucket();
        }

        // retry command execution
        result = gridProxy.execute(command);
        if (!result.isBucketNotFound()) {
            return result.getData();
        } else {
            // something wrong goes with GRID, reinitialization of bucket has no effect
            throw new BucketNotFoundException(gridProxy.getBucketKey());
        }
    }

    private void initializeBucket() {
        GridBucketState initialState = new GridBucketState(configuration, BucketState.createInitialState(configuration));
        gridProxy.setInitialState(initialState);
    }

}
