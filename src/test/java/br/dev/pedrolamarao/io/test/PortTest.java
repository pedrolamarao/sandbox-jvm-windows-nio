package br.dev.pedrolamarao.io.test;

import org.junit.jupiter.api.Test;

import br.dev.pedrolamarao.io.Port;
import br.dev.pedrolamarao.windows.Ws2_32;
import lombok.Cleanup;

public final class PortTest
{
	@Test
	public void smoke () throws Throwable
	{
		@Cleanup final var port = Port.open(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP, "localhost", "12345");
		port.listen();
	}
}
