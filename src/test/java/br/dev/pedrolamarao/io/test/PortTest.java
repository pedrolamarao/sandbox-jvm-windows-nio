package br.dev.pedrolamarao.io.test;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import br.dev.pedrolamarao.io.Link;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.Port;
import br.dev.pedrolamarao.java.foreign.windows.Ws2_32;
import jdk.incubator.foreign.MemorySegment;
import lombok.Cleanup;

public final class PortTest
{
	@Test
	public void listen () throws Throwable
	{
		@Cleanup final var address = MemorySegment.allocateNative(Ws2_32.sockaddr_in.LAYOUT).fill((byte) 0);
		Ws2_32.sockaddr_in.family.set(address, (short) Ws2_32.AF_INET);
		
		@Cleanup final var port = new Port(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP);
		port.bind(address);
		port.listen(Ws2_32.SOMAXCONN);
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void accept () throws Throwable
	{
		@Cleanup final var address = MemorySegment.allocateNative(Ws2_32.sockaddr_in.LAYOUT).fill((byte) 0);
		Ws2_32.sockaddr_in.family.set(address, (short) Ws2_32.AF_INET);
		
		@Cleanup final var port = new Port(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP);
		port.bind(address);
		port.listen(Ws2_32.SOMAXCONN);
		
		@Cleanup final var link = new Link(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP);
		
		@Cleanup final var operation = new Operation();
		@Cleanup final var buffer = MemorySegment.allocateNative(2048);
		port.accept(operation, buffer, link);
		port.query(operation);
		port.cancel(operation);
	}
}
