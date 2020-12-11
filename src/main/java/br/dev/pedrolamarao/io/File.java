package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.FILE_FLAG_OVERLAPPED;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.FILE_SHARE_READ;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.GENERIC_READ;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.INVALID_HANDLE_VALUE;
import static br.dev.pedrolamarao.java.foreign.windows.Kernel32.OPEN_EXISTING;
import static jdk.incubator.foreign.MemoryAddress.NULL;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import br.dev.pedrolamarao.java.foreign.windows.Kernel32;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class File implements IoDevice
{	
	private final MemoryAddress handle;
	
	private File (MemoryAddress handle)
	{
		this.handle = handle;
	}
	
	public static File open (Path path) throws Throwable
	{
		final MemoryAddress handle;
		try (var path_c = CLinker.toCString(path.toString())) {
			handle = (MemoryAddress) Kernel32.createFileA.invokeExact(path_c.address(), GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, NULL);
		}
		if (handle.equals(INVALID_HANDLE_VALUE)) {
			final var error = (int) Kernel32.getLastError.invokeExact();
			throw new RuntimeException("open: native error: " + error);
		}
		return new File(handle);
	}
	
	public MemoryAddress handle ()
	{
		return handle;
	}
	
	public boolean lock (long position, long length, int flags, Operation operation) throws Throwable
	{
		final var lengthBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.nativeOrder());
		lengthBuffer.putLong(length).flip();
		operation.offset(position);
		final var result = (int) Kernel32.lockFileEx.invokeExact(handle, flags, 0, lengthBuffer.getInt(0), lengthBuffer.getInt(1), operation.handle());
		// complete with success?
		if (result != 0)
			return true;
		// incomplete?
		final var error = (int) Kernel32.getLastError.invokeExact();
		return switch (error) {
		case Kernel32.ERROR_IO_PENDING -> false;
		case Kernel32.ERROR_IO_INCOMPLETE -> false;
		default -> throw new RuntimeException("pull: native error: " + error); 
		};
	}
	
	public boolean pull (ByteBuffer buffer, Operation operation) throws Throwable
	{
		final var segment = MemorySegment.ofByteBuffer(buffer);
		final var result = (int) Kernel32.readFile.invokeExact(handle, segment.address(), (int) segment.byteSize(), MemoryAddress.NULL, operation.handle());
		// complete with success?
		if (result != 0)
			return true;
		// incomplete?
		final var error = (int) Kernel32.getLastError.invokeExact();
		return switch (error) {
		case Kernel32.ERROR_IO_PENDING -> false;
		case Kernel32.ERROR_IO_INCOMPLETE -> false;
		default -> throw new RuntimeException("pull: native error: " + error); 
		};
	}
	
	public boolean pullAt (long position, ByteBuffer buffer, Operation operation) throws Throwable
	{
		final var segment = MemorySegment.ofByteBuffer(buffer);
		operation.offset(position);
		final var result = (int) Kernel32.readFile.invokeExact(handle, segment.address(), (int) segment.byteSize(), MemoryAddress.NULL, operation.handle());
		// complete with success?
		if (result != 0)
			return true;
		// incomplete?
		final var error = (int) Kernel32.getLastError.invokeExact();
		return switch (error) {
		case Kernel32.ERROR_IO_PENDING -> false;
		case Kernel32.ERROR_IO_INCOMPLETE -> false;
		default -> throw new RuntimeException("pull: native error: " + error); 
		};
	}
}
