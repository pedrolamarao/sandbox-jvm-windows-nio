package br.dev.pedrolamarao.io.test;

import static br.dev.pedrolamarao.windows.Kernel32.FILE_NOTIFY_CHANGE_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.bus.BusMachine;
import br.dev.pedrolamarao.io.callback.CallbackDirectory;
import jdk.incubator.foreign.NativeScope;
import lombok.Cleanup;

public final class CallbackDirectoryTest
{
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void cancelDevice (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		
		@Cleanup final var bus = new BusMachine();
		
		@Cleanup final var directory = new CallbackDirectory(tmp);
		final var directoryKey = directory.device().handle().toRawLongValue(); 
		bus.register(directoryKey, directory.device());
		
		final var flag = new AtomicInteger();
		
		final var buffer = nativeScope.allocate(1024);
		@Cleanup final var operation = new Operation();
		directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable exc, Void attachment) { }
		});
		
		directory.cancel();
		
		Files.createTempFile(tmp, null, null);
		
		bus.step(Duration.ofMillis(100), (key, operation_, status, result) ->
		{
			if (key == directoryKey)
				directory.complete(operation_, status, result);
		});
		
		assertEquals(0 ,flag.get());
	}

	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void cancelOperation (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var bus = new BusMachine();
		
		@Cleanup final var directory = new CallbackDirectory(tmp);
		final var directoryKey = directory.device().handle().toRawLongValue(); 
		bus.register(directoryKey, directory.device());
		
		final var flag = new AtomicInteger();
		
		@Cleanup final var operation = new Operation();
		directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable cause, Void attachment) { }
		});
		
		directory.cancel(operation);
		
		Files.createTempFile(tmp, null, null);
		
		bus.step(Duration.ofMillis(100), (key, operation_, status, result) ->
		{
			if (key == directoryKey)
				directory.complete(operation_, status, result);
		});
		
		assertEquals(0 ,flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void complete (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var bus = new BusMachine();
		
		@Cleanup final var directory = new CallbackDirectory(tmp);
		final var directoryKey = directory.device().handle().toRawLongValue(); 
		bus.register(directoryKey, directory.device());
		
		final var flag = new AtomicInteger();
		
		@Cleanup final var operation = new Operation();
		directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable cause, Void attachment) { }
		});
		
		Files.createTempFile(tmp, null, null);
		
		bus.step(Duration.ofMillis(100), (key, operation_, status, result) ->
		{
			if (key == directoryKey)
				directory.complete(operation_, status, result);
		});
		
		assertEquals(1 ,flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void completeAfterCancel (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var bus = new BusMachine();
		
		@Cleanup final var directory = new CallbackDirectory(tmp);
		final var directoryKey = directory.device().handle().toRawLongValue(); 
		bus.register(directoryKey, directory.device());
		
		final var flag = new AtomicInteger();
		
		@Cleanup final var operation = new Operation();
		directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable cause, Void attachment) { }
		});
		
		directory.cancel(operation);
		
		Files.createTempFile(tmp, null, null);
		
		bus.step(Duration.ofMillis(100), (key, operation_, status, result) -> directory.complete(operation_, status, result));
		
		assertEquals(0 ,flag.get());

		operation.clear();
		directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
		{
			@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
			@Override public void failed (Throwable cause, Void attachment) { }
		});
		
		bus.step(Duration.ofMillis(100), (key, operation_, status, result) ->
		{
			if (key == directoryKey)
				directory.complete(operation_, status, result);
		});
		
		assertEquals(1 ,flag.get());
	}
}
