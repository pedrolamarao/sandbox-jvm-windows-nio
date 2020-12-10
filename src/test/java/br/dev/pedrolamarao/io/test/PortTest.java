package br.dev.pedrolamarao.io.test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import br.dev.pedrolamarao.io.Link;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.Port;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemorySegment;
import lombok.Cleanup;

public final class PortTest
{
	@Test
	public void listen () throws Throwable
	{
		final var port0 = Port.open(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP, "localhost", "12345");
		port0.listen();
		port0.close();

		final var port1 = Port.open(Ws2_32.AF_INET, Ws2_32.IPPROTO_TCP, "localhost", "12345");
		port1.listen();
		port1.close();

		final var port2 = Port.open(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP, "localhost", 12345);
		port2.listen();
		port2.close();

		final var port3 = Port.open(Ws2_32.AF_INET, Ws2_32.IPPROTO_TCP, "localhost", 12345);
		port3.listen();
		port3.close();
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void accept () throws Throwable
	{
		@Cleanup final var port = Port.open(Ws2_32.AF_INET, Ws2_32.IPPROTO_TCP, "0.0.0.0", 12345);
		port.listen();
		
		@Cleanup final var link = new Link(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP);
		
		@Cleanup final var operation = new Operation();
		@Cleanup final var buffer = MemorySegment.allocateNative(2048);
		port.accept(operation, buffer, link);
		operation.get(port, Duration.ofMillis(100), false);
		operation.cancel(port);
	}
}
