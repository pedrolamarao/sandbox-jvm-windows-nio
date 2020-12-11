package br.dev.pedrolamarao.io;

import java.io.IOException;

import br.dev.pedrolamarao.windows.Kernel32;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class Link implements IoDevice
{
	private final MemoryAddress socket;
	
	// life-cycle
	
	public Link (int family, int style, int protocol) throws IOException
	{
		socket = downcall("<init>", () -> (MemoryAddress) Ws2_32.socket.invokeExact(family, style, protocol));
		if (socket == MemoryAddress.ofLong(-1)) {
			final var error = downcall("<init>", () -> (int) Kernel32.getLastError.invokeExact());
			throw new RuntimeException("native error: " + Integer.toUnsignedString(error, 10));
		}
	}
	
	// properties
	
	@Override
	public MemoryAddress handle ()
	{
		return socket;
	}
	
	// methods
	
	public void bind (MemorySegment address) throws IOException
	{
		final int result = downcall("bind", () -> (int) Ws2_32.bind.invokeExact(socket, address.address(), (int) address.byteSize()));
		if (result == -1) {
			final var error = downcall("bind", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
			throw new IOException("listen: system error: " + error);
		}
	}
	
	public void setsockopt (int level, int option, MemorySegment value) throws IOException
	{
		final int result = downcall("finish", () -> (int) Ws2_32.setsockopt.invokeExact(socket, Ws2_32.SOL_SOCKET, Ws2_32.SO_UPDATE_ACCEPT_CONTEXT, value.address(), (int) value.byteSize()));
		if (result == -1) {
			final var error = downcall("finish", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
			throw new IOException("finish: system error: " + error);
		}
	}
	
	// utility
	
	@FunctionalInterface
	private interface Downcallable <T>
	{
		T call () throws Throwable;
	}
	
	private static <T> T downcall (String caller, Downcallable<T> callable) throws IOException
	{
		try
		{
			return callable.call();
		}
		catch (IOException e)
		{
			throw e;
		}
		catch (Throwable e)
		{
			throw new IOException(caller + ": downcall failed", e);
		}
	}
}
