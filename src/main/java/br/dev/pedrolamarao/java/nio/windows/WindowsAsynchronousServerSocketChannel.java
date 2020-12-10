package br.dev.pedrolamarao.java.nio.windows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.time.Duration;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import br.dev.pedrolamarao.io.IoDevice;
import br.dev.pedrolamarao.io.Link;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.OperationState;
import br.dev.pedrolamarao.io.Port;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemorySegment;

public final class WindowsAsynchronousServerSocketChannel extends AsynchronousServerSocketChannel implements WindowsChannel
{
	@SuppressWarnings("preview")
	public final record AcceptState (Operation operation, MemorySegment buffer, Link link, Object context, CompletionHandler<AsynchronousSocketChannel, Object> handler)
	{
		public void complete (AsynchronousSocketChannel channel)
		{
			handler().completed(channel, context());			
		}
		
		public void fail (Throwable cause)
		{
			handler().failed(cause, context());
		}
	}
	
	private final WindowsAsynchronousChannelGroup group;
	
	private long key = 0;
	
	private final HashMap<Long, AcceptState> operations = new HashMap<>();
	
	private Port port = null;
	
	public WindowsAsynchronousServerSocketChannel (WindowsAsynchronousChannelProvider provider, WindowsAsynchronousChannelGroup group)
	{
		super(provider);
		this.group = group;
	}

	@Override
	public void close () throws IOException
	{
		if (port != null) {
			port.close();
			group.unregister(key);
		}
		
		operations.forEach((key, state) -> state.operation().close());
	}
	
	//
	
	public IoDevice getDevice ()
	{
		return port;
	}

	@Override
	public boolean isOpen ()
	{
		return (port != null);
	}
	
	//

	@Override
	public <T> T getOption (SocketOption<T> name) throws IOException
	{
		if (! isOpen()) {
			throw new ClosedChannelException();
		}
		
		throw new IOException("oops");
	}

	@Override
	public Set<SocketOption<?>> supportedOptions ()
	{
		throw new RuntimeException("oops");
	}

	private int socketFamily;
	
	private int socketProtocol;
	
	@SuppressWarnings("preview")
	@Override
	public AsynchronousServerSocketChannel bind (SocketAddress local, int backlog) throws IOException
	{
		if (isOpen()) {
			throw new IOException("illegal state: channel already open");
		}
		
		if (local instanceof InetSocketAddress address)
		{
			socketFamily = Ws2_32.AF_INET;
			socketProtocol = Ws2_32.IPPROTO_TCP;
			
			try
			{
				port = Port.open(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP, address.getAddress().getHostName(), address.getPort());
				key = group.register(port, this::complete);
				port.listen();
				return this;
			} 
			catch (Throwable e)
			{
				throw new IOException("bind: failed", e);
			}
		}
		else
		{
			throw new IOException("unexpected SocketAddress type: " + local.getClass());
		}
	}

	@Override
	public <T> AsynchronousServerSocketChannel setOption (SocketOption<T> name, T value) throws IOException
	{
		if (! isOpen()) {
			throw new ClosedChannelException();
		}
		
		throw new IOException("oops");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <A> void accept (A attachment, CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
	{
		if (! isOpen()) {
			throw new RuntimeException("unexpected state: not bound");
		}
		
		final var operation = new Operation();
		final var buffer = MemorySegment.allocateNative(2048);
		final var link = new Link(socketFamily, Ws2_32.SOCK_STREAM, socketProtocol); 
		
		try
		{
			port.accept(operation, buffer, link);
		} 
		catch (Throwable e)
		{
			throw new RuntimeException("accept: failed", e);
		}
		
		operations.put(operation.handle().toRawLongValue(), new AcceptState(operation, buffer, link, attachment, (CompletionHandler<AsynchronousSocketChannel, Object>) handler));
	}

	@Override
	public Future<AsynchronousSocketChannel> accept ()
	{
		final var future = new CompletableFuture<AsynchronousSocketChannel>();
		accept(future, new CompletableFutureHandler<AsynchronousSocketChannel>());
		return future;
	}

	@Override
	public SocketAddress getLocalAddress () throws IOException
	{
		if (! isOpen()) {
			throw new ClosedChannelException();
		}
		
		throw new IOException("oops");
	}
	
	public void complete (long operation, boolean status, int ignore)
	{
		final var state = operations.remove(operation);
		if (state == null) {
			// what!?
			return;
		}

		final OperationState systemState;
		
		try 
		{ 
			systemState = state.operation().get(port, Duration.ZERO, false);
		}
	    catch (Throwable cause) 
		{
	    	// #TODO: record this event
	    	state.handler().failed(cause, state.context());
	    	state.operation().close();
	    	return;
	    }
	    
		if (! systemState.complete()) {
			// what!?
			return;
		}
		
		final var systemResult = systemState.result();

		try
		{
			if (systemResult == 0)
			{
				final var channel = new WindowsAsynchronousSocketChannel((WindowsAsynchronousChannelProvider) this.provider(), group, state.link());
				state.complete(channel);
			}
			else
			{
				state.handler().failed(new IOException("operation failed with status code " + Integer.toUnsignedString(systemResult, 10)), state.context());
			}
		}
		catch (Throwable cause)
		{
			// #TODO: record this event
		}

		state.operation().close();
	}
}
