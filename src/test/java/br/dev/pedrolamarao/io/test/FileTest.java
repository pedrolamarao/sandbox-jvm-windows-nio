package br.dev.pedrolamarao.io.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.File;
import br.dev.pedrolamarao.io.Operation;
import jdk.incubator.foreign.NativeScope;
import lombok.Cleanup;

public class FileTest
{
	@TempDir Path tmp;
	
	@Test
	public void associate () throws Throwable
	{
		final var path = Files.createTempFile(tmp, "FileTest", "associate");
		try (var bus = new Bus();) 
		{ 
			try (var file = File.open(path)) { bus.register(0, file); }
		}
	}

	@Test
	public void openClose () throws Throwable
	{
		final var path = Files.createTempFile(tmp, "FileTest", "openClose");
		try (var file = File.open(path)) 
		{ }
	}
	
	@Test
	public void lock () throws Throwable
	{
		final var path = Files.createTempFile(tmp, "FileTest", "pull");
		
		Files.writeString(path, "FOOBAR", StandardOpenOption.WRITE);
		
		try (var bus = new Bus();
			 var file = File.open(path);
			 var scope = NativeScope.unboundedScope())
		{ 
			bus.register(0, file);
			
			@Cleanup final var operation = new Operation();
			file.lock(0, 1, 0, operation);
			
			final var queuedStatus = bus.pull(Duration.ZERO);
			assertTrue(queuedStatus.isPresent());
			assertTrue(queuedStatus.get().status());
			// assertEquals(operation, queuedStatus.get().operation());
			assertEquals(0, queuedStatus.get().data());
			
			final var operationStatus = file.get(operation);
			assertTrue(operationStatus.isPresent());
			assertEquals(0, operationStatus.get().status());
			assertEquals(0, operationStatus.get().data());
		}
	}
	
	@Test
	public void pull () throws Throwable
	{
		final var path = Files.createTempFile(tmp, "FileTest", "pull");
		
		Files.writeString(path, "FOOBAR", StandardOpenOption.WRITE);
		final var size = Files.size(path);
		
		try (var bus = new Bus();
			 var file = File.open(path);
			 var scope = NativeScope.unboundedScope())
		{ 
			bus.register(0, file);
			
			final var buffer = ByteBuffer.allocateDirect(1024);
			@Cleanup final var operation = new Operation();
			file.pull(buffer, operation);
			
			final var queuedStatus = bus.pull(Duration.ZERO);
			assertTrue(queuedStatus.isPresent());
			assertTrue(queuedStatus.get().status());
			// assertEquals(operation, queuedStatus.get().operation());
			assertEquals(size, queuedStatus.get().data());
			
			final var operationStatus = file.get(operation);
			assertTrue(operationStatus.isPresent());
			assertEquals(0, operationStatus.get().status());
			assertEquals(size, operationStatus.get().data());
		}
	}
	
	@Test
	public void pullAt () throws Throwable
	{
		final var path = Files.createTempFile(tmp, "FileTest", "pull");
		
		Files.writeString(path, "FOOBAR", StandardOpenOption.WRITE);
		final var size = Files.size(path);
		
		try (var bus = new Bus();
			 var file = File.open(path);
			 var scope = NativeScope.unboundedScope())
		{ 
			bus.register(0, file);
			
			final var buffer = ByteBuffer.allocateDirect(1024);
			@Cleanup final var operation = new Operation();
			file.pullAt(1, buffer, operation);
			
			final var queuedStatus = bus.pull(Duration.ZERO);
			assertTrue(queuedStatus.isPresent());
			assertTrue(queuedStatus.get().status());
			// assertEquals(operation, queuedStatus.get().operation());
			assertEquals((size - 1), queuedStatus.get().data());
			
			final var operationStatus = file.get(operation);
			assertTrue(operationStatus.isPresent());
			assertEquals(0, operationStatus.get().status());
			assertEquals((size - 1), operationStatus.get().data());
		}
	}
	
	@Test
	public void unknownFile () throws Throwable
	{
		assertThrows(
			RuntimeException.class,
			() -> {
				try (var file = File.open(tmp.resolve("unknownFile"))) 
				{ }
			}
		);
	}
}
