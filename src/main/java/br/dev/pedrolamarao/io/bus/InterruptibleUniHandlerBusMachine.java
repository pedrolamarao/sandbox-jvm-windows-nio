package br.dev.pedrolamarao.io.bus;

import java.time.Duration;
import java.util.Optional;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.BusEvent;
import jdk.incubator.foreign.MemoryAddress;

public final class InterruptibleUniHandlerBusMachine implements AutoCloseable
{
	// types
	
	@FunctionalInterface
	public interface Handler
	{
		void handle (long key, MemoryAddress operation, boolean success, int data);
	}
	
	// attributes

	private final Bus bus;
	
	private final Handler handler;
	
	// life-cycle methods
	
	public InterruptibleUniHandlerBusMachine (Handler handler) throws Throwable
	{
		this.bus = new Bus();
		this.handler = handler;
	}
	
	public void close () throws Exception
	{
		bus.close();
	}
	
	// methods
	
	public void interrupt () throws Throwable
	{
		bus.push(0, MemoryAddress.ofLong(0), 0);
	}
	
	public void push (long key, MemoryAddress operation, int data) throws Throwable
	{
		if (key == 0)
			throw new IllegalArgumentException("invalid key: zero is not allowed");
		bus.push(key, operation, data);
	}
	
	public void register (long key, IoDevice device) throws Throwable
	{
		if (key == 0)
			throw new IllegalArgumentException("invalid key: zero is not allowed");
		bus.register(key, device);
	}
	
	public boolean step (Duration timeLimit) throws Throwable
	{
		final Optional<BusEvent> item = bus.pull(timeLimit);
		if (item.isEmpty()) {
			return false;
		}
		final BusEvent status = item.get();
		if (status.key() == 0) {
			Thread.currentThread().interrupt();
		}
		handler.handle(status.key(), status.operation(), status.status(), status.data());
		return true;
	}
}
