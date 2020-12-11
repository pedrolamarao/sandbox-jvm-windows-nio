package br.dev.pedrolamarao.io;

import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_POINTER;

import java.io.IOException;

import br.dev.pedrolamarao.windows.Kernel32;
import br.dev.pedrolamarao.windows.Mswsock;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;

public final class Link implements IoDevice
{
	private final MemoryAddress socket;
	
	// life-cycle
	
	public Link (int family, int style, int protocol) throws IOException
	{
		socket = downcall("<init>", () -> (MemoryAddress) Ws2_32.socket.invokeExact(family, style, protocol));
		if (Ws2_32.INVALID_SOCKET.equals(socket)) {
			final var error = downcall("<init>", () -> (int) Kernel32.getLastError.invokeExact());
			throw new RuntimeException("native error: " + Integer.toUnsignedString(error, 10));
		}
	}
	
	public void close () throws IOException
	{
		downcall("close", () -> (int) Ws2_32.closesocket.invokeExact(socket));
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
		final var result = downcall("bind", () -> (int) Ws2_32.bind.invokeExact(socket, address.address(), (int) address.byteSize()));
		if (result == -1) {
			final var error = downcall("bind", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
			throw new IOException("bind: system error: " + error);
		}
	}
	
	public void getsockopt (int level, int option, MemorySegment value) throws IOException
	{
		try (var nativeScope = NativeScope.boundedScope(C_INT.byteSize()))
		{
			final var length = nativeScope.allocate(C_INT, 0);
			final var result = downcall("getsockopt", () -> (int) Ws2_32.getsockopt.invokeExact(socket, Ws2_32.SOL_SOCKET, Ws2_32.SO_UPDATE_ACCEPT_CONTEXT, value.address(), length.address()));
			if (result == -1) {
				final var error = downcall("getsockopt", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
				throw new IOException("getsockopt: system error: " + error);
			}
		}
	}
	
	public void connect (Operation operation, MemorySegment address) throws IOException
	{
		try (var nativeScope = NativeScope.boundedScope(256))
		{
			final var guid = nativeScope.allocate(Kernel32.GUID.LAYOUT);
			Mswsock.WSAID_CONNECTEX.set(guid);
			final var value = nativeScope.allocate(C_POINTER, (long) 0);
			final var length = nativeScope.allocate(C_INT, (int) 0);
			
			final var r0 = 
				downcall(
					"connect", 
					() -> (int) Ws2_32.WSAIoctl.invokeExact(
						socket, Ws2_32.SIO_GET_EXTENSION_FUNCTION_POINTER, 
						guid.address(), (int) guid.byteSize(), 
						value.address(), (int) value.byteSize(), 
						length.address(), MemoryAddress.NULL, MemoryAddress.NULL
					)
				);
			if (r0 == -1) {
				final var error = downcall("connect", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
				throw new IOException("connect: system error: " + error);
			}
			
			final var ConnectEx = CLinker.getInstance().downcallHandle(MemoryAccess.getAddress(value), Mswsock.ConnectExType, Mswsock.ConnectExDescriptor);
			
			final var r1 = downcall("connect", () -> (int) ConnectEx.invokeExact(socket, address.address(), (int) address.byteSize(), MemoryAddress.NULL, 0, MemoryAddress.NULL, operation.handle()));
			if (r1 == -1) {
				final var error = downcall("connect", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
				throw new IOException("connect: system error: " + error);
			}			
		}
	}
	
	public void control (int control, MemorySegment in, MemorySegment out) throws IOException
	{
		try (var nativeScope = NativeScope.unboundedScope())
		{
			final var length = nativeScope.allocate(C_INT, 0);
			final var result = downcall("control", () -> (int) Ws2_32.WSAIoctl.invokeExact(socket, control, in.address(), (int) in.byteSize(), out.address(), (int) out.byteSize(), length.address(), MemoryAddress.NULL, MemoryAddress.NULL));
			if (result == -1) {
				final var error = downcall("control", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
				throw new IOException("control: system error: " + error);
			}		
		}
	}
	
	public void setsockopt (int level, int option, MemorySegment value) throws IOException
	{
		final var result = downcall("setsockopt", () -> (int) Ws2_32.setsockopt.invokeExact(socket, Ws2_32.SOL_SOCKET, Ws2_32.SO_UPDATE_ACCEPT_CONTEXT, value.address(), (int) value.byteSize()));
		if (result == -1) {
			final var error = downcall("setsockopt", () -> (int) Ws2_32.WSAGetLastError.invokeExact());
			throw new IOException("setsockopt: system error: " + error);
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
