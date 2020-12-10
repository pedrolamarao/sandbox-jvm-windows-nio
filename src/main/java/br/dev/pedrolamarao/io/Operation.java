package br.dev.pedrolamarao.io;

import br.dev.pedrolamarao.windows.Kernel32;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class Operation implements AutoCloseable
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
	
	public MemoryAddress handle ()
	{
		return operation.address();
	}
	
	public long offset ()
	{
		return (long) Kernel32.OVERLAPPED.offset.get(operation);
	}
	
	public void offset (long value)
	{
		Kernel32.OVERLAPPED.offset.set(operation, value);
	}
	
	public void clear ()
	{
		operation.fill((byte) 0);
	}
}
