package br.dev.pedrolamarao.io;

import static br.dev.pedrolamarao.windows.Kernel32.FILE_FLAG_BACKUP_SEMANTICS;
import static br.dev.pedrolamarao.windows.Kernel32.FILE_FLAG_OVERLAPPED;
import static br.dev.pedrolamarao.windows.Kernel32.FILE_SHARE_DELETE;
import static br.dev.pedrolamarao.windows.Kernel32.FILE_SHARE_READ;
import static br.dev.pedrolamarao.windows.Kernel32.FILE_SHARE_WRITE;
import static br.dev.pedrolamarao.windows.Kernel32.GENERIC_READ;
import static br.dev.pedrolamarao.windows.Kernel32.INVALID_HANDLE_VALUE;
import static br.dev.pedrolamarao.windows.Kernel32.OPEN_EXISTING;
import static br.dev.pedrolamarao.windows.Kernel32.createFileA;
import static br.dev.pedrolamarao.windows.Kernel32.getLastError;
import static br.dev.pedrolamarao.windows.Kernel32.readDirectoryChangesW;
import static jdk.incubator.foreign.MemoryAddress.NULL;

import java.nio.file.Path;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class Directory implements IoDevice
{
	private final MemoryAddress handle;

	public Directory (Path path) throws Throwable
	{
		try (var path_c = CLinker.toCString(path.toString())) {
			final var mode = GENERIC_READ;
			final var share = FILE_SHARE_DELETE | FILE_SHARE_READ | FILE_SHARE_WRITE;
			handle = (MemoryAddress) createFileA.invokeExact(path_c.address(), mode, share, NULL, OPEN_EXISTING, (FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OVERLAPPED), NULL);
		}
		if (handle.equals(INVALID_HANDLE_VALUE)) {
			final var error = (int) getLastError.invokeExact();
			throw new RuntimeException("open: native error: " + error);
		}
	}

	@Override
	public MemoryAddress handle ()
	{
		return handle;
	}
	
	public void watch (Operation operation, MemorySegment buffer, boolean recursive, int flags) throws Throwable
	{
		final var recursive_ = (recursive ? 1 : 0); 
		final var result = 
			(int) readDirectoryChangesW.invokeExact(
				handle,
				buffer.address(),
				(int) buffer.byteSize(),
				recursive_,
				flags,
				NULL,
				operation.handle(),
				NULL
			);
		// complete with success?
		if (result != 0)
			return;
		// incomplete?
		final var error = (int) getLastError.invokeExact();
		throw new RuntimeException("pull: native error: " + error); 
	}
}
