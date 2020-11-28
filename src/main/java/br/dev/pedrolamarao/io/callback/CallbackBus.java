package br.dev.pedrolamarao.io.callback;

import java.time.Duration;
import java.util.HashMap;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import jdk.incubator.foreign.MemoryAddress;

public final class CallbackBus
{
	@FunctionalInterface
	public interface IoHandler
	{
		void handle (long operation, boolean status, int result) throws Throwable;
	}
	
	//
	
	private final Bus bus;
	
	private long counter = 1;
	
	private final HashMap<Long, IoHandler> handlers = new HashMap<>();
	
	public CallbackBus () throws Throwable
	{
		this.bus = new Bus();
	}
	
	public  void close () throws Exception
	{
		bus.close();
	}
	
	//
	
	public void interrupt () throws Throwable
	{
		bus.push(0, MemoryAddress.ofLong(0), 0);
	}
	
	public void register (IoDevice device, IoHandler handler) throws Throwable
	{
		final var key = counter++;		
		bus.register(key, device);
		handlers.put(key, handler);		
	}
	
	public boolean step (Duration timeLimit) throws Throwable
	{
		final var pulled = bus.pull(timeLimit);
		if (pulled.isEmpty()) {
			return false;
		}
		final var event = pulled.get();
		final var key = event.key();
		if (key == 0) {
			Thread.currentThread().interrupt();
			return false;
		}
		final var handler = handlers.get(key);
		if (handler == null) {
			// #TODO: what?
			return false;
		}
		handler.handle(event.operation().toRawLongValue(), event.status(), event.data());
		return true;
	}
}
