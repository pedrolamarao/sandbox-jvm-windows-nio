package br.dev.pedrolamarao.io.callback;

import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import br.dev.pedrolamarao.io.Directory;
import br.dev.pedrolamarao.io.Operation;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;

public final class CallbackDirectory implements AutoCloseable
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
	
	public CallbackDirectory (Path path) throws Throwable
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
	
	public void complete (long operation, boolean status, int data) throws Throwable
	{
		final var state = pending.remove(operation);
		if (state == null) {
			// #TODO: what?
			return;
		}
		state.handler().handle(status, data);
	}
	
	public <Context> void watch (Operation operation, MemorySegment buffer, boolean recursive, int flags, Context context, CompletionHandler<Integer, Context> handler) throws Throwable
	{
		final var index = operation.handle().toRawLongValue();
		pending.put(index, new IoState(operation, (status, result) -> {
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
			throw t;
		}
	}
}
