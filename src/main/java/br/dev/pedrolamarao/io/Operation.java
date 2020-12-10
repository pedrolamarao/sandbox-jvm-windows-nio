package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.windows.Kernel32.ERROR_IO_INCOMPLETE;
import static br.dev.pedrolamarao.windows.Kernel32.WAIT_TIMEOUT;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.MemoryAccess.getInt;
import static jdk.incubator.foreign.MemorySegment.allocateNative;

import java.time.Duration;

import br.dev.pedrolamarao.windows.Kernel32;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class Operation implements AutoCloseable, Comparable<Operation>
{
	private final MemorySegment operation;
	
	public Operation ()
	{
		this.operation = MemorySegment.allocateNative(Kernel32.OVERLAPPED.LAYOUT).fill((byte) 0).share();
		// #TODO: leaks memory!
	}
	
	public void close ()
	{
		operation.close();
	}
	
	//
	
	@Override
	public int compareTo (Operation that)
	{
		final var x = this.operation.address().toRawLongValue();
		final var y = that.operation.address().toRawLongValue();
		if (x < y)
			return -1;
		else if (x > y)
			return 1;
		else
			return 0;
	}
	
	//
	
	public MemoryAddress handle ()
	{
		return operation.address();
	}
	
	long offset ()
	{
		return (long) Kernel32.OVERLAPPED.offset.get(operation);
	}
	
	void offset (long value)
	{
		Kernel32.OVERLAPPED.offset.set(operation, value);
	}
	
	//
	
	public void cancel (Device device) throws Throwable
	{
		final var result = (int) Kernel32.cancelIoEx.invokeExact(device.handle(), operation.address());
		if (result == 0) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("cancel: native error: " + error);
		}
	}
	
	public void clear ()
	{
		operation.fill((byte) 0);
	}
	
	public OperationState get (Device device, Duration timeLimit, boolean alertable) throws Throwable
	{
		try (final var dataRef = allocateNative(C_INT)) 
		{
			final var result = (int) Kernel32.getOverlappedResultEx.invokeExact(device.handle(), operation.address(), dataRef.address(), (int) timeLimit.toMillis(), (alertable ? 1 : 0));
			if (result != 0) {
				return new OperationState(true, 0, getInt(dataRef));
			}
			else {
				final var error = (int) Kernel32.getLastError.invokeExact();
				return switch (error) {
					case ERROR_IO_INCOMPLETE -> new OperationState(false, 0, 0);
					case WAIT_TIMEOUT -> new OperationState(false, 0, 0);
					default -> new OperationState(true, error, getInt(dataRef));
				};
			}
		}
	}
}
