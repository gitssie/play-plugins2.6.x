package play.libs.concurrent;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * RedeemablePromise is an object which can be completed with a value or failed with an exception.
 * <p>
 * <pre>
 * {@code
 * RedeemablePromise<Integer> someFutureInt = RedeemablePromise.empty();
 *
 * someFutureInt.map(new Function<Integer, Result>() {
 *     public Result apply(Integer i) {
 *         // This would apply once the redeemable promise succeed
 *         return Results.ok("" + i);
 *     }
 * });
 *
 * // In another thread, you now may complete the RedeemablePromise.
 * someFutureInt.success(42);
 * }
 * </pre>
 * Use {@link CompletableFuture} instead.
 */

public class RedeemablePromise<A> extends Promise<A> {

    private final CompletableFuture<A> future;

    private RedeemablePromise(CompletableFuture<A> future) {
        super(future);

        this.future = future;
    }

    /**
     * Creates a new Promise with no value
     */
    public static <A> RedeemablePromise<A> empty() {
        return new RedeemablePromise<>(new CompletableFuture<>());
    }

    /**
     * Completes the promise with a value.
     *
     * @param a The value to complete with
     */
    public void success(A a) {
        if (!future.complete(a)) {
            throw new IllegalStateException("RedeemablePromise already completed.");
        }
    }

    /**
     * Completes the promise with an exception
     *
     * @param t The exception to fail the promise with
     */
    public void failure(Throwable t) {
        if (!future.completeExceptionally(t)) {
            throw new IllegalStateException("RedeemablePromise already completed.");
        }
    }

    /**
     * Completes this promise with the specified Promise, once that Promise is completed.
     *
     * @param other The value to complete with
     * @return A promise giving the result of attempting to complete this promise with the other
     * promise. If the completion was successful then the result will be a null value,
     * if the completion failed then the result will be an IllegalStateException.
     */
    public Promise<Void> completeWith(CompletionStage<? extends A> other) {
        return new Promise<>(other.handle((a, error) -> {
            boolean completed;
            if (error != null) {
                completed = future.completeExceptionally(error);
            } else {
                completed = future.complete(a);
            }
            if (!completed) {
                throw new IllegalStateException("RedeemablePromise already completed.");
            } else {
                return null;
            }
        }));
    }

    /**
     * Completes this promise with the specified Promise, once that Promise is completed.
     *
     * @param other The value to complete with
     * @param ec    An execution context
     * @return A promise giving the result of attempting to complete this promise with the other
     * promise. If the completion was successful then the result will be a null value,
     * if the completion failed then the result will be an IllegalStateException.
     */
    public Promise<Void> completeWith(CompletionStage<? extends A> other, Executor ec) {
        return new Promise<>(other.handleAsync((a, error) -> {
            boolean completed;
            if (error != null) {
                completed = future.completeExceptionally(error);
            } else {
                completed = future.complete(a);
            }
            if (!completed) {
                throw new IllegalStateException("RedeemablePromise already completed.");
            } else {
                return null;
            }
        }, ec));
    }

    /**
     * Completes this promise with the specified Promise, once that Promise is completed.
     *
     * @param other The value to complete with
     * @return A promise giving the result of attempting to complete this promise with the other
     * promise. If the completion was successful then the result will be true, if the
     * completion couldn't occur then the result will be false.
     */
    public Promise<Boolean> tryCompleteWith(Promise<? extends A> other) {
        return new Promise<>(other.handle((a, error) -> {
            if (error != null) {
                return future.completeExceptionally(error);
            } else {
                return future.complete(a);
            }
        }));
    }

    /**
     * Completes this promise with the specified Promise, once that Promise is completed.
     *
     * @param other The value to complete with
     * @param ec    An execution context
     * @return A promise giving the result of attempting to complete this promise with the other
     * promise. If the completion was successful then the result will be true, if the
     * completion couldn't occur then the result will be false.
     */
    public Promise<Boolean> tryCompleteWith(Promise<? extends A> other, Executor ec) {
        return new Promise<>(other.handleAsync((a, error) -> {
            if (error != null) {
                return future.completeExceptionally(error);
            } else {
                return future.complete(a);
            }
        }, ec));
    }
}
