package br.dev.pedrolamarao.java.nio.windows.test;

import static java.lang.String.format;
import static java.lang.System.err;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.LocalTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.dev.pedrolamarao.java.nio.windows.WindowsAsynchronousChannelProvider;


public final class WindowsAsynchronousChannelPressureTest
{
	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	private final WindowsAsynchronousChannelProvider provider = new WindowsAsynchronousChannelProvider();

	private AsynchronousChannelGroup group;
	
	@BeforeEach
	public void beforeEach () throws IOException
	{
		 group = provider.openAsynchronousChannelGroup(executor, 0);
	}
	
	@AfterEach
	public void afterEach () throws IOException
	{
		group.shutdownNow();
		executor.shutdownNow();
	}
	
	@Test
	public void accept__serial () throws Exception
	{
		final var target = 10000;

		final var accepted = new AtomicInteger(0);
		
		final var connected = new AsynchronousSocketChannel[target];
		
		for (int i = 0; i != target; ++i) {
			final var socket = AsynchronousSocketChannel.open();
			socket.bind(new InetSocketAddress(0));
			connected[i] = socket;
		}
		
		try (var port = AsynchronousServerSocketChannel.open(group))
		{
			port.bind(new InetSocketAddress("127.0.0.1", 12345));

			final CompletionHandler<AsynchronousSocketChannel, Void> handler = new CompletionHandler<AsynchronousSocketChannel, Void>()
			{
				@Override public void completed (AsynchronousSocketChannel socket, Void __)
				{
					port.accept(null, this);
					accepted.incrementAndGet();
					try { socket.close(); }
						catch (IOException e) { err.println(format("%s: test: failed closing accepted: %s", LocalTime.now(), e)); }
				}

				@Override public void failed (Throwable cause, Void __)
				{
					err.println(format("%s: test: failed accepting: %s", LocalTime.now(), cause));
				}
			};
			
			port.accept(null, handler);

			connect:
			for (int i = 0; i != target; ++i)
			{
				final var socket = connected[i];

				err.println(format("%s: test: connecting: %d", LocalTime.now(), i));
				final var pending = socket.connect(new InetSocketAddress("127.0.0.1", 12345));

				try
				{
					pending.get(100, TimeUnit.MILLISECONDS);
				}
				catch (Throwable cause)
				{
					err.println(format("%s: test: failed connecting: %d: %s", LocalTime.now(), i, cause));
					break connect;
				}				

				Thread.sleep(1); // #TODO: fails without this sleep
			}
		}
		
		for (int i = 0; i != target; ++i) {
			connected[i].close();
		}
		
		assertEquals(target, accepted.get());
	}
	
	@Test
	public void accept__parallel () throws Exception
	{
		final var target = 10000;

		final var accepted = new AtomicInteger();
		
		final var connected = new AsynchronousSocketChannel[target];
		
		for (int i = 0; i != target; ++i) {
			final var socket = AsynchronousSocketChannel.open();
			socket.bind(new InetSocketAddress(0));
			connected[i] = socket;
		}
		
		try (var port = AsynchronousServerSocketChannel.open(group))
		{
			port.bind(new InetSocketAddress("127.0.0.1", 12345));

			final CompletionHandler<AsynchronousSocketChannel, Void> acceptHandler = new CompletionHandler<AsynchronousSocketChannel, Void>()
			{
				@Override public void completed (AsynchronousSocketChannel socket, Void ignore)
				{
					port.accept(null, this);
					accepted.incrementAndGet();
					try { socket.close(); }
						catch (IOException e) { err.println(format("%s: test: failed closing accepted: %s", LocalTime.now(), e)); }
				}

				@Override public void failed (Throwable cause, Void ignore)
				{
					err.println(format("%s: test: accept failed: %s", LocalTime.now(), cause));
				}
			};
			
			port.accept(null, acceptHandler);

			final var pending = new Future[target];
			
			for (int i = 0; i != target; ++i)
			{
				err.println(format("%s: test: connecting: %d", LocalTime.now(), i));
				pending[i] = connected[i].connect(new InetSocketAddress("127.0.0.1", 12345));
				Thread.sleep(1); // #TODO: fails without this sleep
			}

			for (int i = 0; i != target; ++i)
			{
				final var socket = connected[i];
				
				try
				{
					pending[i].get(100, TimeUnit.MILLISECONDS);
				} 
				catch (InterruptedException | ExecutionException | TimeoutException e)
				{
					err.println(format("%s: test: connect failed: %d: %s", LocalTime.now(), i, e));
				}
				
				socket.close();
			}
		}
		
		assertEquals(target, accepted.get());
	}
}
