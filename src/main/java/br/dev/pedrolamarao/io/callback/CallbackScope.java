package br.dev.pedrolamarao.io.callback;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.Operation;

/**
 * <p>
 * Controller assumes that one of these two things 
 * will eventually happen for each registered operation:
 * either {@link #unregister(Operation)} or {@link #complete(long, boolean, int)}.
 * </p>
 */

public final class CallbackScope implements AutoCloseable
{
	// types
	
	@SuppressWarnings("preview")
	public static final record IoState (IoDevice device, Operation operation) { }
	
	// attributes
	
	private final ReentrantLock lock = new ReentrantLock();
	
	private final Phaser joiner = new Phaser(0);
	
	private final HashMap<Long, IoState> pending = new HashMap<>();
	
	// life cycle
	
	public CallbackScope ()
	{
	}
	
	public void close ()
	{
		try { join(); }
		    catch (InterruptedException e) { e.printStackTrace(); }
	}
	
	// properties
	
	public long pending ()
	{
		try (var guard = lock(lock)) { return pending.size(); }
	}
	
	// methods
	
	public void cancel ()
	{
		try (var guard = lock(lock)) {
			pending.forEach((key, thing) -> {
				try {
					thing.device().cancel(thing.operation());
				}
				catch (Throwable e) {
					// #TODO: what?
					e.printStackTrace();
				}
			});
		}
	}
	
	public void complete (long operation) throws Throwable
	{
		final IoState thing;
		try (var guard = lock(lock)) { thing = pending.remove(operation); }
		if (thing == null) {
			// #TODO
			return;
		}
		thing.operation().close();
		joiner.arriveAndDeregister();
	}
	
	public void join () throws InterruptedException
	{
		joiner.register();
		joiner.awaitAdvanceInterruptibly(joiner.arriveAndDeregister());
	}
	
	public void join (Duration timeLimit) throws InterruptedException, TimeoutException
	{
		joiner.register();
		joiner.awaitAdvanceInterruptibly(joiner.arriveAndDeregister(), timeLimit.toMillis(), TimeUnit.MILLISECONDS);
	}
	
	public Operation register (IoDevice device)
	{
		final var operation = new Operation();
		final var key = operation.handle().toRawLongValue();
		try (var guard = lock(lock)) { pending.put(key, new IoState(device, operation)); }
		joiner.register();
		return operation;
	}
	
	public void unregister (Operation operation)
	{
		final var key = operation.handle().toRawLongValue();
		try (var guard = lock(lock)) { pending.remove(key); }
		joiner.arriveAndDeregister();
		operation.close();
	}
	
	@FunctionalInterface
	public interface PureCloseable extends AutoCloseable
	{
		void close ();
	}
	
	private static PureCloseable lock (ReentrantLock lock)
	{
		lock.lock();
		return () -> lock.unlock();
	}
}
