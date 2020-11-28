package br.dev.pedrolamarao.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import br.dev.pedrolamarao.io.Bus;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.NativeScope;

public class BusTest
{
	@Test
	public void smoke () throws Exception, Throwable
	{
		try (var bus = new Bus()) { }
		try (var bus = new Bus(0)) { }
	}

	@Test
	public void pushPull () throws Exception, Throwable
	{
		try (var bus = new Bus()) 
		{
			bus.push(1, MemoryAddress.ofLong(2), 3);
			final var pull = bus.pull(Duration.ZERO);
			assertTrue(pull.isPresent());
			assertEquals(1, pull.get().key());
			assertEquals(MemoryAddress.ofLong(2), pull.get().operation());
			assertEquals(3, pull.get().data());
		}
	}

	@Test
	public void timeLimit () throws Exception, Throwable
	{
		try (var bus = new Bus(); 
			 var scope = NativeScope.unboundedScope()) 
		{
			final var pull = bus.pull(Duration.ZERO);
			assertTrue(pull.isEmpty());
		}
	}
}
