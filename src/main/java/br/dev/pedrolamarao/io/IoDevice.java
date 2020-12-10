package br.dev.pedrolamarao.io;

import java.time.Duration;
import java.util.Optional;

import br.dev.pedrolamarao.windows.Kernel32;
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
		operation.cancel(this);
	}
	
	default Optional<OperationStatus> get (Operation operation) throws Throwable
	{
		return operation.get(this, Duration.ZERO, false);
	}
}
