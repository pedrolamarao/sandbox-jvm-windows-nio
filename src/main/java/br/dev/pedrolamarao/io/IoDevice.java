package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.ERROR_IO_INCOMPLETE;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.WAIT_TIMEOUT;
import static br.dev.pedrolamarao.java.foreign.windows.Ws2_32.WSA_IO_PENDING;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.MemoryAccess.getInt;
import static jdk.incubator.foreign.MemorySegment.allocateNative;

import br.dev.pedrolamarao.java.foreign.windows.Kernel32;
import br.dev.pedrolamarao.java.foreign.windows.Ws2_32;
import jdk.incubator.foreign.MemoryAddress;

@SuppressWarnings("preview")
public sealed interface IoDevice extends Device
	permits Directory, File, Link, Port
{
	default void cancel () throws Throwable
	{
		final var result = (int) Kernel32.cancelIoEx.invokeExact(handle(), MemoryAddress.NULL);
		if (result == 0) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("cancel: native error: " + error);
		}
	}
	
	default void cancel (Operation operation) throws Throwable
	{

		final var result = (int) Kernel32.cancelIoEx.invokeExact(handle(), operation.handle());
		if (result == 0) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("cancel: native error: " + error);
		}	
	}
	
	default OperationState query (Operation operation) throws Throwable
	{
		try (var dataRef = allocateNative(C_INT).fill((byte) 0);
			 var flagsRef = allocateNative(C_INT).fill((byte) 0)) 
		{
			final int result;
			
			if (this instanceof Link || this instanceof Port) {
				result = (int) Ws2_32.WSAGetOverlappedResult.invokeExact((int) handle().toRawLongValue(), operation.handle(), dataRef.address(), (int) 0, flagsRef.address());
			}		
			else {
				result = (int) Kernel32.getOverlappedResultEx.invokeExact(handle(), operation.handle(), dataRef.address(), (int) 0, (int) 0);
			}
			
			if (result != 0) {
				return new OperationState(true, 0, getInt(dataRef), getInt(flagsRef));
			}
			
			final var error = (int) Kernel32.getLastError.invokeExact();

			return switch (error) 
			{
				case ERROR_IO_INCOMPLETE -> new OperationState(false, 0, 0, 0);
				case WAIT_TIMEOUT -> new OperationState(false, 0, 0, 0);
				case WSA_IO_PENDING -> new OperationState(false, 0, 0, 0);
				default -> new OperationState(true, error, 0, 0);
			};
		}
	}
}
