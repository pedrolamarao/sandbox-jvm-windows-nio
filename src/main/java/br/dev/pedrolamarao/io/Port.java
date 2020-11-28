package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.windows.Ws2_32.AI_PASSIVE;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static jdk.incubator.foreign.CLinker.toCString;

import br.dev.pedrolamarao.windows.Kernel32;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;

public final class Port implements IoDevice
{
	private final int handle;
	
	// life-cycle methods
	
	private Port (int handle)
	{
		this.handle = handle;
	}
	
	public static Port open (int family, int style, int protocol, String host, String service) throws Throwable
	{
		final MemorySegment address;
		
		try (var scope = NativeScope.unboundedScope())
		{
			final var hostC = toCString(host, scope);
			final var serviceC = toCString(service, scope);
			final var hint = scope.allocate(Ws2_32.addrinfo.LAYOUT).fill((byte) 0);
			Ws2_32.addrinfo.flags.set(hint, AI_PASSIVE);
			Ws2_32.addrinfo.family.set(hint, family);
			Ws2_32.addrinfo.socktype.set(hint, style);
			Ws2_32.addrinfo.protocol.set(hint, protocol);
			final var addressRef = scope.allocate(C_POINTER, (long) 0);
			final var r0 = (int) Ws2_32.getaddrinfo.invokeExact(hostC.address(), serviceC.address(), hint.address(), addressRef.address());
			if (r0 != 0) {
				final var error = (int) Kernel32.getLastError.invokeExact();
				throw new RuntimeException("open: getaddrinfo: native error: " + error);
			}
			
			address = MemoryAccess.getAddress(addressRef).asSegmentRestricted(Ws2_32.addrinfo.LAYOUT.byteSize());
		}

		final int handle;
		
		try
		{
			handle = (int) Ws2_32.socket.invokeExact(family, style, protocol);
			if (handle == -1) {
				final var error = (int) Kernel32.getLastError.invokeExact();
				throw new RuntimeException("open: socket: native error: " + error);
			}
			
			final var r1 = (int) Ws2_32.bind.invokeExact(handle, MemoryAddress.ofLong((long) Ws2_32.addrinfo.addr.get(address)), (int) ((long) Ws2_32.addrinfo.addrlen.get(address)));
			if (r1 != 0) {
				final var error = (int) Kernel32.getLastError.invokeExact();
				@SuppressWarnings("unused") final var r2 = (int) Ws2_32.closesocket.invokeExact(handle);
				throw new RuntimeException("open: bind: native error: " + error);
			}
		}
		finally
		{
			Ws2_32.freeaddrinfo.invokeExact(address.address());
		}
		
		return new Port(handle);
	}
	
	public void close () throws Exception
	{
		try
		{
			@SuppressWarnings("unused")
			final var ignore = (int) Ws2_32.closesocket.invokeExact(handle);
		} 
		catch (Throwable e)
		{
			throw new Exception("close: foreign linker failure", e);
		}
	}
	
	// properties

	@Override
	public MemoryAddress handle ()
	{
		return MemoryAddress.ofLong(handle);
	}
	
	// methods
	
	public void listen () throws Throwable
	{
		final int result = (int) Ws2_32.listen.invokeExact(handle, 0);
		if (result == -1) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("listen: native error: " + error);
		}
	}
}
