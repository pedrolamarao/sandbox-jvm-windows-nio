package br.dev.pedrolamarao.io;

import java.io.IOException;

import br.dev.pedrolamarao.windows.Kernel32;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemoryAddress;

public final class Link implements IoDevice
{
	private final int socket;
	
	// life-cycle
	
	public Link (int family, int style, int protocol) throws IOException
	{
		socket = downcall("<init>", () -> (int) Ws2_32.socket.invokeExact(family, style, protocol));
		if (socket == -1) {
			final var error = downcall("<init>", () -> (int) Kernel32.getLastError.invokeExact());
			throw new RuntimeException("native error: " + Integer.toUnsignedString(error, 10));
		}
	}
	
	@Override
	public MemoryAddress handle ()
	{
		return MemoryAddress.ofLong(socket);
	}
	
	// properties
	
	public int socket ()
	{
		return socket;
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
