/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.libs.concurrent;

import play.api.libs.Execution;
import play.libs.F.Either;
import play.libs.F.PromiseTimeoutException;
import play.libs.F.Tuple;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.function.*;


/**
 * A promise to produce a result of type <code>A</code>.
 * <p>
 * Use the JDK8 {@link CompletionStage} instead. When migrating to CompletionStage, Promise implements
 * CompletionStage, so it may be easier to first migrate all the existing method calls on the promise,
 * such as map/flatMap, which are also deprecated but include migration instructions in the deprecation
 * message.
 */

public class Promise<A> implements CompletionStage<A> {

    private final CompletionStage<A> wrapped;

    public Promise(CompletionStage<A> wrapped) {
        this.wrapped = wrapped;
    }

    public Promise(Supplier<A> function){
        this(function, Execution.httpPromiseContext());
    }

    public Promise(Supplier<A> function,Executor ec){
        this(CompletableFuture.supplyAsync(function,ec));
    }

    public static <A> Promise<A> wrap(CompletionStage<A> future) {
        return new Promise<>(future);
    }

    /**
     * Creates a Promise that wraps a Scala Future.
     *
     * @param future The Scala Future to wrap
     *               Use {@link FutureConverters#toJava(Future)} instead.
     */

    public static <A> Promise<A> wrap(Future<A> future) {
        return new Promise<>(FutureConverters.toJava(future));
    }

    public static <A> Promise<A> wrap(scala.concurrent.Promise<A> promise) {
        return new Promise<>(FutureConverters.toJava(promise.future()));
    }

    public static Executor toExecutor(ExecutionContext ec) {
        ExecutionContext prepared = ec.prepare();
        if (prepared instanceof Executor) {
            return (Executor) prepared;
        } else {
            return prepared::execute;
        }
    }

    /**
     * Combine the given promises into a single promise for the list of results.
     * <p>
     * The sequencing operations are performed in the default ExecutionContext.
     *
     * @param promises The promises to combine
     * @return A single promise whose methods act on the list of redeemed promises
     * Use {@link Futures#sequence(CompletionStage[])} instead.
     */

    public static <A> Promise<List<A>> sequence(Promise<A>... promises) {
        return wrap(Futures.sequence(Arrays.asList(promises)));
    }

    /**
     * Combine the given promises into a single promise for the list of results.
     *
     * @param ec       Used to execute the sequencing operations.
     * @param promises The promises to combine
     * @return A single promise whose methods act on the list of redeemed promises
     * Use {@link Futures#sequence(CompletionStage[])} instead.
     */

    public static <A> Promise<List<A>> sequence(Executor ec, Promise<A>... promises) {
        return sequence(Arrays.asList(promises), ec);
    }

    /**
     * Create a Promise that is redeemed after a timeout.
     *
     * @param message The message to use to redeem the Promise.
     * @param delay   The delay (expressed with the corresponding unit).
     * @param unit    The Unit.
     *                Use {@link Futures#timeout(Object, long, TimeUnit)} instead.
     */

    public static <A> Promise<A> timeout(A message, long delay, TimeUnit unit) {
        return (Promise<A>) Futures.timeout(message, delay, unit);
    }

    /**
     * Create a Promise that is redeemed after a timeout.
     *
     * @param message The message to use to redeem the Promise.
     * @param delay   The delay expressed in milliseconds.
     *                Use {@link Futures#timeout(Object, long, TimeUnit)} instead.
     */

    public static <A> Promise<A> timeout(A message, long delay) {
        return wrap(Futures.timeout(message, delay, TimeUnit.MILLISECONDS));
    }

    /**
     * Create a Promise timer that throws a PromiseTimeoutException after
     * a given timeout.
     * <p>
     * The returned Promise is usually combined with other Promises.
     *
     * @param delay The delay expressed in milliseconds.
     *              Use {@link Futures#timeout(long, TimeUnit)} instead.
     * @return a promise without a real value
     */

    public static Promise<Void> timeout(long delay) {
        return wrap(Futures.timeout(delay, TimeUnit.MILLISECONDS));
    }

    /**
     * Create a Promise timer that throws a PromiseTimeoutException after
     * a given timeout.
     * <p>
     * The returned Promise is usually combined with other Promises.
     *
     * @param delay The delay (expressed with the corresponding unit).
     * @param unit  The Unit.
     * @return a promise without a real value
     * Use {@link Futures#timeout(long, TimeUnit)} instead.
     */

    public static Promise<Void> timeout(long delay, TimeUnit unit) {
        return wrap(Futures.timeout(delay, unit));
    }

    /**
     * Combine the given promises into a single promise for the list of results.
     * <p>
     * The sequencing operations are performed in the default ExecutionContext.
     *
     * @param promises The promises to combine
     * @return A single promise whose methods act on the list of redeemed promises
     * Use {@link Futures#sequence(Iterable)} instead.
     */

    public static <A> Promise<List<A>> sequence(Iterable<Promise<A>> promises) {
        return wrap(Futures.sequence(promises));
    }

    /**
     * Combine the given promises into a single promise for the list of results.
     *
     * @param promises The promises to combine
     * @param ec       Used to execute the sequencing operations.
     * @return A single promise whose methods act on the list of redeemed promises
     * Use {@link Futures#sequence(Iterable)} instead.
     */

    public static <A> Promise<List<A>> sequence(Iterable<Promise<A>> promises, Executor ec) {
        CompletableFuture<List<A>> result = CompletableFuture.completedFuture(new ArrayList<>());
        for (Promise<A> promise : promises) {
            result = result.thenCombineAsync(promise, (list, a) -> {
                list.add(a);
                return list;
            }, (ec));
        }
        return new Promise<>(result);
    }

    /**
     * Create a new pure promise, that is, a promise with a constant value from the start.
     *
     * @param a the value for the promise
     *          Use {@link CompletableFuture#completedFuture(Object)} instead.
     */

    public static <A> Promise<A> pure(final A a) {
        return new Promise<>(CompletableFuture.completedFuture(a));
    }

    /**
     * Create a new promise throwing an exception.
     *
     * @param throwable Value to throw
     *                  Construct a new {@link CompletableFuture} and use
     *                  {@link CompletableFuture#completeExceptionally(Throwable)} instead.
     */

    public static <A> Promise<A> throwing(Throwable throwable) {
        CompletableFuture<A> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return new Promise<>(future);
    }

    /**
     * Create a Promise which will be redeemed with the result of a given function.
     * <p>
     * The function will be run in the default ExecutionContext.
     *
     * @param function Used to fulfill the Promise.
     *                 Use {@link CompletableFuture#supplyAsync(Supplier, Executor)} instead.
     */

    public static <A> Promise<A> promise(Supplier<A> function) {
        return new Promise<>(CompletableFuture.supplyAsync(function, Execution.httpPromiseContext()));
    }

    /**
     * Create a Promise which will be redeemed with the result of a given function.
     *
     * @param function Used to fulfill the Promise.
     * @param ec       The ExecutionContext to run the function in.
     *                 Use {@link CompletableFuture#supplyAsync(Supplier, Executor)} instead.
     */

    public static <A> Promise<A> promise(Supplier<A> function, Executor ec) {
        return new Promise<>(CompletableFuture.supplyAsync(function, (ec)));
    }

    /**
     * Create a Promise which, after a delay, will be redeemed with the result of a
     * given function. The function will be called after the delay.
     * <p>
     * The function will be run in the default ExecutionContext.
     *
     * @param function The function to call to fulfill the Promise.
     * @param delay    The time to wait.
     * @param unit     The units to use for the delay.
     *                 Use {@link Futures#delayed(Supplier, long, TimeUnit, Executor)} with
     *                 {@link Execution#httpPromiseContext()} instead.
     */

    public static <A> Promise<A> delayed(Supplier<A> function, long delay, TimeUnit unit) {
        return wrap(Futures.delayed(function, delay, unit, Execution.httpPromiseContext()));
    }

    /**
     * Create a Promise which, after a delay, will be redeemed with the result of a
     * given function. The function will be called after the delay.
     *
     * @param function The function to call to fulfill the Promise.
     * @param delay    The time to wait.
     * @param unit     The units to use for the delay.
     *                 Use {@link Futures#delayed(Supplier, long, TimeUnit, Executor)} instead.
     */

    public static <A> Promise<A> delayed(Supplier<A> function, long delay, TimeUnit unit, Executor ec) {
        return wrap(Futures.delayed(function, delay, unit, (ec)));
    }

    /**
     * Awaits for the promise to get the result.<br>
     * Throws a Throwable if the calculation providing the promise threw an exception
     *
     * @param timeout A user defined timeout
     * @param unit    timeout for timeout
     * @return The promised result
     * Calling get on a promise is a blocking operation and so introduces the risk of deadlocks
     * and has serious performance implications.
     * @throws PromiseTimeoutException when the promise did timeout.
     */

    public A get(long timeout, TimeUnit unit) {
        // This rather complex exception matching is to ensure that the existing (quite comprehensive)
        // tests still pass. CompletableFuture does a lot of wrapping of exceptions, and doesn't unwrap
        // them, but this API did unwrap things, so to ensure the same exceptions are thrown from the
        // existing APIs, this needed to be done.
        try {
            return this.toCompletableFuture().get(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new PromiseTimeoutException(e.getMessage(), e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof TimeoutException) {
                throw new PromiseTimeoutException(e.getCause().getMessage(), e.getCause());
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    /**
     * Awaits for the promise to get the result.<br>
     * Throws a Throwable if the calculation providing the promise threw an exception
     *
     * @param timeout A user defined timeout in milliseconds
     * @return The promised result
     * Calling get on a promise is a blocking operation and so introduces the risk of deadlocks
     * and has serious performance implications.
     * @throws PromiseTimeoutException when the promise did timeout.
     */

    public A get(long timeout) {
        return get(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Combines the current promise with <code>another</code> promise using `or`.
     *
     * @param another promise that will be combined
     *                Use {@link #applyToEither(CompletionStage, Function)} instead.
     */

    public <B> Promise<Either<A, B>> or(Promise<B> another) {
        return new Promise<>(wrapped.thenApply(Either::<A, B>Left)
                .applyToEither(another.thenApply(Either::<A, B>Right), Function.identity()));
    }

    /**
     * Perform the given <code>action</code> callback when the Promise is redeemed.
     * <p>
     * The callback will be run in the default execution context.
     *
     * @param action The action to perform.
     *               Use {@link #thenAcceptAsync(Consumer, Executor)} with {@link Execution#httpPromiseContext()} instead.
     */

    public void onRedeem(final Consumer<? super A> action) {
        wrapped.thenAcceptAsync(action, Execution.httpPromiseContext());
    }

    /**
     * Perform the given <code>action</code> callback when the Promise is redeemed.
     *
     * @param action The action to perform.
     * @param ec     The ExecutionContext to execute the action in.
     *               Use {@link #thenAcceptAsync(Consumer, Executor)} instead.
     */

    public void onRedeem(final Consumer<? super A> action, Executor ec) {
        wrapped.thenAcceptAsync(action, (ec));
    }

    /**
     * Maps this promise to a promise of type <code>B</code>.  The function <code>function</code> is applied as
     * soon as the promise is redeemed.
     * <p>
     * The function will be run in the default execution context.
     *
     * @param function The function to map <code>A</code> to <code>B</code>.
     * @return A wrapped promise that maps the type from <code>A</code> to <code>B</code>.
     * Use {@link #thenApplyAsync(Function, Executor)} with {@link Execution#httpPromiseContext()} if
     * you want to capture the current context.
     */

    public <B> Promise<B> map(final Function<? super A, ? extends B> function) {
        return new Promise<>(wrapped.thenApplyAsync(function, Execution.httpPromiseContext()));
    }

    /**
     * Maps this promise to a promise of type <code>B</code>.  The function <code>function</code> is applied as
     * soon as the promise is redeemed.
     *
     * @param function The function to map <code>A</code> to <code>B</code>.
     * @param ec       The ExecutionContext to execute the function in.
     * @return A wrapped promise that maps the type from <code>A</code> to <code>B</code>.
     * Use {@link #thenApplyAsync(Function, Executor)}
     */

    public <B> Promise<B> map(final Function<? super A, ? extends B> function, Executor ec) {
        return new Promise<>(wrapped.thenApplyAsync(function, (ec)));
    }

    /**
     * Wraps this promise in a promise that will handle exceptions thrown by this Promise.
     * <p>
     * The function will be run in the default execution context.
     *
     * @param function The function to handle the exception. This may, for example, convert the exception into something
     *                 of type <code>T</code>, or it may throw another exception, or it may do some other handling.
     * @return A wrapped promise that will only throw an exception if the supplied <code>function</code> throws an
     * exception.
     * Use {@link #exceptionally(Function)} if you don't need the current context captured,
     * or {@link #handleAsync(BiFunction, Executor)} with {@link Execution#httpPromiseContext()} if
     * you do.
     */

    public Promise<A> recover(final Function<Throwable, ? extends A> function) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                return function.apply(error);
            } else {
                return a;
            }
        }, Execution.httpPromiseContext()));
    }

    /**
     * Wraps this promise in a promise that will handle exceptions thrown by this Promise.
     *
     * @param function The function to handle the exception. This may, for example, convert the exception into something
     *                 of type <code>T</code>, or it may throw another exception, or it may do some other handling.
     * @param ec       The ExecutionContext to execute the function in.
     * @return A wrapped promise that will only throw an exception if the supplied <code>function</code> throws an
     * exception.
     * Use {@link #handleAsync(BiFunction, Executor)} instead.
     */

    public Promise<A> recover(final Function<Throwable, ? extends A> function, Executor ec) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                return function.apply(error);
            } else {
                return a;
            }
        }, (ec)));
    }

    /**
     * Creates a new promise that will handle thrown exceptions by assigning to the value of another promise.
     * <p>
     * The function will be run in the default execution context.
     *
     * @param function The function to handle the exception, and which returns another promise
     * @return A promise that will delegate to another promise on failure
     * Use {@link #exceptionally(Function)} if you don't need the current context captured,
     * or {@link #handleAsync(BiFunction, Executor)} with {@link Execution#httpPromiseContext()} if
     * you do, then use {@link #thenCompose(Function)} with the identity function to flatten the result.
     */

    public Promise<A> recoverWith(final Function<Throwable, ? extends CompletionStage<A>> function) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                return function.apply(error);
            } else {
                return CompletableFuture.completedFuture(a);
            }
        }, Execution.httpPromiseContext()).thenCompose(Function.identity()));
    }

    /**
     * Creates a new promise that will handle thrown exceptions by assigning to the value of another promise.
     *
     * @param function The function to handle the exception, and which returns another promise
     * @param ec       The ExecutionContext to execute the function in
     * @return A promise that will delegate to another promise on failure
     * Use {@link #handleAsync(BiFunction, Executor)} instead, followed by {@link #thenCompose(Function)}
     * with the identity function.
     */

    public Promise<A> recoverWith(final Function<Throwable, Promise<A>> function, Executor ec) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                return function.apply(error);
            } else {
                return CompletableFuture.completedFuture(a);
            }
        }, (ec)).thenCompose(Function.identity()));
    }

    /**
     * Creates a new promise which holds the result of this promise if it was completed successfully,
     * otherwise the result of the {@code fallback} promise if it completed successfully.
     * If both promises failed, the resulting promise holds the throwable of this promise.
     *
     * @param fallback The promise to fallback to if this promise has failed
     * @return A promise that will delegate to another promise on failure
     * Use {@link #handleAsync(BiFunction)} followed by {@link #thenCompose(Function)}
     * with the identity function.
     */

    public Promise<A> fallbackTo(final Promise<A> fallback) {
        return new Promise<>(wrapped.handle((a, error) -> {
            if (error != null) {
                return fallback.handle((fallbackA, fallbackError) -> {
                    if (fallbackError != null) {
                        CompletableFuture<A> failed = new CompletableFuture<>();
                        failed.completeExceptionally(error);
                        return failed;
                    } else {
                        return CompletableFuture.completedFuture(fallbackA);
                    }
                }).thenCompose(Function.identity());
            } else {
                return CompletableFuture.completedFuture(a);
            }
        }).thenCompose(Function.identity()));
    }

    /**
     * Perform the given <code>action</code> callback if the promise encounters an exception.
     * <p>
     * This action will be run in the default exceution context.
     *
     * @param action The action to perform.
     *               Use {@link #whenCompleteAsync(BiConsumer, Executor)} with {@link Execution#httpPromiseContext()} if
     *               you want to capture the current context.
     */

    public void onFailure(final Consumer<Throwable> action) {
        wrapped.whenCompleteAsync((a, error) -> {
            if (error != null) {
                action.accept(error);
            }
        }, Execution.httpPromiseContext());
    }

    /**
     * Perform the given <code>action</code> callback if the promise encounters an exception.
     *
     * @param action The action to perform.
     * @param ec     The ExecutionContext to execute the callback in.
     *               Use {@link #whenCompleteAsync(BiConsumer, Executor)}.
     */

    public void onFailure(final Consumer<Throwable> action, Executor ec) {
        wrapped.whenCompleteAsync((a, error) -> {
            if (error != null) {
                action.accept(error);
            }
        }, (ec));
    }

    /**
     * Maps the result of this promise to a promise for a result of type <code>B</code>, and flattens that to be
     * a single promise for <code>B</code>.
     * <p>
     * The function will be run in the default execution context.
     *
     * @param function The function to map <code>A</code> to a promise for <code>B</code>.
     * @return A wrapped promise for a result of type <code>B</code>
     * Use {@link #thenComposeAsync(Function, Executor)} with {@link Execution#httpPromiseContext()} if
     * you want to capture the current context.
     */

    public <B> Promise<B> flatMap(final Function<? super A, ? extends CompletionStage<B>> function) {
        return new Promise<>(wrapped.thenComposeAsync(function, Execution.httpPromiseContext()));
    }

    /**
     * Maps the result of this promise to a promise for a result of type <code>B</code>, and flattens that to be
     * a single promise for <code>B</code>.
     *
     * @param function The function to map <code>A</code> to a promise for <code>B</code>.
     * @param ec       The ExecutionContext to execute the function in.
     * @return A wrapped promise for a result of type <code>B</code>
     * Use {@link #thenComposeAsync(Function, Executor)}.
     */

    public <B> Promise<B> flatMap(final Function<? super A, ? extends CompletionStage<B>> function, Executor ec) {
        return new Promise<>(wrapped.thenComposeAsync(function, (ec)));
    }

    /**
     * Creates a new promise by filtering the value of the current promise with a predicate.
     * If the predicate fails, the resulting promise will fail with a `NoSuchElementException`.
     *
     * @param predicate The predicate to test the current value.
     * @return A new promise with the current value, if the predicate is satisfied.
     * Use {@link #thenApplyAsync(Function, Executor)} to implement the filter manually.
     */

    public Promise<A> filter(final Predicate<? super A> predicate) {
        return new Promise<>(wrapped.thenApplyAsync(a -> {
            if (predicate.test(a)) {
                return a;
            } else {
                throw new NoSuchElementException("Promise.filter predicate is not satisfied");
            }
        }, Execution.httpPromiseContext()));
    }

    /**
     * Creates a new promise by filtering the value of the current promise with a predicate.
     * If the predicate fails, the resulting promise will fail with a `NoSuchElementException`.
     *
     * @param predicate The predicate to test the current value.
     * @param ec        The ExecutionContext to execute the filtering in.
     * @return A new promise with the current value, if the predicate is satisfied.
     * Use {@link #thenApplyAsync(Function, Executor)} to implement the filter manually.
     */

    public Promise<A> filter(final Predicate<? super A> predicate, Executor ec) {
        return new Promise<>(wrapped.thenApplyAsync(a -> {
            if (predicate.test(a)) {
                return a;
            } else {
                throw new NoSuchElementException("Promise.filter predicate is not satisfied");
            }
        }, (ec)));
    }

    /**
     * Creates a new promise by applying the {@code onSuccess} function to a successful result,
     * or the {@code onFailure} function to a failed result.
     * <p>
     * The function will be run in the default execution context.
     *
     * @param onSuccess The function to map a successful result from {@code A} to {@code B}
     * @param onFailure The function to map the {@code Throwable} when failed
     * @return A new promise mapped by either the {@code onSuccess} or {@code onFailure} functions
     * Use {@link #handleAsync(BiFunction, Executor)} instead.
     */

    public <B> Promise<B> transform(final Function<? super A, ? extends B> onSuccess, final Function<Throwable, Throwable> onFailure) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                throw error instanceof CompletionException ? (CompletionException) error : new CompletionException(onFailure.apply(error));
            } else {
                return onSuccess.apply(a);
            }
        }, Execution.httpPromiseContext()));
    }

    /**
     * Creates a new promise by applying the {@code onSuccess} function to a successful result,
     * or the {@code onFailure} function to a failed result.
     *
     * @param onSuccess The function to map a successful result from {@code A} to {@code B}
     * @param onFailure The function to map the {@code Throwable} when failed
     * @param ec        The ExecutionContext to execute functions in
     * @return A new promise mapped by either the {@code onSuccess} or {@code onFailure} functions
     * Use {@link #handleAsync(BiFunction, Executor)} instead.
     */

    public <B> Promise<B> transform(final Function<? super A, ? extends B> onSuccess, final Function<Throwable, Throwable> onFailure, Executor ec) {
        return new Promise<>(wrapped.handleAsync((a, error) -> {
            if (error != null) {
                throw error instanceof CompletionException ? (CompletionException) error : new CompletionException(onFailure.apply(error));
            } else {
                return onSuccess.apply(a);
            }
        }, (ec)));
    }

    /**
     * Zips the values of this promise with <code>another</code>, and creates a new promise holding the tuple of their results
     *
     * @param another Use {@link #thenCombine(CompletionStage, BiFunction)} instead.
     */

    public <B> Promise<Tuple<A, B>> zip(CompletionStage<B> another) {
        return thenCombine(another, (a, b) -> new Tuple(a, b));
    }

    /**
     * Gets the Scala Future wrapped by this Promise.
     *
     * @return The Scala Future
     * Promise no longer wraps a Scala Future, use asScala instead.
     */

    public Future<A> wrapped() {
        return asScala();
    }

    /**
     * Convert this promise to a Scala future.
     * <p>
     * This is equivalent to FutureConverters.toScala(this), however, it converts the wrapped completion stage to
     * a future rather than this, which means if the wrapped completion stage itself wraps a Scala future, it will
     * simply return that wrapped future.
     *
     * @return A Scala future that is completed when this promise is completed.
     */
    public Future<A> asScala() {
        return FutureConverters.toScala(wrapped);
    }

    // delegate methods
    @Override
    public <U> Promise<U> thenApply(Function<? super A, ? extends U> fn) {
        return new Promise<>(wrapped.thenApply(fn));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super A, ? extends U> fn) {
        return new Promise<>(wrapped.thenApplyAsync(fn,Execution.httpPromiseContext()));
    }

    @Override
    public <U> Promise<U> thenApplyAsync(Function<? super A, ? extends U> fn, Executor executor) {
        return new Promise<>(wrapped.thenApplyAsync(fn, executor));
    }

    @Override
    public Promise<Void> thenAccept(Consumer<? super A> action) {
        return new Promise<>(wrapped.thenAccept(action));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super A> action) {
        return new Promise<>(wrapped.thenAcceptAsync(action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<Void> thenAcceptAsync(Consumer<? super A> action, Executor executor) {
        return new Promise<>(wrapped.thenAcceptAsync(action, executor));
    }

    @Override
    public Promise<Void> thenRun(Runnable action) {
        return new Promise<>(wrapped.thenRun(action));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action) {
        return new Promise<>(wrapped.thenRunAsync(action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<Void> thenRunAsync(Runnable action, Executor executor) {
        return new Promise<>(wrapped.thenRunAsync(action, executor));
    }

    @Override
    public <U, V> Promise<V> thenCombine(CompletionStage<? extends U> other, BiFunction<? super A, ? super U, ? extends V> fn) {
        return new Promise<>(wrapped.thenCombine(other, fn));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super A, ? super U, ? extends V> fn) {
        return new Promise<>(wrapped.thenCombineAsync(other, fn,Execution.httpPromiseContext()));
    }

    @Override
    public <U, V> Promise<V> thenCombineAsync(CompletionStage<? extends U> other, BiFunction<? super A, ? super U, ? extends V> fn, Executor executor) {
        return new Promise<>(wrapped.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> Promise<Void> thenAcceptBoth(CompletionStage<? extends U> other, BiConsumer<? super A, ? super U> action) {
        return new Promise<>(wrapped.thenAcceptBoth(other, action));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super A, ? super U> action) {
        return new Promise<>(wrapped.thenAcceptBothAsync(other, action,Execution.httpPromiseContext()));
    }

    @Override
    public <U> Promise<Void> thenAcceptBothAsync(CompletionStage<? extends U> other, BiConsumer<? super A, ? super U> action, Executor executor) {
        return new Promise<>(wrapped.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterBoth(CompletionStage<?> other, Runnable action) {
        return new Promise<>(wrapped.runAfterBoth(other, action));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action) {
        return new Promise<>(wrapped.runAfterBothAsync(other, action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<Void> runAfterBothAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return new Promise<>(wrapped.runAfterBothAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> applyToEither(CompletionStage<? extends A> other, Function<? super A, U> fn) {
        return new Promise<>(wrapped.applyToEither(other, fn));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends A> other, Function<? super A, U> fn) {
        return new Promise<>(wrapped.applyToEitherAsync(other, fn,Execution.httpPromiseContext()));
    }

    @Override
    public <U> Promise<U> applyToEitherAsync(CompletionStage<? extends A> other, Function<? super A, U> fn, Executor executor) {
        return new Promise<>(wrapped.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public Promise<Void> acceptEither(CompletionStage<? extends A> other, Consumer<? super A> action) {
        return new Promise<>(wrapped.acceptEither(other, action));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends A> other, Consumer<? super A> action) {
        return new Promise<>(wrapped.acceptEitherAsync(other, action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<Void> acceptEitherAsync(CompletionStage<? extends A> other, Consumer<? super A> action, Executor executor) {
        return new Promise<>(wrapped.acceptEitherAsync(other, action, executor));
    }

    @Override
    public Promise<Void> runAfterEither(CompletionStage<?> other, Runnable action) {
        return new Promise<>(wrapped.runAfterEither(other, action));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action) {
        return new Promise<>(wrapped.runAfterEitherAsync(other, action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<Void> runAfterEitherAsync(CompletionStage<?> other, Runnable action, Executor executor) {
        return new Promise<>(wrapped.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> Promise<U> thenCompose(Function<? super A, ? extends CompletionStage<U>> fn) {
        return new Promise<>(wrapped.thenCompose(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super A, ? extends CompletionStage<U>> fn) {
        return new Promise<>(wrapped.thenComposeAsync(fn));
    }

    @Override
    public <U> Promise<U> thenComposeAsync(Function<? super A, ? extends CompletionStage<U>> fn, Executor executor) {
        return new Promise<>(wrapped.thenComposeAsync(fn, executor));
    }

    @Override
    public Promise<A> exceptionally(Function<Throwable, ? extends A> fn) {
        return new Promise<>(wrapped.exceptionally(fn));
    }

    @Override
    public Promise<A> whenComplete(BiConsumer<? super A, ? super Throwable> action) {
        return new Promise<>(wrapped.whenComplete(action));
    }

    @Override
    public Promise<A> whenCompleteAsync(BiConsumer<? super A, ? super Throwable> action) {
        return new Promise<>(wrapped.whenCompleteAsync(action,Execution.httpPromiseContext()));
    }

    @Override
    public Promise<A> whenCompleteAsync(BiConsumer<? super A, ? super Throwable> action, Executor executor) {
        return new Promise<>(wrapped.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> Promise<U> handle(BiFunction<? super A, Throwable, ? extends U> fn) {
        return new Promise<>(wrapped.handle(fn));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super A, Throwable, ? extends U> fn) {
        return new Promise<>(wrapped.handleAsync(fn,Execution.httpPromiseContext()));
    }

    @Override
    public <U> Promise<U> handleAsync(BiFunction<? super A, Throwable, ? extends U> fn, Executor executor) {
        return new Promise<>(wrapped.handleAsync(fn, executor));
    }

    @Override
    public CompletableFuture<A> toCompletableFuture() {
        return wrapped.toCompletableFuture();
    }
}
