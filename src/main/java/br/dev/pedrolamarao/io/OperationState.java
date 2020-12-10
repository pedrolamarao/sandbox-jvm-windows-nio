package br.dev.pedrolamarao.io;

@SuppressWarnings("preview")
public final record OperationState(boolean complete, int result, int bytes, int flags) 
{
}