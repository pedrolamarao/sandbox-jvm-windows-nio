package br.dev.pedrolamarao.io.bus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.BusEvent;
import jdk.incubator.foreign.MemoryAddress;

public final class InterruptibleAutoKeyMultiHandlerBusMachine implements AutoCloseable
{
	// types
	
	@FunctionalInterface
	public interface Handler
	{
		void handle (long operation, boolean status, int data) throws Throwable;
	}
	
	// attributes

	private final Bus bus;
	
	private long counter = 1;
	
	private final HashMap<Long, Handler> map = new HashMap<>();
	
	// life-cycle methods
	
	public InterruptibleAutoKeyMultiHandlerBusMachine () throws Throwable
	{
		this.bus = new Bus();
	}
	
	public void close () throws Exception
	{
		bus.close();
	}
	
	// methods
	
	public long define (Handler handler)
	{
		final var key = counter++;
		map.put(key, handler);
		return key;
	}

	public void interrupt () throws Throwable
	{
		bus.push(0, MemoryAddress.ofLong(0), 0);
	}
	
	public void push (long key, MemoryAddress operation, int data) throws Throwable
	{
		if (key == 0)
			throw new IllegalArgumentException("illegal key: must not be zero");
		bus.push(key, operation, data);
	}
	
	public void register (long key, IoDevice device) throws Throwable
	{
		if (key == 0)
			throw new IllegalArgumentException("illegal key: must not be zero");
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
		final Handler handler = map.get(status.key());
		if (handler == null) {
			// #TODO: warn
			return false;
		}
		handler.handle(status.operation().toRawLongValue(), status.status(), status.data());
		return true;
	}
}
