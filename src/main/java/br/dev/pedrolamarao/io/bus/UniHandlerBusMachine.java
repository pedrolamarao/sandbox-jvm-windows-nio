package br.dev.pedrolamarao.io.bus;

import java.time.Duration;
import java.util.Optional;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.BusEvent;
import jdk.incubator.foreign.MemoryAddress;

public final class UniHandlerBusMachine implements AutoCloseable
{
	// types
	
	@FunctionalInterface
	public interface Handler
	{
		void handle (long key, long operation, boolean success, int data) throws Throwable;
	}
	
	// attributes

	private final Bus bus;
	
	private final Handler handler;
	
	// life-cycle methods
	
	public UniHandlerBusMachine (Handler handler) throws Throwable
	{
		this.bus = new Bus();
		this.handler = handler;
	}
	
	public void close () throws Exception
	{
		bus.close();
	}
	
	// methods
	
	public void push (long key, MemoryAddress operation, int data) throws Throwable
	{
		bus.push(key, operation, data);
	}
	
	public void register (long key, IoDevice device) throws Throwable
	{
		bus.register(key, device);
	}
	
	public boolean step (Duration timeLimit) throws Throwable
	{
		final Optional<BusEvent> item = bus.pull(timeLimit);
		if (item.isEmpty()) {
			return false;
		}
		final BusEvent status = item.get();
		handler.handle(status.key(), status.operation().toRawLongValue(), status.status(), status.data());
		return true;
	}
}
