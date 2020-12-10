package br.dev.pedrolamarao.java.nio.windows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import br.dev.pedrolamarao.io.Link;
import br.dev.pedrolamarao.io.Operation;
import br.dev.pedrolamarao.io.OperationStatus;
import br.dev.pedrolamarao.windows.Ws2_32;

public final class WindowsAsynchronousSocketChannel extends AsynchronousSocketChannel implements WindowsChannel
{
	@SuppressWarnings("preview")
	public static final record State (Operation operation, Object context, CompletionHandler<Integer, Object> handler) { }
	
	private final WindowsAsynchronousChannelGroup group;
	
	private boolean isOpen;
	
	private long key;
	
	private Link link;
	
	private final HashMap<Long, State> operations = new HashMap<>();
	
	public WindowsAsynchronousSocketChannel (WindowsAsynchronousChannelProvider provider, WindowsAsynchronousChannelGroup group)
	{
		super(provider);
	
		this.group = group;
		this.isOpen = false;
		this.key = 0;
		this.link = null;
	}
	
	public WindowsAsynchronousSocketChannel (WindowsAsynchronousChannelProvider provider, WindowsAsynchronousChannelGroup group, Link link)
	{
		super(provider);
		
		this.group = group;
		this.isOpen = true;
		this.key = group.register(link, this);
		this.link = link;
	}

	@Override
	public void close () throws IOException
	{
		try { link.close(); }
			catch (Exception e) { }
		
		group.unregister(key);
	}
	
	//

	@Override
	public boolean isOpen ()
	{
		return isOpen;
	}
	
	//

	@Override
	public SocketAddress getLocalAddress () throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SocketAddress getRemoteAddress () throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<SocketOption<?>> supportedOptions ()
	{
		return null;
	}

	@Override
	public <T> T getOption (SocketOption<T> name) throws IOException
	{
		return null;
	}

	@Override
	public <T> AsynchronousSocketChannel setOption (SocketOption<T> name, T value) throws IOException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	//
	
	@SuppressWarnings("preview")
	@Override
	public AsynchronousSocketChannel bind (SocketAddress local) throws IOException
	{
		if (link != null) {
			throw new IOException("illegal state: already bound");
		}
		
		if (local instanceof InetSocketAddress address)
		{
			try
			{
				link = new Link(Ws2_32.AF_INET, Ws2_32.SOCK_STREAM, Ws2_32.IPPROTO_TCP);
				// #TODO: bind
				key = group.register(link, this);
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
	public <A> void connect (SocketAddress remote, A attachment, CompletionHandler<Void, ? super A> handler)
	{
		if (isOpen()) {
			group.submit(() -> handler.failed(new IOException("illegal state: already connected"), attachment));
		}
		
		group.submit(() -> handler.failed(new IOException("oops"), attachment));
	}

	@Override
	public Future<Void> connect (SocketAddress remote)
	{
		if (isOpen()) {
			return CompletableFuture.failedFuture(new IOException("illegal state: already connected"));
		}
		
		return CompletableFuture.failedFuture(new IOException("oops"));
	}

	@Override
	public <A> void read (ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler)
	{
		if (! isOpen()) {
			group.submit(() -> handler.failed(new IOException("illegal state: not connected"), attachment));			
		}
		
		ForkJoinPool.commonPool().submit(() -> handler.failed(new IOException("oops"), attachment));
	}

	@Override
	public Future<Integer> read (ByteBuffer dst)
	{
		if (isOpen()) {
			return CompletableFuture.failedFuture(new IOException("illegal state: already connected"));
		}
		
		return CompletableFuture.failedFuture(new IOException("oops"));
	}

	@Override
	public <A> void read (ByteBuffer[] dsts, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler)
	{
		if (! isOpen()) {
			group.submit(() -> handler.failed(new IOException("illegal state: not connected"), attachment));			
		}
		
		ForkJoinPool.commonPool().submit(() -> handler.failed(new IOException("oops"), attachment));
	}

	@Override
	public <A> void write (ByteBuffer src, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler)
	{
		if (! isOpen()) {
			group.submit(() -> handler.failed(new IOException("illegal state: not connected"), attachment));			
		}
		
		ForkJoinPool.commonPool().submit(() -> handler.failed(new IOException("oops"), attachment));
	}

	@Override
	public Future<Integer> write (ByteBuffer src)
	{
		if (isOpen()) {
			return CompletableFuture.failedFuture(new IOException("illegal state: already connected"));
		}
		
		return CompletableFuture.failedFuture(new IOException("oops"));
	}

	@Override
	public <A> void write (ByteBuffer[] srcs, int offset, int length, long timeout, TimeUnit unit, A attachment, CompletionHandler<Long, ? super A> handler)
	{
		if (! isOpen()) {
			group.submit(() -> handler.failed(new IOException("illegal state: not connected"), attachment));			
		}
		
		ForkJoinPool.commonPool().submit(() -> handler.failed(new IOException("oops"), attachment));
	}

	@Override
	public AsynchronousSocketChannel shutdownInput () throws IOException
	{
		if (! isOpen()) {
			throw new IOException("illegal state: not connected");
		}
		
		throw new IOException("oops");
	}

	@Override
	public AsynchronousSocketChannel shutdownOutput () throws IOException
	{
		if (! isOpen()) {
			throw new IOException("illegal state: not connected");
		}
		
		throw new IOException("oops");
	}
	
	public void complete (long operation, boolean ignore0, int ignore1)
	{
		// do we know this operation?
		
		final var state = operations.remove(operation);
		if (state == null) {
			// what!?
			return;
		}
		
		// get operation result

		final Optional<OperationStatus> systemState;
		
		try 
		{ 
			systemState = state.operation().get(link, Duration.ZERO, false);
		}
	    catch (Throwable cause) 
		{
	    	// #TODO: record this event
	    	state.handler().failed(cause, state.context());
	    	state.operation().close();
	    	return;
	    }
	    
		if (systemState.isEmpty()) {
			// what!?
			return;
		}
		
		// evaluate operation result
		
		final var systemResult = systemState.get().status();

		try
		{
			if (systemResult == 0)
			{
				state.handler().completed(systemState.get().data(), state.context());
			}
			else
			{
				state.handler().failed(new IOException("operation failed: code = " + Integer.toUnsignedString(systemResult, 10)), state.context());
			}
		}
		catch (Throwable cause)
		{
			// #TODO: record this event
		}

		state.operation().close();
	}
}
