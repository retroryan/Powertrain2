package com;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

public class VehicleDao {

    public static <T> CompletionStage<T> toCompletionStage(ListenableFuture<T> listenableFuture) {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        listenableFuture.addListener(() -> {
            try {
                completableFuture.complete(listenableFuture.get());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, ForkJoinPool.commonPool());
        return completableFuture;
    }

}
