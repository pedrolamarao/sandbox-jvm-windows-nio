package br.dev.pedrolamarao.io.callback;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import jdk.incubator.foreign.MemoryAddress;

public final class CallbackMachine
{
	// types
	
	@FunctionalInterface
	public interface IoHandler
	{
		void handle (long operation, boolean success, int data) throws Throwable;
	}
	
	//
	
	private final Bus bus;
	
	private long counter = 1;
	
	private final ConcurrentHashMap<Long, IoHandler> handlers = new ConcurrentHashMap<>();
		
	private final ScheduledExecutorService timers;
	
	private final ExecutorService workers;
	
	//
	
	public CallbackMachine () throws Throwable
	{
		this.bus = new Bus();
		this.timers = Executors.newSingleThreadScheduledExecutor();
		this.workers = Executors.newSingleThreadExecutor();
		this.workers.submit(this::run);
	}
	
	public void close () throws Exception
	{
		try { interrupt(); }
		    catch (Throwable e) { }
		bus.close();
		workers.shutdownNow();
	}
	
	// methods
	
	public void interrupt () throws Throwable
	{
		bus.push(0, MemoryAddress.ofLong(0), 0);
	}
	
	public void push (long key, MemoryAddress operation, int data) throws Throwable
	{
		bus.push(key, operation, data);
	}
	
	public void register (IoDevice device, IoHandler handler) throws Throwable
	{
		final var key = counter++;		
		handlers.put(key, handler);
		bus.register(key, device);
	}
	
	public CallbackScope scope ()
	{
		return new CallbackScope();
	}
	
	public CallbackScope scope (Duration timeLimit)
	{
		final var scope = new CallbackScope();
		Runnable expire = () -> scope.cancel();
		timers.schedule(expire, timeLimit.toMillis(), TimeUnit.MILLISECONDS);
		return scope;
	}
	
	private void run ()
	{
		while (true) try
		{
			final var pulled = bus.pull(Duration.ofMillis(Long.MAX_VALUE));
			if (pulled.isEmpty()) {
				continue;
			}
			final var event = pulled.get();
			final var key = event.key();
			if (key == 0) {
				Thread.currentThread().interrupt();
				return;
			}
			final var handler = handlers.get(key);
			if (handler == null) {
				// #TODO: what?
				continue;
			}
			handler.handle(event.operation().toRawLongValue(), event.status(), event.data());
		}
		catch (Throwable t)
		{
			// #TODO: what?
			t.printStackTrace();
			return;
		}
	}
}
