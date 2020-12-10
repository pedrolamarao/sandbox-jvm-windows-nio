package br.dev.pedrolamarao.java.nio.windows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import br.dev.pedrolamarao.io.Link;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.OperationState;
import br.dev.pedrolamarao.io.Port;
import br.dev.pedrolamarao.windows.Ws2_32;
import jdk.incubator.foreign.MemoryLayout.PathElement;
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

	private int family = Ws2_32.AF_UNSPEC;
	
	private final WindowsAsynchronousChannelGroup group;
	
	private long key = 0;
	
	private final HashMap<Long, AcceptState> operations = new HashMap<>();
	
	private Port port = null;
	
	// life-cycle
	
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
	
	// properties

	@Override
	public boolean isOpen ()
	{
		return (port != null);
	}
	
	// methods

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
	
	@Override
	public AsynchronousServerSocketChannel bind (SocketAddress address, int backlog) throws IOException
	{
		if (isOpen()) {
			throw new IOException("illegal state: channel already open");
		}

		try (var sockaddr = toSockaddr(address))
		{
			family = (short) Ws2_32.sockaddr.family.get(sockaddr);
			port = new Port(family, Ws2_32.SOCK_STREAM, 0);
			port.bind(sockaddr);
			key = group.register(port, this::complete);
			port.listen();
			return this;
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
		
		try
		{
			final var operation = new Operation();
			final var buffer = MemorySegment.allocateNative(2048);
			final var link = new Link(family, Ws2_32.SOCK_STREAM, 0); 

			port.accept(operation, buffer, link);
			
			operations.put(operation.handle().toRawLongValue(), new AcceptState(operation, buffer, link, attachment, (CompletionHandler<AsynchronousSocketChannel, Object>) handler));
		} 
		catch (IOException e)
		{
			group.submit(() -> handler.failed(e, attachment));
		}
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
			systemState = port.query(state.operation());
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
	
	@SuppressWarnings("preview")
	public static MemorySegment toSockaddr (SocketAddress address) throws IOException
	{
		if (address instanceof InetSocketAddress inetAddress)
		{
			final var length = inetAddress.getAddress().getAddress().length;
			
			if (length == 4) 
			{
				final var sockaddr = MemorySegment.allocateNative(Ws2_32.sockaddr_in.LAYOUT);
				Ws2_32.sockaddr_in.family.set(sockaddr, (short) Ws2_32.AF_INET);
				Ws2_32.sockaddr_in.port.set(sockaddr, (short) inetAddress.getPort());
				final var addrOffset = Ws2_32.sockaddr_in.LAYOUT.byteOffset(PathElement.groupElement("addr"));
				final var addr = sockaddr.asSlice(addrOffset, Ws2_32.in_addr.LAYOUT.byteSize());
				addr.copyFrom(MemorySegment.ofArray(inetAddress.getAddress().getAddress()));
				return sockaddr;
			}
			else if (length == 16) 
			{
				final var sockaddr = MemorySegment.allocateNative(Ws2_32.sockaddr_in6.LAYOUT);
				Ws2_32.sockaddr_in6.family.set(sockaddr, (short) Ws2_32.AF_INET6);
				Ws2_32.sockaddr_in6.port.set(sockaddr, (short) inetAddress.getPort());
				final var addrOffset = Ws2_32.sockaddr_in6.LAYOUT.byteOffset(PathElement.groupElement("addr"));
				final var addr = sockaddr.asSlice(addrOffset, Ws2_32.in6_addr.LAYOUT.byteSize());
				addr.copyFrom(MemorySegment.ofArray(inetAddress.getAddress().getAddress()));
				return sockaddr;
			}
			else
			{
				throw new IOException("unexpected IP address length: " + length);
			}
		}
		else
		{
			throw new IOException("unexpected address type: " + address.getClass());
		}
	}
}
