package br.dev.pedrolamarao.io;

import br.dev.pedrolamarao.windows.Kernel32;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemoryAddress;

public final class Link implements IoDevice
{
	private final int socket;	
	
	public Link (int family, int style, int protocol)
	{
		try
		{
			socket = (int) Ws2_32.socket.invokeExact(family, style, protocol);
			if (socket == -1) {
				final var error = (int) Kernel32.getLastError.invokeExact();
				throw new RuntimeException("native error: " + Integer.toUnsignedString(error, 10));
			}
		} 
		catch (Throwable e)
		{
			throw new RuntimeException("failed invoking method handle", e);
		}
	}
	
	@Override
	public MemoryAddress handle ()
	{
		return MemoryAddress.ofLong(socket);
	}
	
	public int socket ()
	{
		return socket;
	}
}
