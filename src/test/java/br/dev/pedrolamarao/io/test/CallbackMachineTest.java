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

import br.dev.pedrolamarao.io.callback.CallbackMachine;
import br.dev.pedrolamarao.io.callback.CallbackScope;
import br.dev.pedrolamarao.io.callback.ScopedCallbackDirectory;
import jdk.incubator.foreign.NativeScope;
import lombok.Cleanup;

public final class CallbackMachineTest
{
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void cancelDevice (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var machine = new CallbackMachine();
		
		@Cleanup final var directory = new ScopedCallbackDirectory(tmp);
		machine.register(directory.device(), directory::complete);
		
		final var flag = new AtomicInteger();

		try (var scope = machine.scope())
		{
			final var operation = directory.watch(scope, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
			{
				@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
				@Override public void failed (Throwable exc, Void attachment) { }
			});
			
			directory.cancel();
			
			Files.createTempFile(tmp, null, null);
		}
		
		assertEquals(0, flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void cancelOperation (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var machine = new CallbackMachine();
		
		@Cleanup final var directory = new ScopedCallbackDirectory(tmp);
		machine.register(directory.device(), directory::complete);
		
		final var flag = new AtomicInteger();

		try (var scope = machine.scope())
		{
			final var operation = directory.watch(scope, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
			{
				@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
				@Override public void failed (Throwable exc, Void attachment) { }
			});
			
			directory.cancel(operation);
			
			Files.createTempFile(tmp, null, null);
		}
		
		assertEquals(0, flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void cancelScope (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var machine = new CallbackMachine();
		
		@Cleanup final var directory = new ScopedCallbackDirectory(tmp);
		machine.register(directory.device(), directory::complete);
		
		final var flag = new AtomicInteger();

		try (var scope = machine.scope())
		{
			directory.watch(scope, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
			{
				@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
				@Override public void failed (Throwable exc, Void attachment) { }
			});
			
			scope.cancel();
			
			Files.createTempFile(tmp, null, null);
		}
		
		assertEquals(0, flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void complete (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var machine = new CallbackMachine();
		
		@Cleanup final var directory = new ScopedCallbackDirectory(tmp);
		machine.register(directory.device(), directory::complete);
		
		final var flag = new AtomicInteger();
		
		try (var scope = machine.scope())
		{
			directory.watch(scope, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
			{
				@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
				@Override public void failed (Throwable exc, Void attachment) { }
			});
			
			Files.createTempFile(tmp, null, null);
		}
		
		assertEquals(1, flag.get());
	}
	
	@Test
	@Timeout(value=1000, unit=TimeUnit.MILLISECONDS)
	public void expire (@TempDir Path tmp) throws Throwable
	{
		@Cleanup final var nativeScope = NativeScope.unboundedScope();
		final var buffer = nativeScope.allocate(1024);
		
		@Cleanup final var machine = new CallbackMachine();
		
		@Cleanup final var directory = new ScopedCallbackDirectory(tmp);
		machine.register(directory.device(), directory::complete);
		
		final var flag = new AtomicInteger();
		
		try (var scope = machine.scope(Duration.ofMillis(500)))
		{
			directory.watch(scope, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME, null, new CompletionHandler<Integer, Void>() 
			{
				@Override public void completed (Integer result, Void attachment) { flag.incrementAndGet(); }
				@Override public void failed (Throwable exc, Void attachment) { }
			});
		}
		
		assertEquals(0, flag.get());
	}
}
