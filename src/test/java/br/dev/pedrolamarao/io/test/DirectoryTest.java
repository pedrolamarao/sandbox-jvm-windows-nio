package br.dev.pedrolamarao.io.test;

import static br.dev.pedrolamarao.windows.Kernel32.FILE_NOTIFY_CHANGE_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.dev.pedrolamarao.io.Bus;
import br.dev.pedrolamarao.io.Directory;
import br.dev.pedrolamarao.io.Operation;
import jdk.incubator.foreign.NativeScope;
import lombok.Cleanup;

public final class DirectoryTest
{
	@Test
	public void smoke (@TempDir Path tmp) throws Throwable
	{
		try (var scope = NativeScope.unboundedScope();
			 var bus = new Bus())
		{
			@Cleanup final var directory = new Directory(tmp);
			bus.register(1, directory);
			
			final var buffer = scope.allocate(4096);
			@Cleanup final var operation = new Operation();
			directory.watch(operation, buffer, false, FILE_NOTIFY_CHANGE_FILE_NAME);
			
			Files.createTempFile(tmp, null, null);
			
			final var status = bus.pull(Duration.ofMillis(1000));
			assertTrue(status.isPresent());
			assertTrue(status.get().status());
			assertEquals(1, status.get().key());
			assertEquals(operation.handle(), status.get().operation());
		}
	}
}
