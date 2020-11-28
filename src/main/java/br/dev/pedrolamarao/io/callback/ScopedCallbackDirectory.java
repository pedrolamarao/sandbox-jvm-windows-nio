package br.dev.pedrolamarao.io.callback;

import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import br.dev.pedrolamarao.io.Directory;
import br.dev.pedrolamarao.io.Operation;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;

public final class ScopedCallbackDirectory implements AutoCloseable
{
	public interface IoHandler
	{
		void handle (boolean status, int result) throws Throwable;
	}
	
	@SuppressWarnings("preview")
	public static final record IoState (Operation operation, IoHandler handler) { }

	private final Directory directory;
	
	private final NativeScope nativeScope = NativeScope.unboundedScope();
	
	private final ConcurrentHashMap<Long, IoState> pending = new ConcurrentHashMap<>();
	
	//
	
	public ScopedCallbackDirectory (Path path) throws Throwable
	{
		this.directory = new Directory(path);
	}
	
	public void close () throws Exception
	{
		directory.close();
		nativeScope.close();
	}
	
	//
	
	public Directory device ()
	{
		return directory;
	}

	//
	
	public void cancel () throws Throwable
	{
		directory.cancel();
	}
	
	public void cancel (Operation operation) throws Throwable
	{
		directory.cancel(operation);
	}
	
	public void complete (long handle, boolean status, int data) throws Throwable
	{
		final var state = pending.remove(handle);
		if (state == null) {
			// #TODO: what?
			return;
		}
		state.handler().handle(status, data);
	}
	
	public <Context> Operation watch (CallbackScope scope, MemorySegment buffer, boolean recursive, int flags, Context context, CompletionHandler<Integer, Context> handler) throws Throwable
	{
		final var operation = scope.register(directory);
		final var index = operation.handle().toRawLongValue();
		pending.put(index, new IoState(operation, (status, result) -> {
			scope.complete(index);
			if (status)
				handler.completed(result, context);
			else
				handler.failed(new RuntimeException("oops"), context);
		}));
		try {
			directory.watch(operation, buffer, recursive, flags);
		}
		catch (Throwable t) {
			pending.remove(index);
			scope.unregister(operation);
			throw t;
		}
		return operation;
	}
}
