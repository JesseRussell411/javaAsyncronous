package asynchronous.futures;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import asynchronous.TaskCancelException;

/** contains a promise with a public method to cancel */
public class Task<T> implements Future<T>{
	public final Promise<T> promise;
	public BiConsumer<TaskCancelException, Boolean> onCancel;
	
	public Task(BiConsumer<Function<T, Boolean>, Function<Throwable, Boolean>> initializer, BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel = onCancel;
	}
	public Task(Consumer<Promise<T>.Settle> initializer, BiConsumer<TaskCancelException, Boolean> onCancel) {
		promise = new Promise<T>(initializer);
		this.onCancel = onCancel;
	}
	
	Task(Promise<T> promise, BiConsumer<TaskCancelException, Boolean> onCancel){
		this.promise = promise;
		this.onCancel = onCancel;
	}
	
	Task(Promise<T> promise){
		this.promise = promise;
		onCancel = (e, b) -> {};
	}
	
	Task(BiConsumer<TaskCancelException, Boolean> onCancel){
		promise = new Promise<T>();
		this.onCancel = onCancel;
		
	}
	
	Task(){
		promise = new Promise<T>();
		onCancel = (e, b) -> {};
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(promise) {
			if (promise.isSettled())
				return false;
			else {
				final var cancelError = new TaskCancelException(this);
				onCancel.accept(cancelError, mayInterruptIfRunning);
				promise.reject(cancelError);
				return true;
			}
		}
	}
	@Override
	public boolean isCancelled() {
		return promise.getError() instanceof TaskCancelException tce && tce.getTask() == this;
	}
	@Override
	public boolean isDone() {
		return promise.isDone();
	}
	@Override
	public T get() throws InterruptedException, ExecutionException {
		return promise.get();
	}
	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return promise.get(timeout, unit);
	}
	
	public static <T> Task<T> threadInit(Consumer<Promise<T>.Settle> initializer, BiConsumer<TaskCancelException, Boolean> onCancel){
		final var thread = new Thread<T>();
		
	}
	
	public static Task<Void> asyncRun(Runnable func){
		final var task = new Task<Void>();
		final var thread = new Thread(() -> {
			try {
				func.run();
				task.promise.resolve(null);
			}
			catch(Throwable e) {
				task.promise.reject(e);
			}
		});
		task.onCancel = (exception, interruptIfRunning) -> thread.interrupt();
		thread.start();
		return task;
	}
	
	public static <T> Task<T> asyncGet(Supplier<T> func){
		final var task = new Task<T>();
		final var thread = new Thread(() -> {
			try {
				task.promise.resolve(func.get());
			}
			catch(Throwable e) {
				task.promise.reject(e);
			}
		});
		task.onCancel = (exception, interruptIfRunning) -> thread.interrupt();
		thread.start();
		return task;
	}

}
