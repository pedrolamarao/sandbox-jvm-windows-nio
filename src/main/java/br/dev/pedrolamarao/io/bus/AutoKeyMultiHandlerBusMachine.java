package br.dev.pedrolamarao.io.bus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.BusEvent;
import jdk.incubator.foreign.MemoryAddress;

public final class AutoKeyMultiHandlerBusMachine implements AutoCloseable
{
	// types
	
	@FunctionalInterface
	public interface Handler
	{
		void handle (MemoryAddress operation, boolean success, int data) throws Throwable;
	}
	
	// attributes

	private final Bus bus;
	
	private long key = 1;
	
	private final HashMap<Long, Handler> map = new HashMap<>();
	
	// life-cycle methods
	
	public AutoKeyMultiHandlerBusMachine () throws Throwable
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
		final var k = key++;
		map.put(k, handler);
		return k;
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
		final Handler handler = map.get(status.key());
		if (handler == null) {
			// #TODO: warn
			return false;
		}
		handler.handle(status.operation(), status.status(), status.data());
		return true;
	}
}
