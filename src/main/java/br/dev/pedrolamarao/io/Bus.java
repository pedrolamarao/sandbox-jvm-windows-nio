package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.INVALID_HANDLE_VALUE;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.WAIT_TIMEOUT;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static jdk.incubator.foreign.MemoryAccess.getAddress;
import static jdk.incubator.foreign.MemoryAccess.getInt;
import static jdk.incubator.foreign.MemoryAccess.getLong;
import static jdk.incubator.foreign.MemoryAddress.NULL;

import java.time.Duration;
import java.util.Optional;

import br.dev.pedrolamarao.java.foreign.windows.Kernel32;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.NativeScope;

public final class Bus implements Device
{	
	private final MemoryAddress handle;
	
	public Bus () throws Throwable
	{
		handle = (MemoryAddress) Kernel32.createIoCompletionPort.invokeExact(INVALID_HANDLE_VALUE, NULL, NULL, 0);
		if (handle.equals(NULL)) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("open: native error: " + error);
		}
	}
	
	public Bus (int concurrency) throws Throwable
	{
		handle = (MemoryAddress) Kernel32.createIoCompletionPort.invokeExact(INVALID_HANDLE_VALUE, NULL, NULL, concurrency);
		if (handle.equals(NULL)) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("open: native error: " + error);
		}
	}
	
	//
	
	public MemoryAddress handle ()
	{
		return handle;
	}
	
	//
	
	public void register (long key, IoDevice device) throws Throwable
	{
		final var result = (MemoryAddress) Kernel32.createIoCompletionPort.invokeExact(device.handle(), handle, MemoryAddress.ofLong(key), 0);
		if (result.equals(NULL)) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("associate: native error: " + error);
		}
	}
	
	public Optional<BusEvent> pull (Duration timeLimit) throws Throwable
	{
		try (var scope = NativeScope.boundedScope(24)) 
		{
			// prepare arguments
			final var dataRef = scope.allocate(C_INT, 0);
			final var keyRef = scope.allocate(C_POINTER, NULL);
			final var operationRef = scope.allocate(C_POINTER, NULL);
			// down call
			final var result = (int) Kernel32.getQueuedCompletionStatus.invokeExact(handle, dataRef.address(), keyRef.address(), operationRef.address(), (int) timeLimit.toMillis());
			// success?
			if (result != 0)
				return Optional.of(new BusEvent(getLong(keyRef), getAddress(operationRef), true, getInt(dataRef)));
			// operation failure?
			final var operation = MemoryAccess.getAddress(operationRef); 
			if (! operation.equals(NULL))
				return Optional.of(new BusEvent(getLong(keyRef), getAddress(operationRef), false, getInt(dataRef)));
			// time limit exceeded?
			final var error = (int) Kernel32.getLastError.invokeExact();
			if (error == WAIT_TIMEOUT)
				return Optional.empty();
			// bus failure
			throw new RuntimeException("pull: native error: " + error);
		}
	}
	
	public void push (long key, MemoryAddress operation, int data) throws Throwable
	{
		final var result = (int) Kernel32.postQueuedCompletionStatus.invokeExact(handle, data, MemoryAddress.ofLong(key), operation);
		if (result == 0) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("push: native error: " + error);
		}
	}
}
