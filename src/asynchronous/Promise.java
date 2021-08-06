package asynchronous;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.*;

import exceptionsPlus.UncheckedException;
/**
 *
 * @author jesse
 */
public class Promise<T> {
    private Queue<Consumer<T>> thenQueue = new LinkedList<>();
    private Queue<Promise<?>> thenPromiseQueue = new LinkedList<>();
    private Queue<Consumer<Exception>> errorQueue = new LinkedList<>();
    private boolean resolved = false;
    private boolean errored = false;
    private T result;
    private Exception exception;
    
    public boolean isResolved() { return resolved; }
    public boolean isErrored() { return errored; }
    public T getResult() { return result; }
    
    Promise(){}
    
    public Promise(BiConsumer<Consumer<T>, Consumer<Exception>> func) {
        func.accept((r) -> resolve(r), (e) -> reject(e));
    }

    public Promise(Consumer<Consumer<T>> func){
        func.accept((t) -> resolve(t));
    }
    
    private void runThenQueue(){
    	Consumer<T> then;
    	while((then = thenQueue.poll()) != null) {
    		then.accept(result);
    	}
    }
    
    private void runErrorQueue() {
    	Consumer<Exception> error;
    	while((error = errorQueue.poll()) != null) {
    		error.accept(exception);
    	}
    	
    	// error out all then's
    	Promise<?> prom;
    	while((prom = thenPromiseQueue.poll()) != null) {
    		prom.reject(exception);
    	}
    	
    	thenQueue.clear();
    }
    
    synchronized void resolve(T result) {
        // cancel if this promise has already been resolved.
        if (resolved || errored) {return;}
        
        // take the result
        this.result = result;
        
        // set resolved
        resolved = true;
        
        // run the next function (given to us by the then method)
        runThenQueue();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    

    synchronized void reject(Exception exception) {
        // take the result
        this.exception = exception;
        
        // set resolved
        errored = true;
        
        // run the next function (given to us by the then method)
        runErrorQueue();
        
        // notify threads stuck in the await function that the wait is over.
        notifyAll();
    }
    
    public synchronized T await() throws InterruptedException {
    	while(!resolved && !errored) {
    		wait();
    	}
    	
    	if (resolved) {
    		return result;
    	}
    	else {
    		throw new UncheckedException(exception);
    	}
    }

    // o-----------------o
    // | Then and Error: |
    // o-----------------o
    public synchronized <R> Promise<R> then(Function<T, R> func) {
    	final var prom = new Promise<R>(resolve -> {
    		thenQueue.add(r -> {
    			resolve.accept(func.apply(r));
    		});
    	});
    	thenPromiseQueue.add(prom);
    	if (errored) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncThen(Function<T, Promise<R>> func) {
    	final var prom = new Promise<R>((resolve, reject) -> {
    		thenQueue.add(r -> {
    			final var funcProm = func.apply(r);
    			funcProm.then(r2 -> {resolve.accept(r2);});
    			funcProm.error(e -> {reject.accept(e);});
    		});
    	});
    	thenPromiseQueue.add(prom);
    	if (errored) {
    		runErrorQueue();
    	}
    	if (resolved) {
    		runThenQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> error(Function<Exception, R> func) {
    	final var prom = new Promise<R>(resolve -> {
    		errorQueue.add(e -> {
    			resolve.accept(func.apply(e));
    		});
    	});
    	if (errored) {
    		runErrorQueue();
    	}
    	return prom;
    }
    
    public synchronized <R> Promise<R> asyncError(Function<Exception, Promise<R>> func) {
    	final var prom = new Promise<R>(resolve -> {
    		errorQueue.add(e -> {
    			func.apply(e).then(r -> {resolve.accept(r);});
    		});
    	});
    	if (errored) {
    		runErrorQueue();
    	}
    	return prom;
    }
    
    public synchronized Promise<T> then(Consumer<T> func) {
        return then((r) -> {
            func.accept(r);
            return result;
        });
    }
    
    public synchronized Promise<T> then(Runnable func) {
        return then((r) -> {
            func.run();
            return result;
        });
    }
    
    public synchronized <R> Promise<R> then(Supplier<R> func) {
        return then((r) -> {
            return func.get();
        });
    }
    
    public synchronized <R> Promise<R> asyncThen(Supplier<Promise<R>> func) {
        return asyncThen((r) -> {
            return func.get();
        });
    }
    
    public synchronized Promise<Exception> error(Consumer<Exception> func) {
        return error((e) -> {
            func.accept(e);
            return exception;
        });
    }
    
    public synchronized Promise<Exception> error(Runnable func) {
        return error((e) -> {
            func.run();
            return exception;
        });
    }
    
    public synchronized <R> Promise<R> error(Supplier<R> func) {
        return error((e) -> {
            return func.get();
        });
    }
    
    public synchronized <R> Promise<R> asyncError(Supplier<Promise<R>> func) {
        return asyncError((e) -> {
            return func.get();
        });
    }
    // END Then and Error
}