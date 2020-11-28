package br.dev.pedrolamarao.io;

import jdk.incubator.foreign.MemoryAddress;

@SuppressWarnings("preview")
public final record BusEvent(long key, MemoryAddress operation, boolean status, int data) 
{
}
