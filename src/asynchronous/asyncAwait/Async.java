package asynchronous.asyncAwait;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import asynchronous.CoThread;
import asynchronous.*;
import asynchronous.futures.Deferred;
import asynchronous.futures.Promise;
import asynchronous.futures.exceptions.FutureCancellationException;
import exceptionsPlus.UncheckedWrapper;
import functionPlus.*;
import message.*;

/**
 * Asynchronous function used for asynchronous programming. Call Async.execute at the end of the main method to run called Async functions.
 * @author jesse
 *
 * @param <T>
 */
public class Async {
	private final AtomicInteger runningInstanceCount = new AtomicInteger(0);
	private final Queue<AsyncSupplier<?>.CalledInstance> executionQueue = new ConcurrentLinkedQueue<>();
	private final Object executeWaitLock = new Object();
	
	/**
	 * Notify Async class that the instance has started
	 */
	private void asyncStartNotify(AsyncSupplier<?>.CalledInstance inst) {
		synchronized(executeWaitLock) {
			executionQueue.add(inst);
			runningInstanceCount.incrementAndGet();
			executeWaitLock.notify();
		}
	}
	
	/**
	 * Notify Async class that an awaited promise has completed
	 * @param inst The instance awaiting the promise
	 */
	private void asyncAwaitCompleteNotify(AsyncSupplier<?>.CalledInstance inst) {
		synchronized(executeWaitLock) {
			executionQueue.add(inst);
			executeWaitLock.notify();
		}
	}
	
	/**
	 * Notify Async class that an instance has completed
	 */
	private void asyncCompleteNotify() {
		synchronized(executeWaitLock) {
			runningInstanceCount.decrementAndGet();
			executeWaitLock.notify();
		}
	}
	
	/**
	 * Runs all Async instances in the execution queue.
	 * @throws InterruptedException
	 */
	public void execute(VolitileMessenger<Integer> maxThreadCount, VolitileMessenger<Boolean> listen, VolitileMessenger<Boolean> stop) throws InterruptedException {
		maxThreadCount.onChange(v -> {
			synchronized(executeWaitLock) {
				executeWaitLock.notifyAll();
			}
		});
		stop.onChange(v -> {
			if (v == false)
				return;
			
			synchronized(executeWaitLock) {
				executeWaitLock.notifyAll();
			}
		});
		listen.onChange(v -> {	
			synchronized(executeWaitLock) {
				executeWaitLock.notifyAll();
			}
		});
		
		final var threadCount = new AtomicInteger();
		
		// execution loop
		do {
			AsyncSupplier<?>.CalledInstance instance;
			while(!stop.get() && threadCount.get() < maxThreadCount.get() && (instance = executionQueue.poll()) != null) {
				threadCount.incrementAndGet();
				
				// execute the instance
				// the returned promise will tell us when the instance yields again or if it completes or throws an error.
				instance.execute().onSettledRun(() -> {
					threadCount.decrementAndGet();
					synchronized(executeWaitLock) {
						executeWaitLock.notifyAll();
					}
				});
			}
			
			synchronized(executeWaitLock) {
				executeWaitLock.notifyAll();
				while(!stop.get()) {
					// if the max thread count is zero, pause.
					if (maxThreadCount.get() != 0) {
						// exit conditions
						if (!listen.get() && executionQueue.isEmpty() && runningInstanceCount.get() == 0) break;
						// resume conditions
						if (!executionQueue.isEmpty()) break;						
					}
					
					executeWaitLock.wait();
				}
			}
		} while(!stop.get() && !(!listen.get() && executionQueue.isEmpty() && runningInstanceCount.get() == 0));
	}
	
	public void execute(VolitileMessenger<Boolean> listen, VolitileMessenger<Boolean> stop) throws InterruptedException{
		execute(new VolitileMessenger<Integer>(1), listen, stop);
	}
	
	public void execute(VolitileMessenger<Integer> maxThreadCount) throws InterruptedException{
		execute(maxThreadCount, new VolitileMessenger<Boolean>(false), new VolitileMessenger<Boolean>(false));
	}
	
	public void execute(int maxThreadCount, boolean listen) throws InterruptedException{
		execute(new VolitileMessenger<Integer>(maxThreadCount), new VolitileMessenger<Boolean>(listen), new VolitileMessenger<Boolean>(false));
	}
	
	public void execute(int maxThreadCount) throws InterruptedException{
		execute(new VolitileMessenger<Integer>(maxThreadCount), new VolitileMessenger<Boolean>(false), new VolitileMessenger<Boolean>(false));
	}
	
	public void execute(int maxThreadCount, boolean listen, VolitileMessenger<Boolean> stop) throws InterruptedException{
		execute(new VolitileMessenger<Integer>(maxThreadCount), new VolitileMessenger<Boolean>(listen), stop);
	}
	
	public void execute(int maxThreadCount, VolitileMessenger<Boolean> stop) throws InterruptedException{
		execute(new VolitileMessenger<Integer>(maxThreadCount), new VolitileMessenger<Boolean>(false), stop);
	}
	
	public void execute() throws InterruptedException{
		execute(new VolitileMessenger<Integer>(1), new VolitileMessenger<Boolean>(false), new VolitileMessenger<Boolean>(false));
	}
	
	
	// Await functional class for awaiting futures in an Async functional class.
	public class Await{
		private final CoThread<Promise<?>>.Yield yields;
		
		// can't be instantiated by the user. Only Async and itself (but only Async should)
		private Await(CoThread<Promise<?>>.Yield yields) {
			this.yields = yields;
		}
		
		/**
		 * Awaits the given future, returning it's result when it's resolved.
		 * @param <T> The type of the future.
		 * @param future The future to await.
		 * @param onCancel called if the future is canceled. if null: throws a PromiseCancellationException if the future is canceled.
		 * @return result of future or null if the future is null.
		 * @throws UncheckedWrapper Wrapper around all Exceptions checked and un-checked. Will contain whatever exception was thrown.
		 * This is the only exception thrown by await.apply. PromiseCancellationException: if the future is canceled and onCancel was null.
		 */
		public <T> T apply(Future<T> future, Supplier<T> onCancel) throws UncheckedWrapper, FutureCancellationException {
			if (future == null)
				return null;
			
			final var promise = Promise.fromFuture(future);
			
			try {
				// yields to Async.execute. wait for the promise to complete. Async.execute will take care of that.
				yields.accept(promise);
				
				// at this point yields has stopped blocking which should mean that the promise is complete.
				if (promise.isFulfilled()) {
					return promise.getResult();
				}
				else if (promise.isRejected()) {
					throw promise.getError();
				}
				else if (promise.isCanceled()) {
					if (onCancel == null)
						throw new FutureCancellationException(future);
					else
						return onCancel.get();
				}
				else if (promise.isSettled()) {
					throw new IllegalStateException("The promise given to Async.Await.apply has been settled but is not fulfilled, rejected, or canceled.");
				}
				else {
					throw new IllegalStateException("The promise given to Async.Await.apply has not been settled after yielding.");
				}
			}
			catch(Throwable e) {
				throw UncheckedWrapper.uncheckify(e);
			}
		}
		
		public <T> T apply(Future<T> future) throws UncheckedWrapper {
			return apply(future, null);
		}
		
		/**
		 * Asynchronously waits for the given function to run in a separate thread.
		 * @return whatever was returned by the function;
		 */
		public <T> T func(Supplier<T> func) {
			return apply(Promise.asyncGet(func).promise);
		}
		
		/**
		 * Asynchronously waits for the given function to run in a separate thread.
		 * */
		public void func(Runnable func) {
			apply(Promise.asyncRun(func).promise);
		}
		
		// utils:
		/**
		 * Asynchronous sleep function. May sleep for longer than the specified time while the instance waits its turn to execute again.
		 */
		public void sleep(long milliseconds, int nanoseconds) {
			apply(Timing.setTimeout(() -> null, milliseconds, nanoseconds));
		}
		
		/**
		 * Asynchronous sleep function. May sleep for longer than the specified time while the instance waits its turn to execute again.
		 */
		public void sleep(long milliseconds) {
			apply(Timing.setTimeout(() -> null, milliseconds));
		}
	}
	
	
	// o-------------------o
	// | function classes: |
	// o-------------------o
	/**
	 * Asynchronous function used for asynchronous programming. Call Async.execute at the end of the main method to run called Async functions.
	 * @author jesse
	 *
	 * @param <T>
	 */
	public class AsyncSupplier<T> implements Supplier<Promise<T>> {
		private final Function<Await, T> func;
		private final String name;
		
		public String getName() { return name; }
		
		public AsyncSupplier(Function<Await, T> func) {
			this(func, null);
		}
		
		public AsyncSupplier(Function<Await, T> func, String name) {
			this.func = func;
			if (name == null)
				this.name = "async";
			else
				this.name = name;
		}
		
		public Promise<T> get(){
			var inst = new CalledInstance();
			return inst.start();
		}
		
		
		/**
		 * Call to an Async function.
		 * @author jesse
		 *
		 */
		private class CalledInstance {
			private final CoThread<Promise<?>> coThread;
			private volatile T result = null;
			private volatile Deferred<T> deferred;
			
			CalledInstance() {
				coThread = new CoThread<>(yields -> {
					result = func.apply(new Await(yields));
				}, name);
			}
			
			private synchronized Promise<T> start(){				
				// make a new promise and extract resolve and reject methods
				deferred = new Deferred<T>();
				
				// add callback to promise that decrements running instance count when the call completes.
				deferred.promise().onSettledRun(() -> asyncCompleteNotify());
				
				// Notify Async class that this instance has started.
				asyncStartNotify(this);
				
				// This promise will resolve when the instance completes successfully, and reject when an error occurs
				return deferred.promise();
			}
			
			private synchronized Promise<Result<Promise<?>>> execute() {
				return coThread.run().thenAccept(result -> {
					if (result.undefined) {
						coThread.close();
						deferred.settle().resolve(this.result);
					} else {
						result.value.onSettledRun(() -> 
							asyncAwaitCompleteNotify(this));
					}
				}, error ->{
					// threw an error:
					deferred.settle().reject(error);
					//
				}, () -> {
					// was canceled for some reason:
					deferred.settle().cancel();
					//
				});
			}
		}
	}
	public class AsyncRunnable implements Supplier<Promise<Void>>{
		private final AsyncSupplier<Void> async;
		
		public AsyncRunnable(Consumer<Await> func, String name) {
			async = new AsyncSupplier<Void>(
					await -> { func.accept(await); return null; }, name);
		}
		
		public AsyncRunnable(Consumer<Await> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> get(){
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncFunction<T1, R> implements Function<T1, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[1];
		
		public AsyncFunction(BiFunction<Await, T1, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0]), name);
		}
		
		public AsyncFunction(BiFunction<Await, T1, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1){
			args[0] = t1;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncConsumer<T1> implements Function<T1, Promise<Void>>{
		private final AsyncFunction<T1, Void> async;
		
		public AsyncConsumer(BiConsumer<Await, T1> func, String name) {
			async = new AsyncFunction<T1, Void>(
					(await, t1) -> { func.accept(await, t1); return null; }, name);
		}
		
		public AsyncConsumer(BiConsumer<Await, T1> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1) {
			return async.apply(t1);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncBiFunction<T1, T2, R> implements BiFunction<T1, T2, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[2];
		
		public AsyncBiFunction(TriFunction<Await, T1, T2, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1]), name);
		}
		
		public AsyncBiFunction(TriFunction<Await, T1, T2, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2){
			args[0] = t1;
			args[1] = t2;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncBiConsumer<T1, T2> implements BiFunction<T1, T2, Promise<Void>>{
		private final AsyncBiFunction<T1, T2, Void> async;
		
		public AsyncBiConsumer(TriConsumer<Await, T1, T2> func, String name) {
			async = new AsyncBiFunction<T1, T2, Void>(
					(await, t1, t2) -> { func.accept(await, t1, t2); return null; }, name);
		}
		
		public AsyncBiConsumer(TriConsumer<Await, T1, T2> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2) {
			return async.apply(t1, t2);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncTriFunction<T1, T2, T3, R> implements TriFunction<T1, T2, T3, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[3];
		
		public AsyncTriFunction(QuadFunction<Await, T1, T2, T3, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2]), name);
		}
		
		public AsyncTriFunction(QuadFunction<Await, T1, T2, T3, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncTriConsumer<T1, T2, T3> implements TriFunction<T1, T2, T3, Promise<Void>>{
		private final AsyncTriFunction<T1, T2, T3, Void> async;
		
		public AsyncTriConsumer(QuadConsumer<Await, T1, T2, T3> func, String name) {
			async = new AsyncTriFunction<T1, T2, T3, Void>(
					(await, t1, t2, t3) -> { func.accept(await, t1, t2, t3); return null; }, name);
		}
		
		public AsyncTriConsumer(QuadConsumer<Await, T1, T2, T3> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3) {
			return async.apply(t1, t2, t3);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncQuadFunction<T1, T2, T3, T4, R> implements QuadFunction<T1, T2, T3, T4, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[4];
		
		public AsyncQuadFunction(PentaFunction<Await, T1, T2, T3, T4, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3]), name);
		}
		
		public AsyncQuadFunction(PentaFunction<Await, T1, T2, T3, T4, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			args[3] = t4;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncQuadConsumer<T1, T2, T3, T4> implements QuadFunction<T1, T2, T3, T4, Promise<Void>>{
		private final AsyncQuadFunction<T1, T2, T3, T4, Void> async;
		
		public AsyncQuadConsumer(PentaConsumer<Await, T1, T2, T3, T4> func, String name) {
			async = new AsyncQuadFunction<T1, T2, T3, T4, Void>(
					(await, t1, t2, t3, t4) -> { func.accept(await, t1, t2, t3, t4); return null; }, name);
		}
		
		public AsyncQuadConsumer(PentaConsumer<Await, T1, T2, T3, T4> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4) {
			return async.apply(t1, t2, t3, t4);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncPentaFunction<T1, T2, T3, T4, T5, R> implements PentaFunction<T1, T2, T3, T4, T5, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[5];
		
		public AsyncPentaFunction(HexaFunction<Await, T1, T2, T3, T4, T5, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4]), name);
		}
		
		public AsyncPentaFunction(HexaFunction<Await, T1, T2, T3, T4, T5, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			args[3] = t4;
			args[4] = t5;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncPentaConsumer<T1, T2, T3, T4, T5> implements PentaFunction<T1, T2, T3, T4, T5, Promise<Void>>{
		private final AsyncPentaFunction<T1, T2, T3, T4, T5, Void> async;
		
		public AsyncPentaConsumer(HexaConsumer<Await, T1, T2, T3, T4, T5> func, String name) {
			async = new AsyncPentaFunction<T1, T2, T3, T4, T5, Void>(
					(await, t1, t2, t3, t4, t5) -> { func.accept(await, t1, t2, t3, t4, t5); return null; }, name);
		}
		
		public AsyncPentaConsumer(HexaConsumer<Await, T1, T2, T3, T4, T5> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5) {
			return async.apply(t1, t2, t3, t4, t5);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> implements HexaFunction<T1, T2, T3, T4, T5, T6, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[6];
		
		public AsyncHexaFunction(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4], (T6)args[5]), name);
		}
		
		public AsyncHexaFunction(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			args[3] = t4;
			args[4] = t5;
			args[5] = t6;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> implements HexaFunction<T1, T2, T3, T4, T5, T6, Promise<Void>>{
		private final AsyncHexaFunction<T1, T2, T3, T4, T5, T6, Void> async;
		
		public AsyncHexaConsumer(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func, String name) {
			async = new AsyncHexaFunction<T1, T2, T3, T4, T5, T6, Void>(
					(await, t1, t2, t3, t4, t5, t6) -> { func.accept(await, t1, t2, t3, t4, t5, t6); return null; }, name);
		}
		
		public AsyncHexaConsumer(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6) {
			return async.apply(t1, t2, t3, t4, t5, t6);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> implements HeptaFunction<T1, T2, T3, T4, T5, T6, T7, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[7];
		
		public AsyncHeptaFunction(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4], (T6)args[5], (T7)args[6]), name);
		}
		
		public AsyncHeptaFunction(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			args[3] = t4;
			args[4] = t5;
			args[5] = t6;
			args[6] = t7;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> implements HeptaFunction<T1, T2, T3, T4, T5, T6, T7, Promise<Void>>{
		private final AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, Void> async;
		
		public AsyncHeptaConsumer(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func, String name) {
			async = new AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, Void>(
					(await, t1, t2, t3, t4, t5, t6, t7) -> { func.accept(await, t1, t2, t3, t4, t5, t6, t7); return null; }, name);
		}
		
		public AsyncHeptaConsumer(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7) {
			return async.apply(t1, t2, t3, t4, t5, t6, t7);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<R>>{
		private final AsyncSupplier<R> async;
		private final Object[] args = new Object[8];
		
		public AsyncOctoFunction(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func, String name) {
			async = new AsyncSupplier<R>(
					await -> func.apply(await, (T1)args[0], (T2)args[1], (T3)args[2], (T4)args[3], (T5)args[4], (T6)args[5], (T7)args[6], (T8)args[7]), name);
		}
		
		public AsyncOctoFunction(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func) {
			this(func, null);
		}
		
		public synchronized Promise<R> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8){
			args[0] = t1;
			args[1] = t2;
			args[2] = t3;
			args[3] = t4;
			args[4] = t5;
			args[5] = t6;
			args[6] = t7;
			args[7] = t8;
			return async.get();
		}
		
		public String getName() {
			return async.getName();
		}
	}
	public class AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> implements OctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Promise<Void>>{
		private final AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Void> async;
		
		public AsyncOctoConsumer(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func, String name) {
			async = new AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, Void>(
					(await, t1, t2, t3, t4, t5, t6, t7, t8) -> { func.accept(await, t1, t2, t3, t4, t5, t6, t7, t8); return null; }, name);
		}
		
		public AsyncOctoConsumer(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func) {
			this(func, null);
		}
		
		public synchronized Promise<Void> apply(T1 t1, T2 t2, T3 t3, T4 t4, T5 t5, T6 t6, T7 t7, T8 t8) {
			return async.apply(t1, t2, t3, t4, t5, t6, t7, t8);
		}
		
		public String getName() {
			return async.getName();
		}
	}
	
	// o------o
	// | def: |
	// o------o
	public <R> AsyncSupplier<R> def(Function<Await, R> func){
		return new AsyncSupplier<>(func);
	}
	public AsyncRunnable def(Consumer<Await> func){
		return new AsyncRunnable(func);
	}
	public <T1, R> AsyncFunction<T1, R> def(BiFunction<Await, T1, R> func){
		return new AsyncFunction<>(func);
	}
	public <T> AsyncConsumer<T> def(BiConsumer<Await, T> func){
		return new AsyncConsumer<>(func);
	}
	public <T1, T2, R> AsyncBiFunction<T1, T2, R> def(TriFunction<Await, T1, T2, R> func){
		return new AsyncBiFunction<>(func);
	}
	public <T1, T2> AsyncBiConsumer<T1, T2> def(TriConsumer<Await, T1, T2> func){
		return new AsyncBiConsumer<>(func);
	}
	public <T1, T2, T3, R> AsyncTriFunction<T1, T2, T3, R> def(QuadFunction<Await, T1, T2, T3, R> func){ 
		return new AsyncTriFunction<>(func); 
	}
	public <T1, T2, T3> AsyncTriConsumer<T1, T2, T3> def(QuadConsumer<Await, T1, T2, T3> func){
		return new AsyncTriConsumer<>(func);
	}
	public <T1, T2, T3, T4, R> AsyncQuadFunction<T1, T2, T3, T4, R> def(PentaFunction<Await, T1, T2, T3, T4, R> func){
		return new AsyncQuadFunction<>(func);
	}
	public <T1, T2, T3, T4> AsyncQuadConsumer<T1, T2, T3, T4> def(PentaConsumer<Await, T1, T2, T3, T4> func){
		return new AsyncQuadConsumer<>(func);
	}
	public <T1, T2, T3, T4, T5, R> AsyncPentaFunction<T1, T2, T3, T4, T5, R> def(HexaFunction<Await, T1, T2, T3, T4, T5, R> func){
		return new AsyncPentaFunction<>(func);
	}
	public <T1, T2, T3, T4, T5> AsyncPentaConsumer<T1, T2, T3, T4, T5> def(HexaConsumer<Await, T1, T2, T3, T4, T5> func){
		return new AsyncPentaConsumer<>(func);
	}
	public <T1, T2, T3, T4, T5, T6, R> AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> def(HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func){
		return new AsyncHexaFunction<>(func);
	}
	public <T1, T2, T3, T4, T5, T6> AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> def(HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func){
		return new AsyncHexaConsumer<>(func);
	}
	public <T1, T2, T3, T4, T5, T6, T7, R> AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> def(OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func){
		return new AsyncHeptaFunction<>(func);
	}
	public <T1, T2, T3, T4, T5, T6, T7> AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> def(OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func){
		return new AsyncHeptaConsumer<>(func);
	}
	public <T1, T2, T3, T4, T5, T6, T7, T8, R> AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> def(NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func){
		return new AsyncOctoFunction<>(func);
	}
	public <T1, T2, T3, T4, T5, T6, T7, T8> AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> def(NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func){
		return new AsyncOctoConsumer<>(func);
	}
	
	
	public <R> AsyncSupplier<R> def(String name, Function<Await, R> func){
		return new AsyncSupplier<>(func, name);
	}
	public AsyncRunnable def(String name, Consumer<Await> func){
		return new AsyncRunnable(func, name);
	}
	public <T1, R> AsyncFunction<T1, R> def(String name, BiFunction<Await, T1, R> func){
		return new AsyncFunction<>(func, name);
	}
	public <T> AsyncConsumer<T> def(String name, BiConsumer<Await, T> func){
		return new AsyncConsumer<>(func, name);
	}
	public <T1, T2, R> AsyncBiFunction<T1, T2, R> def(String name, TriFunction<Await, T1, T2, R> func){
		return new AsyncBiFunction<>(func, name);
	}
	public <T1, T2> AsyncBiConsumer<T1, T2> def(String name, TriConsumer<Await, T1, T2> func){
		return new AsyncBiConsumer<>(func, name);
	}
	public <T1, T2, T3, R> AsyncTriFunction<T1, T2, T3, R> def(String name, QuadFunction<Await, T1, T2, T3, R> func){ 
		return new AsyncTriFunction<>(func, name); 
	}
	public <T1, T2, T3> AsyncTriConsumer<T1, T2, T3> def(String name, QuadConsumer<Await, T1, T2, T3> func){
		return new AsyncTriConsumer<>(func, name);
	}
	public <T1, T2, T3, T4, R> AsyncQuadFunction<T1, T2, T3, T4, R> def(String name, PentaFunction<Await, T1, T2, T3, T4, R> func){
		return new AsyncQuadFunction<>(func, name);
	}
	public <T1, T2, T3, T4> AsyncQuadConsumer<T1, T2, T3, T4> def(String name, PentaConsumer<Await, T1, T2, T3, T4> func){
		return new AsyncQuadConsumer<>(func, name);
	}
	public <T1, T2, T3, T4, T5, R> AsyncPentaFunction<T1, T2, T3, T4, T5, R> def(String name, HexaFunction<Await, T1, T2, T3, T4, T5, R> func){
		return new AsyncPentaFunction<>(func, name);
	}
	public <T1, T2, T3, T4, T5> AsyncPentaConsumer<T1, T2, T3, T4, T5> def(String name, HexaConsumer<Await, T1, T2, T3, T4, T5> func){
		return new AsyncPentaConsumer<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6, R> AsyncHexaFunction<T1, T2, T3, T4, T5, T6, R> def(String name, HeptaFunction<Await, T1, T2, T3, T4, T5, T6, R> func){
		return new AsyncHexaFunction<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6> AsyncHexaConsumer<T1, T2, T3, T4, T5, T6> def(String name, HeptaConsumer<Await, T1, T2, T3, T4, T5, T6> func){
		return new AsyncHexaConsumer<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6, T7, R> AsyncHeptaFunction<T1, T2, T3, T4, T5, T6, T7, R> def(String name, OctoFunction<Await, T1, T2, T3, T4, T5, T6, T7, R> func){
		return new AsyncHeptaFunction<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6, T7> AsyncHeptaConsumer<T1, T2, T3, T4, T5, T6, T7> def(String name, OctoConsumer<Await, T1, T2, T3, T4, T5, T6, T7> func){
		return new AsyncHeptaConsumer<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6, T7, T8, R> AsyncOctoFunction<T1, T2, T3, T4, T5, T6, T7, T8, R> def(String name, NonaFunction<Await, T1, T2, T3, T4, T5, T6, T7, T8, R> func){
		return new AsyncOctoFunction<>(func, name);
	}
	public <T1, T2, T3, T4, T5, T6, T7, T8> AsyncOctoConsumer<T1, T2, T3, T4, T5, T6, T7, T8> def(String name, NonaConsumer<Await, T1, T2, T3, T4, T5, T6, T7, T8> func){
		return new AsyncOctoConsumer<>(func, name);
	}
	
	// special:
	public AsyncRunnable defRunnable(Consumer<Await> func) {
		return new AsyncRunnable(func);
	}
	public AsyncRunnable defRunnable(String name, Consumer<Await> func) {
		return new AsyncRunnable(func, name);
	}
	// this took forever to type
}

















