package br.dev.pedrolamarao.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import br.dev.pedrolamarao.io.Directory;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.callback.CallbackBus;
import br.dev.pedrolamarao.windows.Kernel32;
import jdk.incubator.foreign.NativeScope;
import lombok.Cleanup;

public final class CallbackBusTest
{
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void complete (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var bus = new CallbackBus();
		
		@Cleanup final var directory = new Directory(tmp);
		final var directoryMap = new HashMap<Long, CompletionHandler<Integer, Void>>();
		bus.register(directory, (operation, status, result) ->
		{
			final var handler = directoryMap.get(operation);
			if (handler == null) {
				// #TODO: what?
				return;
			}
			if (status) {
				handler.completed(result, null);
			}
			else {
				handler.failed(new RuntimeException("oops"), null);
			}
		});
		
		final var flag = new AtomicInteger();
		
		@Cleanup final var operation = new Operation();
		directoryMap.put(operation.handle().toRawLongValue(), new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable exc, Void attachment) { }
		});
		directory.watch(operation, buffer, false, Kernel32.FILE_NOTIFY_CHANGE_FILE_NAME);
		
		Files.createTempFile(tmp, null, null);
		
		bus.step(Duration.ofMillis(100));
		
		assertEquals(1 ,flag.get());
	}
}
